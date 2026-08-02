package com.intelli.webrunner.ui;

import com.intelli.webrunner.execution.ExecutionResult;
import com.intelli.webrunner.execution.RequestExecutionService;
import com.intelli.webrunner.script.VarsStore;
import com.intelli.webrunner.state.ChainState;
import com.intelli.webrunner.state.ChainStepState;
import com.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.intelli.webrunner.state.NodeState;
import com.intelli.webrunner.state.NodeType;
import com.intelli.webrunner.state.RequestDetailsState;
import com.intelli.webrunner.state.RequestStatusState;
import com.intelli.webrunner.state.RequestType;
import com.intelli.webrunner.util.JsonUtils;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;

import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Editor and runner for chain requests: an ordered list of other requests executed sequentially
 * over a shared {@link VarsStore}. Self-contained around an active chain request id supplied by
 * the host on {@link #load(String)}.
 */
public final class ChainEditorPanel {

	private final Project project;
	private final GlobalWebrunnerStateService stateService;
	private final RequestExecutionService executionService;

	private final DefaultListModel<String> chainListModel = new DefaultListModel<>();
	private final JBList<String> chainList = new JBList<>(chainListModel);
	private final JComboBox<String> chainRequestCombo = new JComboBox<>();
	private final JButton chainAddButton = new JButton("Add");
	private final JButton chainRemoveButton = new JButton("Remove");
	private final JButton chainRunButton = new JButton(AllIcons.Actions.Execute);
	private final JButton chainStopButton = new JButton("\u25A0");
	private final JButton chainDebugButton = new JButton(AllIcons.Actions.StartDebugger);
	private final JButton chainNextButton = new JButton(AllIcons.Debugger.NextStatement);
	private final JButton chainContextButton = new JButton(AllIcons.Nodes.Variable);
	private final JBTextArea chainLogsArea = new JBTextArea();
	private final FileType scriptFileType;
	private final EditorTextField chainCurrentStateArea;
	private final JComboBox<String> chainSuccessCodesCombo =
		new JComboBox<>(new String[]{"200", "200, 201, 204", "200, 400"});
	private final JCheckBox runBasicBeforeRequestCheckbox = new JCheckBox("Run basic Before request");
	private final JCheckBox runBasicAfterRequestCheckbox = new JCheckBox("Run basic After request");
	private final JCheckBox runBasicStressCheckbox = new JCheckBox("Run basic Stress");
	private final EditorTextField chainRunIfArea;
	private final EditorTextField chainBeforeRequestArea;
	private final EditorTextField chainAfterRequestArea;
	private final EditorTextField chainInterruptIfArea;
	private final EditorTextField chainRawRequestArea;
	private final EditorTextField chainSentRequestArea;
	private final EditorTextField chainRequestResponseArea;
	private final EditorTextField chainResultBodyArea;
	private final EditorTextField chainResultResponseArea;
	private final EditorTextField chainResultHeadersArea;
	private final EditorTextField chainResultCookiesArea;
	private final EditorTextField chainResultBodySnapshotArea;
	private final JTabbedPane chainResponseTabs = new JTabbedPane();
	private final JButton openChainWindowButton = new JButton("Open Chain");
	private final JPanel root = new JPanel(new BorderLayout());
	private final Map<Integer, ChainNodeRenderer.StepMetadata> chainStepMetadata = new HashMap<>();
	private final List<ChainStepState> chainStepStates = new ArrayList<>();

	private ChainSession chainSession;
	private Future<?> activeChainExecution;
	private String activeRequestId;
	private int activeStepIndex = -1;
	private boolean isLoading = false;
	private boolean isLoadingStep = false;
	private static final Dimension ICON_BUTTON_SIZE = new Dimension(28, 28);

	public ChainEditorPanel(
		Project project,
		GlobalWebrunnerStateService stateService,
		RequestExecutionService executionService
	) {
		this.project = project;
		this.stateService = stateService;
		this.executionService = executionService;
		this.scriptFileType = resolveScriptFileType();
		this.chainCurrentStateArea = new EditorTextField("", project, JsonFileType.INSTANCE);
		this.chainCurrentStateArea.setOneLineMode(false);
		this.chainRunIfArea = createScriptEditor();
		this.chainBeforeRequestArea = createScriptEditor();
		this.chainAfterRequestArea = createScriptEditor();
		this.chainInterruptIfArea = createScriptEditor();
		this.chainRawRequestArea = createJsonEditor();
		this.chainSentRequestArea = createJsonEditor();
		this.chainRequestResponseArea = createJsonEditor();
		this.chainResultBodyArea = createJsonEditor();
		this.chainResultResponseArea = createJsonEditor();
		this.chainResultHeadersArea = createJsonEditor();
		this.chainResultCookiesArea = createJsonEditor();
		this.chainResultBodySnapshotArea = createJsonEditor();
		buildUi();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
		JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
		configureIconButton(chainRunButton, "Run Chain");
		configureStopButton(chainStopButton);
		configureIconButton(chainDebugButton, "Debug Chain");
		configureIconButton(chainNextButton, "Next");
		configureChainContextButton();
		chainNextButton.setEnabled(false);
		topBar.add(chainRunButton);
		topBar.add(chainStopButton);
		topBar.add(chainDebugButton);
		topBar.add(chainNextButton);
		topBar.add(chainContextButton);
		topBar.add(openChainWindowButton);
		chainRunButton.addActionListener(e -> runChain(false));
		chainStopButton.addActionListener(e -> stopChain());
		chainDebugButton.addActionListener(e -> runChain(true));
		chainNextButton.addActionListener(e -> runChainNext());
		chainContextButton.addActionListener(e -> openChainContext());
		openChainWindowButton.addActionListener(e -> openChainWindow());
		root.add(topBar, BorderLayout.NORTH);

		JPanel chainEditor = new JPanel(new BorderLayout());
		chainList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		chainList.setCellRenderer(new ChainNodeRenderer(stateService, chainStepMetadata::get));
		chainList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				selectChainStep(chainList.getSelectedIndex());
			}
		});
		chainList.setDragEnabled(true);
		chainList.setDropMode(DropMode.INSERT);
		chainList.setTransferHandler(new ChainListTransferHandler());
		chainEditor.add(new JBScrollPane(chainList), BorderLayout.CENTER);

		JPanel chainControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		chainRequestCombo.setRenderer(new ChainNodeRenderer(stateService));
		chainControls.add(new JLabel("Request"));
		chainControls.add(chainRequestCombo);
		chainControls.add(chainAddButton);
		chainControls.add(chainRemoveButton);
		chainAddButton.addActionListener(e -> addChainRequest());
		chainRemoveButton.addActionListener(e -> removeChainRequest());
		chainEditor.add(chainControls, BorderLayout.SOUTH);

		chainResponseTabs.add("Logs", new JBScrollPane(chainLogsArea));
		chainResponseTabs.add("Current State", new JBScrollPane(chainCurrentStateArea));

		JSplitPane leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chainEditor, chainResponseTabs);
		leftSplitPane.setResizeWeight(0.6);
		SplitPaneStyling.applyThinBlackDivider(leftSplitPane);

		JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplitPane, buildChainWorkspacePanel());
		mainSplitPane.setResizeWeight(0.55);
		SplitPaneStyling.applyThinBlackDivider(mainSplitPane);
		root.add(mainSplitPane, BorderLayout.CENTER);
	}

	private JComponent buildChainWorkspacePanel() {
		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Options", buildOptionsPanel());
		tabs.add("Request", buildRequestPanel());
		tabs.add("Result", buildResultPanel());
		return tabs;
	}

	private JComponent buildOptionsPanel() {
		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Config", buildConfigPanel());
		tabs.add("Run if", new JBScrollPane(chainRunIfArea));
		tabs.add("Before Request", new JBScrollPane(chainBeforeRequestArea));
		tabs.add("After Request", new JBScrollPane(chainAfterRequestArea));
		tabs.add("Interrupt if", new JBScrollPane(chainInterruptIfArea));
		return tabs;
	}

	private JComponent buildRequestPanel() {
		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Raw Request", new JBScrollPane(chainRawRequestArea));
		tabs.add("Sent Request", new JBScrollPane(chainSentRequestArea));
		tabs.add("Response", new JBScrollPane(chainRequestResponseArea));
		return tabs;
	}

	private JComponent buildConfigPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		chainSuccessCodesCombo.setEditable(true);
		JPanel successCodesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		successCodesRow.add(new JLabel("Success Codes"));
		successCodesRow.add(chainSuccessCodesCombo);

		JPanel checkboxes = new JPanel(new GridLayout(0, 1, 0, 4));
		checkboxes.add(runBasicBeforeRequestCheckbox);
		checkboxes.add(runBasicAfterRequestCheckbox);
		checkboxes.add(runBasicStressCheckbox);

		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.add(successCodesRow, BorderLayout.NORTH);
		content.add(checkboxes, BorderLayout.CENTER);
		panel.add(content, BorderLayout.NORTH);
		return panel;
	}

	private JComponent buildResultPanel() {
		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Body", new JBScrollPane(chainResultBodyArea));
		tabs.add("Response", new JBScrollPane(chainResultResponseArea));
		tabs.add("Headers", new JBScrollPane(chainResultHeadersArea));
		tabs.add("Cookies", new JBScrollPane(chainResultCookiesArea));
		tabs.add("Body", new JBScrollPane(chainResultBodySnapshotArea));
		return tabs;
	}

	private void configureIconButton(JButton button, String tooltip) {
		button.setToolTipText(tooltip);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setPreferredSize(ICON_BUTTON_SIZE);
		button.setFocusable(false);
		button.setRequestFocusEnabled(false);
	}

	private void configureStopButton(JButton button) {
		configureIconButton(button, "Stop Chain");
		button.setForeground(Color.RED);
		button.setEnabled(false);
	}

	private void configureChainContextButton() {
		chainContextButton.setToolTipText("Chain Context");
		chainContextButton.setForeground(JBColor.ORANGE);
		chainContextButton.setMargin(new Insets(0, 0, 0, 0));
		chainContextButton.setPreferredSize(ICON_BUTTON_SIZE);
		chainContextButton.setFocusable(false);
		chainContextButton.setRequestFocusEnabled(false);
	}

	public void load(String requestId) {
		activeRequestId = requestId;
		isLoading = true;
		clearChainStepMetadata();
		activeStepIndex = -1;
		chainStepStates.clear();
		ChainState chain = stateService.getChainState(requestId);
		chainListModel.clear();
		if (chain != null) {
			for (String id : chain.requestIds) {
				chainListModel.addElement(id);
			}
			chainStepStates.addAll(normalizeChainStepStates(chain.requestIds, chain.stepStates));
			chainLogsArea.setText(safe(chain.logs));
			chainCurrentStateArea.setText(safe(chain.currentState));
		} else {
			chainLogsArea.setText("");
			chainCurrentStateArea.setText("");
		}
		clearChainResult();
		if (!chainStepStates.isEmpty()) {
			activeStepIndex = 0;
			chainList.setSelectedIndex(0);
			loadChainStepUi(0);
		} else {
			activeStepIndex = -1;
			loadChainStepUi(-1);
		}
		chainNextButton.setEnabled(chainSession != null);
		isLoading = false;
		refreshRequestsCombo();
	}

	public void save() {
		if (activeRequestId == null) {
			return;
		}
		saveActiveChainStepUi();
		ChainState chain = stateService.getChainState(activeRequestId);
		if (chain == null) {
			chain = new ChainState();
			chain.requestId = activeRequestId;
		}
		chain.requestIds = Collections.list(chainListModel.elements());
		chain.stepStates = new ArrayList<>(chainStepStates);
		chain.logs = chainLogsArea.getText();
		chain.currentState = chainCurrentStateArea.getText();
		stateService.saveChainState(chain);
	}

	public void refreshRequestsCombo() {
		chainRequestCombo.removeAllItems();
		for (NodeState node : stateService.getNodes()) {
			if (node.type == NodeType.REQUEST && node.requestType != RequestType.CHAIN) {
				if (activeRequestId != null && activeRequestId.equals(node.id)) {
					continue;
				}
				chainRequestCombo.addItem(node.id);
			}
		}
	}

	private void addChainRequest() {
		if (activeRequestId == null) {
			return;
		}
		Object selected = chainRequestCombo.getSelectedItem();
		if (selected == null) {
			return;
		}
		chainListModel.addElement(String.valueOf(selected));
		chainStepStates.add(defaultChainStepState(String.valueOf(selected)));
		clearChainStepMetadata();
		chainList.setSelectedIndex(chainListModel.size() - 1);
		save();
	}

	private void removeChainRequest() {
		int index = chainList.getSelectedIndex();
		if (index < 0) {
			return;
		}
		saveActiveChainStepUi();
		chainListModel.remove(index);
		if (index < chainStepStates.size()) {
			chainStepStates.remove(index);
		}
		activeStepIndex = -1;
		clearChainStepMetadata();
		if (!chainStepStates.isEmpty()) {
			chainList.setSelectedIndex(Math.min(index, chainStepStates.size() - 1));
		} else {
			loadChainStepUi(-1);
		}
		save();
	}

	/** Invoked by the host's "send" hotkey: advances a debug run or starts a full run. */
	public void triggerSend() {
		if (chainSession != null) {
			runChainNext();
		} else {
			runChain(false);
		}
	}

	private void runChain(boolean debug) {
		if (activeRequestId == null) {
			return;
		}
		save();
		clearChainStepMetadata();
		chainRunButton.setEnabled(false);
		chainDebugButton.setEnabled(false);
		chainStopButton.setEnabled(true);
		chainNextButton.setEnabled(debug);
		chainSession = new ChainSession();
		if (debug) {
			runChainNext();
			return;
		}
		activeChainExecution = runInBackground(() -> {
			while (chainSession.nextIndex < chainListModel.size()) {
				if (chainSession.cancelled || Thread.currentThread().isInterrupted()) {
					finishChainRun();
					return;
				}
				executeChainStep(
					chainSession,
					chainListModel.getElementAt(chainSession.nextIndex),
					chainSession.nextIndex
				);
				chainSession.nextIndex++;
			}
			finishChainRun();
		});
	}

	private void runChainNext() {
		if (chainSession == null) {
			return;
		}
		if (chainSession.nextIndex >= chainListModel.size()) {
			finishChainRun();
			return;
		}
		int stepIndex = chainSession.nextIndex;
		String requestId = chainListModel.getElementAt(stepIndex);
		activeChainExecution = runInBackground(() -> {
			if (chainSession.cancelled || Thread.currentThread().isInterrupted()) {
				finishChainRun();
				return;
			}
			executeChainStep(chainSession, requestId, stepIndex);
			chainSession.nextIndex++;
			invokeLater(() -> chainNextButton.setEnabled(
				chainSession.nextIndex < chainListModel.size()));
			if (chainSession.nextIndex >= chainListModel.size()) {
				finishChainRun();
			}
		});
	}

	private void stopChain() {
		if (chainSession != null) {
			chainSession.cancelled = true;
		}
		if (activeChainExecution != null) {
			activeChainExecution.cancel(true);
			activeChainExecution = null;
		}
		finishChainRun();
	}

	private void executeChainStep(
		ChainSession session,
		String requestId,
		int stepIndex
	) {
		NodeState node = stateService.findNode(requestId);
		if (node == null || node.type != NodeType.REQUEST) {
			session.logs.add("Missing request " + requestId);
			updateChainUi(session, null);
			return;
		}
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		RequestStatusState status = stateService.getRequestStatus(requestId);
		if (details == null || status == null) {
			session.logs.add("Missing request details for " + requestId);
			updateChainUi(session, null);
			return;
		}
		ExecutionResult result;
		if (details.type == RequestType.HTTP) {
			String method = details.method == null ? "GET" : details.method;
			result = executionService.executeWithScripts(method,
										details.url,
										status.requestHeaders,
										status.requestParams,
										status.requestBody,
										status.beforeScript,
										status.afterScript,
										true,
										session.vars,
										details.payloadType,
										status.formData,
										status.binaryFilePath
			);
		} else if (details.type == RequestType.GRPC) {
			if (details.service == null || details.service.isBlank() || details.grpcMethod == null ||
				details.grpcMethod.isBlank()) {
				session.logs.add("Missing gRPC service or method for " + requestId);
				updateChainUi(session, node);
				return;
			}
			result = executionService.executeGrpcWithScripts(details,
											status.requestHeaders,
											status.requestParams,
											status.requestBody,
											status.beforeScript,
											status.afterScript,
											session.vars
			);
		} else {
			session.logs.add("Unsupported request in chain: " + requestId);
			updateChainUi(session, null);
			return;
		}
		session.logs.add(result.logs);
		updateChainStepResultState(stepIndex, result);
		updateChainStepMetadata(stepIndex, result, parseSuccessCodes(chainStepStateAt(stepIndex).successCodes));

		Map<String, Object> currentState = new LinkedHashMap<>();
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("id", requestId);
		meta.put("name", node.name);
		meta.put("type", details.type.name());
		if (details.type == RequestType.HTTP) {
			meta.put("http", Map.of("method", safe(details.method), "url", safe(details.url)));
		} else if (details.type == RequestType.GRPC) {
			meta.put(
				"grpc",
				Map.of("target", safe(details.target), "service", safe(details.service), "method", safe(details.grpcMethod))
			);
		}
		currentState.put("request", Map.of(
			"meta", meta,
			"body", safe(status.requestBody),
			"headers", status.requestHeaders == null ? List.of() : status.requestHeaders
		));
		currentState.put("response", Map.of(
			"statusCode", result.statusCode,
			"statusMessage", safe(result.statusMessage),
			"body", safe(result.responseBody),
			"headers", safe(result.responseHeaders),
			"cookies", safe(result.responseCookies)
		));
		currentState.put("vars", session.vars.entries());
		session.currentStateJson = JsonUtils.toJson(currentState);
		updateChainResult(stepIndex, result);

		updateChainUi(session, node, stepIndex);
	}

	private void updateChainResult(int stepIndex, ExecutionResult result) {
		invokeLater(() -> {
			if (activeStepIndex == stepIndex) {
				loadChainStepUi(stepIndex);
			}
		});
	}

	private void clearChainResult() {
		chainResultBodyArea.setText("");
		chainResultResponseArea.setText("");
		chainResultHeadersArea.setText("");
		chainResultCookiesArea.setText("");
		chainResultBodySnapshotArea.setText("");
	}

	private void selectChainStep(int index) {
		if (isLoading) {
			return;
		}
		saveActiveChainStepUi();
		activeStepIndex = index;
		loadChainStepUi(index);
		save();
	}

	private void saveActiveChainStepUi() {
		if (isLoading || isLoadingStep || activeStepIndex < 0 || activeStepIndex >= chainStepStates.size()) {
			return;
		}
		ChainStepState step = chainStepStates.get(activeStepIndex);
		step.requestId = chainListModel.getElementAt(activeStepIndex);
		step.successCodes = comboEditorText(chainSuccessCodesCombo);
		step.runBasicBeforeRequest = runBasicBeforeRequestCheckbox.isSelected();
		step.runBasicAfterRequest = runBasicAfterRequestCheckbox.isSelected();
		step.runBasicStress = runBasicStressCheckbox.isSelected();
		step.runIfScript = chainRunIfArea.getText();
		step.beforeRequestScript = chainBeforeRequestArea.getText();
		step.afterRequestScript = chainAfterRequestArea.getText();
		step.interruptIfScript = chainInterruptIfArea.getText();
		step.rawRequestSnapshot = chainRawRequestArea.getText();
		step.sentRequestSnapshot = chainSentRequestArea.getText();
		step.responseSnapshot = chainRequestResponseArea.getText();
		step.resultBody = chainResultBodyArea.getText();
		step.resultResponse = chainResultResponseArea.getText();
		step.resultHeaders = chainResultHeadersArea.getText();
		step.resultCookies = chainResultCookiesArea.getText();
		step.resultBodySnapshot = chainResultBodySnapshotArea.getText();
	}

	private void loadChainStepUi(int index) {
		isLoadingStep = true;
		try {
			if (index < 0 || index >= chainStepStates.size()) {
				chainSuccessCodesCombo.getEditor().setItem("200");
				runBasicBeforeRequestCheckbox.setSelected(false);
				runBasicAfterRequestCheckbox.setSelected(false);
				runBasicStressCheckbox.setSelected(false);
				chainRunIfArea.setText("");
				chainBeforeRequestArea.setText("");
				chainAfterRequestArea.setText("");
				chainInterruptIfArea.setText("");
				chainRawRequestArea.setText("");
				chainSentRequestArea.setText("");
				chainRequestResponseArea.setText("");
				clearChainResult();
				return;
			}
			ChainStepState step = chainStepStates.get(index);
			chainSuccessCodesCombo.getEditor().setItem(safe(step.successCodes).isBlank() ? "200" : step.successCodes);
			runBasicBeforeRequestCheckbox.setSelected(step.runBasicBeforeRequest);
			runBasicAfterRequestCheckbox.setSelected(step.runBasicAfterRequest);
			runBasicStressCheckbox.setSelected(step.runBasicStress);
			chainRunIfArea.setText(safe(step.runIfScript));
			chainBeforeRequestArea.setText(safe(step.beforeRequestScript));
			chainAfterRequestArea.setText(safe(step.afterRequestScript));
			chainInterruptIfArea.setText(safe(step.interruptIfScript));
			chainRawRequestArea.setText(safe(step.rawRequestSnapshot));
			chainSentRequestArea.setText(safe(step.sentRequestSnapshot));
			chainRequestResponseArea.setText(safe(step.responseSnapshot));
			chainResultBodyArea.setText(safe(step.resultBody));
			chainResultResponseArea.setText(safe(step.resultResponse));
			chainResultHeadersArea.setText(safe(step.resultHeaders));
			chainResultCookiesArea.setText(safe(step.resultCookies));
			chainResultBodySnapshotArea.setText(safe(step.resultBodySnapshot));
		} finally {
			isLoadingStep = false;
		}
	}

	private List<ChainStepState> normalizeChainStepStates(
		List<String> requestIds,
		List<ChainStepState> steps
	) {
		List<ChainStepState> normalized = new ArrayList<>();
		if (requestIds == null) {
			return normalized;
		}
		for (int index = 0; index < requestIds.size(); index++) {
			ChainStepState step = steps != null && index < steps.size()
				? copyChainStepState(steps.get(index))
				: defaultChainStepState(requestIds.get(index));
			step.requestId = requestIds.get(index);
			normalized.add(step);
		}
		return normalized;
	}

	private ChainStepState chainStepStateAt(int index) {
		while (index >= chainStepStates.size()) {
			String requestId = index < chainListModel.size() ? chainListModel.getElementAt(index) : "";
			chainStepStates.add(defaultChainStepState(requestId));
		}
		ChainStepState step = chainStepStates.get(index);
		if (index >= 0 && index < chainListModel.size()) {
			step.requestId = chainListModel.getElementAt(index);
		}
		return step;
	}

	private ChainStepState defaultChainStepState(String requestId) {
		ChainStepState step = new ChainStepState();
		step.requestId = requestId;
		step.successCodes = "200";
		return step;
	}

	private ChainStepState copyChainStepState(ChainStepState source) {
		if (source == null) {
			return defaultChainStepState("");
		}
		ChainStepState copy = new ChainStepState();
		copy.requestId = source.requestId;
		copy.successCodes = source.successCodes;
		copy.runBasicBeforeRequest = source.runBasicBeforeRequest;
		copy.runBasicAfterRequest = source.runBasicAfterRequest;
		copy.runBasicStress = source.runBasicStress;
		copy.runIfScript = source.runIfScript;
		copy.beforeRequestScript = source.beforeRequestScript;
		copy.afterRequestScript = source.afterRequestScript;
		copy.interruptIfScript = source.interruptIfScript;
		copy.rawRequestSnapshot = source.rawRequestSnapshot;
		copy.sentRequestSnapshot = source.sentRequestSnapshot;
		copy.responseSnapshot = source.responseSnapshot;
		copy.resultBody = source.resultBody;
		copy.resultResponse = source.resultResponse;
		copy.resultHeaders = source.resultHeaders;
		copy.resultCookies = source.resultCookies;
		copy.resultBodySnapshot = source.resultBodySnapshot;
		return copy;
	}

	private void updateChainStepResultState(int stepIndex, ExecutionResult result) {
		ChainStepState step = chainStepStateAt(stepIndex);
		step.rawRequestSnapshot = safe(result.rawRequestSnapshot);
		step.sentRequestSnapshot = safe(result.sentRequestSnapshot);
		step.responseSnapshot = safe(result.responseSnapshot);
		step.resultBody = safe(result.responseBody);
		step.resultResponse = JsonUtils.toJson(Map.of(
			"statusCode", result.statusCode,
			"statusMessage", safe(result.statusMessage),
			"durationMillis", result.durationMillis
		));
		step.resultHeaders = safe(result.responseHeaders);
		step.resultCookies = safe(result.responseCookies);
		step.resultBodySnapshot = safe(result.responseBody);
	}

	private void updateChainStepMetadata(int stepIndex, ExecutionResult result, Set<Integer> successCodes) {
		String status = resolveStepStatus(result.statusCode, successCodes);
		String details = result.statusCode + " | " + formatDuration(result.durationMillis) + " | " +
			formatSize(responseBodySize(result.responseBody));
		invokeLater(() -> {
			chainStepMetadata.put(stepIndex, new ChainNodeRenderer.StepMetadata(status, details));
			chainList.repaint();
		});
	}

	private String resolveStepStatus(int statusCode, Set<Integer> successCodes) {
		return successCodes.contains(statusCode) ? "Passed" : "Failed";
	}

	private Set<Integer> parseSuccessCodes() {
		return parseSuccessCodes(comboEditorText(chainSuccessCodesCombo));
	}

	private Set<Integer> parseSuccessCodes(String text) {
		Set<Integer> codes = new HashSet<>();
		for (String part : safe(text).split(",")) {
			try {
				codes.add(Integer.parseInt(part.trim()));
			} catch (NumberFormatException ignored) {
				// Ignore invalid fragments while the user is editing the comma-separated list.
			}
		}
		if (codes.isEmpty()) {
			codes.add(200);
		}
		return codes;
	}

	private String comboEditorText(JComboBox<String> combo) {
		Object item = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
		return item == null ? "" : String.valueOf(item);
	}

	private void clearChainStepMetadata() {
		chainStepMetadata.clear();
		chainList.repaint();
	}

	private static String formatDuration(long durationMillis) {
		return durationMillis >= 0 ? durationMillis + " ms" : "";
	}

	private static int responseBodySize(String responseBody) {
		return responseBody == null ? 0 : responseBody.getBytes(StandardCharsets.UTF_8).length;
	}

	private static String formatSize(int bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		double kib = bytes / 1024.0;
		if (kib < 1024) {
			return String.format("%.1f KB", kib);
		}
		return String.format("%.1f MB", kib / 1024.0);
	}

	private void updateChainUi(
		ChainSession session,
		NodeState node
	) {
		updateChainUi(session, node, -1);
	}

	private void updateChainUi(
		ChainSession session,
		NodeState node,
		int stepIndex
	) {
		invokeLater(() -> {
			chainLogsArea.setText(String.join("\n", session.logs));
			chainCurrentStateArea.setText(session.currentStateJson);
			if (stepIndex >= 0 && stepIndex < chainListModel.size()) {
				chainList.setSelectedIndex(stepIndex);
			} else if (node != null) {
				chainList.setSelectedValue(node.id, true);
			}
			save();
		});
	}

	private void finishChainRun() {
		invokeLater(() -> {
			chainRunButton.setEnabled(true);
			chainDebugButton.setEnabled(true);
			chainStopButton.setEnabled(false);
			chainNextButton.setEnabled(false);
			chainSession = null;
			activeChainExecution = null;
			save();
		});
	}

	private void openChainWindow() {
		if (activeRequestId == null) {
			return;
		}
		NodeState node = stateService.findNode(activeRequestId);
		String title = "Chain Viewer";
		if (node != null && node.name != null) {
			title += " - " + node.name;
		}
		JDialog dialog = new JDialog();
		dialog.setTitle(title);
		JTabbedPane tabs = new JTabbedPane();

		JBTextArea logsArea = new JBTextArea();
		logsArea.setDocument(chainLogsArea.getDocument());
		EditorTextField stateField =
			new EditorTextField(chainCurrentStateArea.getDocument(), project, JsonFileType.INSTANCE, false, false);
		stateField.setOneLineMode(false);

		tabs.add("Logs", new JBScrollPane(logsArea));
		tabs.add("Current State", new JBScrollPane(stateField));

		dialog.getContentPane().add(tabs);
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(root);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private void openChainContext() {
		JDialog dialog = new JDialog();
		dialog.setTitle("Chain Context");
		EditorTextField stateField =
			new EditorTextField(chainCurrentStateArea.getDocument(), project, JsonFileType.INSTANCE, false, false);
		stateField.setOneLineMode(false);
		dialog.getContentPane().add(new JBScrollPane(stateField));
		dialog.setSize(700, 520);
		dialog.setLocationRelativeTo(root);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private Future<?> runInBackground(Runnable runnable) {
		return ApplicationManager.getApplication().executeOnPooledThread(runnable);
	}

	private void invokeLater(Runnable runnable) {
		ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private FileType resolveScriptFileType() {
		FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension("js");
		if (fileType == null || fileType == PlainTextFileType.INSTANCE) {
			fileType = PlainTextFileType.INSTANCE;
		}
		return fileType;
	}

	private EditorTextField createScriptEditor() {
		EditorTextField editor = new EditorTextField("", project, scriptFileType);
		editor.setOneLineMode(false);
		return editor;
	}

	private EditorTextField createJsonEditor() {
		EditorTextField editor = new EditorTextField("", project, JsonFileType.INSTANCE);
		editor.setOneLineMode(false);
		return editor;
	}

	private static final class ChainSession {

		int nextIndex = 0;
		VarsStore vars = new VarsStore();
		List<String> logs = new ArrayList<>();
		Set<Integer> successCodes = Set.of(200);
		String currentStateJson = "";
		boolean cancelled = false;
	}

	private final class ChainListTransferHandler extends TransferHandler {

		private int fromIndex = -1;

		@Override
		protected Transferable createTransferable(JComponent c) {
			fromIndex = chainList.getSelectedIndex();
			Object value = chainList.getSelectedValue();
			if (value == null) {
				return null;
			}
			return new StringSelection(String.valueOf(value));
		}

		@Override
		public int getSourceActions(JComponent c) {
			return MOVE;
		}

		@Override
		public boolean canImport(TransferSupport support) {
			return support.isDataFlavorSupported(DataFlavor.stringFlavor);
		}

		@Override
		public boolean importData(TransferSupport support) {
			if (!canImport(support)) {
				return false;
			}
			try {
				String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
				JList.DropLocation dropLocation = (JList.DropLocation) support.getDropLocation();
				int index = dropLocation.getIndex();
				if (fromIndex < 0) {
					return false;
				}
				if (index > fromIndex) {
					index--;
				}
				if (index < 0) {
					index = 0;
				}
				saveActiveChainStepUi();
				ChainStepState movedStep = fromIndex < chainStepStates.size()
					? chainStepStates.remove(fromIndex)
					: defaultChainStepState(data);
				chainListModel.remove(fromIndex);
				chainListModel.add(index, data);
				chainStepStates.add(Math.min(index, chainStepStates.size()), movedStep);
				activeStepIndex = -1;
				clearChainStepMetadata();
				chainList.setSelectedIndex(index);
				save();
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}
}
