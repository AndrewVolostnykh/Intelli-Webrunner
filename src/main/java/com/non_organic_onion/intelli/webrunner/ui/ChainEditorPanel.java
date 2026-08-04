package com.non_organic_onion.intelli.webrunner.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.non_organic_onion.intelli.webrunner.execution.ExecutionResult;
import com.non_organic_onion.intelli.webrunner.execution.HttpStressConfig;
import com.non_organic_onion.intelli.webrunner.execution.HttpStressExecutionService;
import com.non_organic_onion.intelli.webrunner.execution.HttpStressRequest;
import com.non_organic_onion.intelli.webrunner.execution.RequestExecutionService;
import com.non_organic_onion.intelli.webrunner.script.VarsStore;
import com.non_organic_onion.intelli.webrunner.state.ChainState;
import com.non_organic_onion.intelli.webrunner.state.ChainStepState;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import com.non_organic_onion.intelli.webrunner.state.NodeState;
import com.non_organic_onion.intelli.webrunner.state.NodeType;
import com.non_organic_onion.intelli.webrunner.state.RequestDetailsState;
import com.non_organic_onion.intelli.webrunner.state.RequestStatusState;
import com.non_organic_onion.intelli.webrunner.state.RequestType;
import com.non_organic_onion.intelli.webrunner.util.JsonUtils;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;

import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
	private final HttpStressExecutionService httpStressExecutionService;
	private final Runnable onRequestsChanged;
	private final ObjectMapper mapper = new ObjectMapper();

	private final DefaultListModel<String> chainListModel = new DefaultListModel<>();
	private final JBList<String> chainList = new JBList<>(chainListModel);
	private final JButton chainAddButton = new JButton("Add");
	private final JButton chainCreateButton = new JButton("Create");
	private final JButton chainRemoveButton = new JButton("Remove");
	private final JButton chainRunButton = new JButton(AllIcons.Actions.Execute);
	private final JButton chainStopButton = new JButton("\u25A0");
	private final JButton chainDebugButton = new JButton(AllIcons.Actions.StartDebugger);
	private final JButton chainNextButton = new JButton(AllIcons.Debugger.NextStatement);
	private final JButton chainContextButton = new JButton(AllIcons.Nodes.Variable);
	private final ConsoleView chainLogsArea;
	private final FileType scriptFileType;
	private final EditorTextField chainCurrentStateArea;
	private final JComboBox<String> chainSuccessCodesCombo =
		new JComboBox<>(new String[]{"200", "200, 201, 204", "200, 400"});
	private final JCheckBox runBasicBeforeRequestCheckbox = new JCheckBox("Run basic Before request");
	private final JCheckBox runBasicAfterRequestCheckbox = new JCheckBox("Run basic After request");
	private final JCheckBox runBasicStressCheckbox = new JCheckBox("Run basic Stress");
	private final EditorTextField chainBeforeRequestArea;
	private final EditorTextField chainAfterRequestArea;
	private final EditorTextField chainRawRequestArea;
	private final EditorTextField chainSentRequestArea;
	private final EditorTextField chainRequestResponseArea;
	private final EditorTextField chainResultBodyArea;
	private final EditorTextField chainResultResponseArea;
	private final EditorTextField chainResultHeadersArea;
	private final EditorTextField chainResultCookiesArea;
	private final EditorTextField chainResultBodySnapshotArea;
	private final JTabbedPane chainResponseTabs = new JTabbedPane();
	private final JPanel root = new JPanel(new BorderLayout());
	private final Map<Integer, ChainNodeRenderer.StepMetadata> chainStepMetadata = new HashMap<>();
	private final List<ChainStepState> chainStepStates = new ArrayList<>();

	private ChainSession chainSession;
	private Future<?> activeChainExecution;
	private String activeRequestId;
	private String chainLogsText = "";
	private int activeStepIndex = -1;
	private boolean isLoading = false;
	private boolean isLoadingStep = false;
	private static final Dimension ICON_BUTTON_SIZE = new Dimension(28, 28);
	private static final ConsoleViewContentType WHITE_LOG_OUTPUT =
		new ConsoleViewContentType(
			"WEBRUNNER_CHAIN_WHITE_LOG_OUTPUT",
			new TextAttributes(Color.WHITE, null, null, null, 0)
		);
	private static final ConsoleViewContentType SKIP_LOG_OUTPUT =
		new ConsoleViewContentType(
			"WEBRUNNER_CHAIN_SKIP_LOG_OUTPUT",
			new TextAttributes(new JBColor(new Color(188, 112, 22), new Color(205, 132, 36)), null, null, null, 0)
		);
	private static final ConsoleViewContentType INTERRUPT_LOG_OUTPUT =
		new ConsoleViewContentType(
			"WEBRUNNER_CHAIN_INTERRUPT_LOG_OUTPUT",
			new TextAttributes(new JBColor(new Color(190, 55, 55), new Color(214, 78, 78)), null, null, null, 0)
		);
	private static final String SKIP_LOG_PREFIX = "[[WEBRUNNER_CHAIN_SKIP]]";
	private static final String INTERRUPT_LOG_PREFIX = "[[WEBRUNNER_CHAIN_INTERRUPT]]";

	public ChainEditorPanel(
		Project project,
		GlobalWebrunnerStateService stateService,
		RequestExecutionService executionService,
		Runnable onRequestsChanged
	) {
		this.project = project;
		this.stateService = stateService;
		this.executionService = executionService;
		this.httpStressExecutionService = new HttpStressExecutionService(executionService);
		this.onRequestsChanged = onRequestsChanged;
		this.chainLogsArea = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
		this.scriptFileType = resolveScriptFileType();
		this.chainCurrentStateArea = EditorThemeSupport.configure(new EditorTextField("", project, JsonFileType.INSTANCE));
		this.chainCurrentStateArea.setOneLineMode(false);
		this.chainBeforeRequestArea = createScriptEditor();
		this.chainAfterRequestArea = createScriptEditor();
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
		chainRunButton.addActionListener(e -> runChain(false));
		chainStopButton.addActionListener(e -> stopChain());
		chainDebugButton.addActionListener(e -> runChain(true));
		chainNextButton.addActionListener(e -> runChainNext());
		chainContextButton.addActionListener(e -> openChainContext());
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
		chainControls.add(chainAddButton);
		chainControls.add(chainCreateButton);
		chainControls.add(chainRemoveButton);
		chainAddButton.addActionListener(e -> addChainRequest());
		chainCreateButton.addActionListener(e -> createChainRequest());
		chainRemoveButton.addActionListener(e -> removeChainRequest());
		chainEditor.add(chainControls, BorderLayout.SOUTH);

		chainResponseTabs.add("Logs", chainLogsArea.getComponent());
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
		tabs.add("Before Request", new JBScrollPane(chainBeforeRequestArea));
		tabs.add("After Request", new JBScrollPane(chainAfterRequestArea));
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
			setChainLogs(safe(chain.logs));
			chainCurrentStateArea.setText(safe(chain.currentState));
		} else {
			setChainLogs("");
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
		chain.logs = chainLogsText;
		chain.currentState = chainCurrentStateArea.getText();
		stateService.saveChainState(chain);
	}

	public void refreshRequestsCombo() {
		// Kept for callers that refresh chain request sources after tree changes.
	}

	private void addChainRequest() {
		if (activeRequestId == null) {
			return;
		}
		RequestSelection selection = showRequestSelectionDialog();
		if (selection == null || selection.requestId == null) {
			return;
		}
		if (selection.copy) {
			addChainRequestCopy(selection.requestId);
		} else {
			addChainRequest(selection.requestId);
		}
	}

	private void addChainRequest(String requestId) {
		chainListModel.addElement(requestId);
		chainStepStates.add(defaultChainStepState(requestId));
		clearChainStepMetadata();
		chainList.setSelectedIndex(chainListModel.size() - 1);
		save();
	}

	private void createChainRequest() {
		if (activeRequestId == null) {
			return;
		}
		NodeState chain = stateService.findNode(activeRequestId);
		if (chain == null) {
			return;
		}
		JBTextField nameField = new JBTextField();
		JComboBox<RequestType> typeCombo = new JComboBox<>(chainCreatableRequestTypes());
		typeCombo.setSelectedItem(RequestType.HTTP);
		Object[] fields = {
			"Request name:", nameField,
			"Request type:", typeCombo
		};
		SwingUtilities.invokeLater(nameField::requestFocusInWindow);
		int result = JOptionPane.showConfirmDialog(
			root,
			fields,
			"New Chain Request",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE
		);
		if (result != JOptionPane.OK_OPTION) {
			return;
		}
		String name = nameField.getText();
		if (name == null || name.isBlank()) {
			return;
		}
		RequestType type = (RequestType) typeCombo.getSelectedItem();
		if (type == null) {
			return;
		}
		NodeState created = stateService.createRequest(name.trim(), type, chain.parentId);
		addChainRequest(created.id);
		if (onRequestsChanged != null) {
			onRequestsChanged.run();
		}
	}

	private RequestType[] chainCreatableRequestTypes() {
		List<RequestType> types = new ArrayList<>();
		for (RequestType type : RequestType.values()) {
			if (type != RequestType.CHAIN) {
				types.add(type);
			}
		}
		return types.toArray(new RequestType[0]);
	}

	private void addChainRequestCopy(String sourceRequestId) {
		NodeState source = stateService.findNode(sourceRequestId);
		NodeState chain = stateService.findNode(activeRequestId);
		if (source == null || chain == null) {
			return;
		}
		String chainName = safe(chain.name).trim();
		String cloneName = safe(source.name).trim();
		if (!chainName.isBlank()) {
			cloneName = cloneName.isBlank() ? chainName : cloneName + " " + chainName;
		}
		NodeState cloned = stateService.cloneRequest(sourceRequestId, cloneName);
		if (cloned == null) {
			return;
		}
		stateService.moveNode(cloned.id, chain.parentId, Integer.MAX_VALUE);
		if (onRequestsChanged != null) {
			onRequestsChanged.run();
		}
		addChainRequest(cloned.id);
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
		chainSession.chainContext = loadChainContext();
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
			if (chainSession.cancelled) {
				finishChainRun();
				return;
			}
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
		ChainStepState stepState = chainStepStateAt(stepIndex);
		String beforeScript = combineScripts(
			stepState.runBasicBeforeRequest ? status.beforeScript : "",
			stepState.beforeRequestScript
		);
		String afterScript = combineScripts(
			stepState.runBasicAfterRequest ? status.afterScript : "",
			stepState.afterRequestScript
		);
		ExecutionResult result;
		if (details.type == RequestType.HTTP) {
			String method = details.method == null ? "GET" : details.method;
			HttpStressConfig stressConfig = loadStressConfig(stepState, status);
			if (stressConfig.enabled()) {
				if (!stressConfig.hasLimit()) {
					result = ExecutionResult.failure(List.of("Stress test requires Total Duration or Number of requests."));
				} else {
					result = httpStressExecutionService.execute(
						new HttpStressRequest(
							method,
							details.url,
							status.requestHeaders,
							status.requestParams,
							status.requestBody,
							beforeScript,
							afterScript,
							details.payloadType,
							status.formData,
							status.binaryFilePath,
							session.chainContext,
							session.chainRequests
						),
						stressConfig
					);
					if (result == null) {
						return;
					}
				}
			} else {
				result = executionService.executeWithScripts(method,
											details.url,
											status.requestHeaders,
											status.requestParams,
											status.requestBody,
											beforeScript,
											afterScript,
											true,
											session.vars,
											session.chainContext,
											session.chainRequests,
											details.payloadType,
											status.formData,
											status.binaryFilePath
				);
			}
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
											beforeScript,
											afterScript,
											session.vars,
											session.chainContext,
											session.chainRequests
			);
		} else {
			session.logs.add("Unsupported request in chain: " + requestId);
			updateChainUi(session, null);
			return;
		}
		session.logs.add(result.logs);
		appendFlowLog(session, node, result);
		updateChainStepResultState(stepIndex, result);
		updateChainStepMetadata(stepIndex, result, parseSuccessCodes(chainStepStateAt(stepIndex).successCodes));
		storeChainRequest(session, node, details, status, result);
		if ("Interrupted".equals(result.flowStatus)) {
			session.cancelled = true;
		}

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
		currentState.put("chainContext", session.chainContext.entries());
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
		step.beforeRequestScript = chainBeforeRequestArea.getText();
		step.afterRequestScript = chainAfterRequestArea.getText();
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
				chainBeforeRequestArea.setText("");
				chainAfterRequestArea.setText("");
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
			chainBeforeRequestArea.setText(safe(step.beforeRequestScript));
			chainAfterRequestArea.setText(safe(step.afterRequestScript));
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

	private void appendFlowLog(
		ChainSession session,
		NodeState node,
		ExecutionResult result
	) {
		String name = node == null ? "" : safe(node.name);
		if ("Skipped".equals(result.flowStatus)) {
			session.logs.add(SKIP_LOG_PREFIX + "Request " + name + " was Skipped.");
		} else if ("Interrupted".equals(result.flowStatus)) {
			session.logs.add(INTERRUPT_LOG_PREFIX + "Request " + name + " interrupted chain.");
		}
	}

	private void storeChainRequest(
		ChainSession session,
		NodeState node,
		RequestDetailsState details,
		RequestStatusState status,
		ExecutionResult result
	) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("id", safe(node.id));
		meta.put("name", safe(node.name));
		meta.put("type", details.type == null ? "" : details.type.name());
		snapshot.put("meta", meta);
		snapshot.put("configuredRequest", configuredRequestSnapshot(details, status));
		snapshot.put("request", parseJsonValue(result.sentRequestSnapshot));
		snapshot.put("sentRequest", parseJsonValue(result.sentRequestSnapshot));
		snapshot.put("rawRequest", parseJsonValue(result.rawRequestSnapshot));
		snapshot.put("response", parseJsonValue(result.responseSnapshot));
		snapshot.put("result", Map.of(
			"statusCode", result.statusCode,
			"statusMessage", safe(result.statusMessage),
			"body", parseJsonValue(result.responseBody),
			"headers", parseJsonValue(result.responseHeaders),
			"cookies", parseJsonValue(result.responseCookies),
			"logs", safe(result.logs),
			"durationMillis", result.durationMillis
		));
		if (node.name != null && !node.name.isBlank()) {
			session.chainRequests.put(node.name, snapshot);
		}
		session.chainRequests.put(node.id, snapshot);
	}

	private Map<String, Object> configuredRequestSnapshot(
		RequestDetailsState details,
		RequestStatusState status
	) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("body", status == null ? "" : parseJsonValue(status.requestBody));
		snapshot.put("headers", status == null || status.requestHeaders == null ? List.of() : status.requestHeaders);
		snapshot.put("params", status == null || status.requestParams == null ? List.of() : status.requestParams);
		snapshot.put("formData", status == null || status.formData == null ? List.of() : status.formData);
		snapshot.put("binaryFilePath", status == null ? "" : safe(status.binaryFilePath));
		if (details == null) {
			return snapshot;
		}
		if (details.type == RequestType.HTTP) {
			snapshot.put("method", safe(details.method));
			snapshot.put("url", safe(details.url));
		} else if (details.type == RequestType.GRPC) {
			snapshot.put("target", safe(details.target));
			snapshot.put("service", safe(details.service));
			snapshot.put("method", safe(details.grpcMethod));
		}
		return snapshot;
	}

	private Object parseJsonValue(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		try {
			return mapper.readValue(value, Object.class);
		} catch (Exception ignored) {
			return value;
		}
	}

	private HttpStressConfig loadStressConfig(
		ChainStepState step,
		RequestStatusState status
	) {
		if (step == null || status == null || !step.runBasicStress || !status.stressEnabled) {
			return HttpStressConfig.disabled();
		}
		return new HttpStressConfig(
			true,
			parseDouble(status.stressRequestsPerSec, 0),
			parseDurationMillis(status.stressTotalDuration, status.stressTotalDurationUnit),
			parseInt(status.stressNumberOfRequests, 0),
			parseInt(status.stressParallelWorkers, 1),
			parseDurationMillis(status.stressRampUpTime, status.stressRampUpTimeUnit),
			parseDurationMillis(status.stressDelayBetweenRequests, status.stressDelayBetweenRequestsUnit),
			parseDouble(status.stressJitterFrom, 0),
			parseDouble(status.stressJitterTo, 0)
		);
	}

	private String combineScripts(
		String basicScript,
		String chainScript
	) {
		String basic = safe(basicScript).trim();
		String chain = safe(chainScript).trim();
		if (basic.isBlank()) {
			return chain;
		}
		if (chain.isBlank()) {
			return basic;
		}
		return basic + "\n" + chain;
	}

	private int parseInt(
		String value,
		int fallback
	) {
		try {
			return value == null || value.isBlank() ? fallback : Math.max(0, Integer.parseInt(value.trim()));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private double parseDouble(
		String value,
		double fallback
	) {
		try {
			return value == null || value.isBlank() ? fallback : Math.max(0, Double.parseDouble(value.trim()));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private long parseDurationMillis(
		String value,
		String unit
	) {
		double amount = parseDouble(value, 0);
		if (amount <= 0) {
			return 0;
		}
		String normalizedUnit = unit == null ? "sec" : unit.trim().toLowerCase(Locale.ROOT);
		if ("min".equals(normalizedUnit)) {
			return Math.round(amount * 60_000);
		}
		if ("mills".equals(normalizedUnit)) {
			return Math.round(amount);
		}
		return Math.round(amount * 1000);
	}

	private void updateChainStepMetadata(int stepIndex, ExecutionResult result, Set<Integer> successCodes) {
		String status = resolveStepStatus(result, successCodes);
		String details = flowStatus(result).isBlank()
			? result.statusCode + " | " + formatDuration(result.durationMillis) + " | " +
				formatSize(responseBodySize(result.responseBody))
			: formatDuration(result.durationMillis) + " | " + formatSize(responseBodySize(result.responseBody));
		invokeLater(() -> {
			chainStepMetadata.put(stepIndex, new ChainNodeRenderer.StepMetadata(status, details));
			chainList.repaint();
		});
	}

	private String resolveStepStatus(ExecutionResult result, Set<Integer> successCodes) {
		String flowStatus = flowStatus(result);
		if (!flowStatus.isBlank()) {
			return flowStatus;
		}
		return successCodes.contains(result.statusCode) ? "Passed" : "Failed";
	}

	private String flowStatus(ExecutionResult result) {
		return result == null || result.flowStatus == null ? "" : result.flowStatus;
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
			setChainLogs(String.join("\n", session.logs));
			chainCurrentStateArea.setText(session.currentStateJson);
			saveChainContextVariablesFromRuntime(session.chainContext.entries());
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

	private void openChainContext() {
		if (activeRequestId == null) {
			return;
		}
		ChainState chain = stateService.getChainState(activeRequestId);
		HeaderTableModel variablesModel = new HeaderTableModel();
		variablesModel.setHeaders(
			chain == null || chain.chainContextVariables == null ? List.of() : chain.chainContextVariables,
			false
		);

		JDialog dialog = new JDialog();
		dialog.setTitle("Chain Context");
		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Variables", buildChainContextVariablesPanel(variablesModel));

		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveButton = new JButton("Save");
		JButton cancelButton = new JButton("Cancel");
		saveButton.addActionListener(e -> {
			stopTableEditing(tabs);
			saveChainContextVariables(variablesModel.getHeaders());
			if (chainSession != null) {
				chainSession.chainContext = toVarsStore(variablesModel.getHeaders());
			}
			dialog.dispose();
		});
		cancelButton.addActionListener(e -> dialog.dispose());
		footer.add(saveButton);
		footer.add(cancelButton);

		JPanel content = new JPanel(new BorderLayout());
		content.add(tabs, BorderLayout.CENTER);
		content.add(footer, BorderLayout.SOUTH);
		dialog.getContentPane().add(content);
		dialog.setSize(700, 520);
		dialog.setLocationRelativeTo(root);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private JComponent buildChainContextVariablesPanel(HeaderTableModel model) {
		JTable table = new JTable(model);
		table.setFillsViewportHeight(true);
		configureEnabledColumn(table);

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JBScrollPane(table), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton addButton = new JButton("Add");
		JButton removeButton = new JButton("Remove");
		addButton.addActionListener(e -> model.addEmptyRow());
		removeButton.addActionListener(e -> model.removeRow(table.getSelectedRow()));
		actions.add(addButton);
		actions.add(removeButton);
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	private Future<?> runInBackground(Runnable runnable) {
		return ApplicationManager.getApplication().executeOnPooledThread(runnable);
	}

	private void invokeLater(Runnable runnable) {
		ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
	}

	private void setChainLogs(String logs) {
		chainLogsText = logs == null ? "" : logs;
		printChainLogs(chainLogsArea, chainLogsText);
	}

	private VarsStore loadChainContext() {
		if (activeRequestId == null) {
			return new VarsStore();
		}
		ChainState chain = stateService.getChainState(activeRequestId);
		return toVarsStore(chain == null ? List.of() : chain.chainContextVariables);
	}

	private VarsStore toVarsStore(List<HeaderEntryState> variables) {
		VarsStore store = new VarsStore();
		if (variables == null) {
			return store;
		}
		for (HeaderEntryState variable : variables) {
			if (variable == null || !variable.enabled || variable.name == null || variable.name.isBlank()) {
				continue;
			}
			store.add(variable.name, parseContextValue(variable.value));
		}
		return store;
	}

	private Object parseContextValue(String value) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		if (trimmed.matches("-?\\d+")) {
			try {
				return Long.parseLong(trimmed);
			} catch (NumberFormatException ignored) {
				return value;
			}
		}
		if (trimmed.matches("-?\\d+\\.\\d+")) {
			try {
				return Double.parseDouble(trimmed);
			} catch (NumberFormatException ignored) {
				return value;
			}
		}
		return value;
	}

	private void saveChainContextVariables(List<HeaderEntryState> variables) {
		if (activeRequestId == null) {
			return;
		}
		ChainState chain = stateService.getChainState(activeRequestId);
		if (chain == null) {
			chain = new ChainState();
			chain.requestId = activeRequestId;
		}
		chain.requestIds = Collections.list(chainListModel.elements());
		chain.stepStates = new ArrayList<>(chainStepStates);
		chain.chainContextVariables = cloneContextVariables(variables);
		chain.logs = chainLogsText;
		chain.currentState = chainCurrentStateArea.getText();
		stateService.saveChainState(chain);
	}

	private void saveChainContextVariablesFromRuntime(Map<String, Object> values) {
		if (values == null) {
			return;
		}
		ChainState chain = stateService.getChainState(activeRequestId);
		List<HeaderEntryState> variables =
			chain == null || chain.chainContextVariables == null ? new ArrayList<>() : cloneContextVariables(chain.chainContextVariables);
		Map<String, HeaderEntryState> byName = new LinkedHashMap<>();
		for (HeaderEntryState variable : variables) {
			if (variable != null && variable.name != null && !variable.name.isBlank()) {
				byName.put(variable.name, variable);
			}
		}
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()) {
				continue;
			}
			HeaderEntryState variable = byName.get(entry.getKey());
			if (variable == null) {
				variable = new HeaderEntryState();
				variable.id = UUID.randomUUID().toString();
				variable.name = entry.getKey();
				variable.enabled = true;
				variables.add(variable);
				byName.put(variable.name, variable);
			}
			variable.value = stringifyContextValue(entry.getValue());
		}
		saveChainContextVariables(variables);
	}

	private List<HeaderEntryState> cloneContextVariables(List<HeaderEntryState> variables) {
		List<HeaderEntryState> copy = new ArrayList<>();
		if (variables == null) {
			return copy;
		}
		for (HeaderEntryState variable : variables) {
			if (variable == null) {
				continue;
			}
			HeaderEntryState clone = new HeaderEntryState();
			clone.id = variable.id;
			clone.name = variable.name;
			clone.value = variable.value;
			clone.enabled = variable.enabled;
			copy.add(clone);
		}
		return copy;
	}

	private String stringifyContextValue(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private void configureEnabledColumn(JTable table) {
		TableColumn enabledColumn = table.getColumnModel().getColumn(0);
		enabledColumn.setPreferredWidth(60);
		enabledColumn.setMinWidth(60);
		enabledColumn.setMaxWidth(60);
		enabledColumn.setResizable(false);
	}

	private void stopTableEditing(JTabbedPane tabs) {
		for (int i = 0; i < tabs.getTabCount(); i++) {
			Component component = tabs.getComponentAt(i);
			if (component instanceof JPanel panel) {
				stopTableEditing(panel);
			}
		}
	}

	private void stopTableEditing(JPanel panel) {
		for (Component component : panel.getComponents()) {
			if (component instanceof JBScrollPane scrollPane
				&& scrollPane.getViewport().getView() instanceof JTable table
				&& table.isEditing()) {
				TableCellEditor editor = table.getCellEditor();
				if (editor != null) {
					editor.stopCellEditing();
				}
			} else if (component instanceof JPanel childPanel) {
				stopTableEditing(childPanel);
			}
		}
	}

	private RequestSelection showRequestSelectionDialog() {
		JDialog dialog = new JDialog();
		dialog.setTitle("Add Chain Request");
		dialog.setModal(true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		JTree requestTree = new JTree(buildRequestSelectionTree());
		requestTree.setRootVisible(true);
		requestTree.setCellRenderer(new NodeTreeCellRenderer(stateService));
		requestTree.expandRow(0);

		JButton addButton = new JButton("Add");
		JButton addCopyButton = new JButton("Add Copy");
		JButton cancelButton = new JButton("Cancel");
		addButton.setEnabled(false);
		addCopyButton.setEnabled(false);
		RequestSelection[] selection = new RequestSelection[1];

		requestTree.addTreeSelectionListener(e -> {
			boolean requestSelected = selectedRequestNode(requestTree) != null;
			addButton.setEnabled(requestSelected);
			addCopyButton.setEnabled(requestSelected);
		});
		addButton.addActionListener(e -> {
			NodeState selected = selectedRequestNode(requestTree);
			if (selected != null) {
				selection[0] = new RequestSelection(selected.id, false);
				dialog.dispose();
			}
		});
		addCopyButton.addActionListener(e -> {
			NodeState selected = selectedRequestNode(requestTree);
			if (selected != null) {
				selection[0] = new RequestSelection(selected.id, true);
				dialog.dispose();
			}
		});
		cancelButton.addActionListener(e -> dialog.dispose());

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(addButton);
		actions.add(addCopyButton);
		actions.add(cancelButton);
		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(new JBScrollPane(requestTree), BorderLayout.CENTER);
		dialog.getContentPane().add(actions, BorderLayout.SOUTH);
		dialog.setSize(520, 640);
		dialog.setLocationRelativeTo(root);
		dialog.setVisible(true);
		return selection[0];
	}

	private DefaultTreeModel buildRequestSelectionTree() {
		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Requests");
		Map<String, List<NodeState>> byParent = new HashMap<>();
		for (NodeState node : stateService.getNodes()) {
			if (activeRequestId != null && activeRequestId.equals(node.id)) {
				continue;
			}
			if (node.type == NodeType.REQUEST && node.requestType == RequestType.CHAIN) {
				continue;
			}
			byParent.computeIfAbsent(node.parentId, key -> new ArrayList<>()).add(node);
		}
		for (List<NodeState> nodes : byParent.values()) {
			nodes.sort(Comparator.comparingInt(a -> a.order));
		}
		buildRequestSelectionChildren(rootNode, null, byParent);
		return new DefaultTreeModel(rootNode);
	}

	private void buildRequestSelectionChildren(
		DefaultMutableTreeNode parentTreeNode,
		String parentId,
		Map<String, List<NodeState>> byParent
	) {
		for (NodeState node : byParent.getOrDefault(parentId, List.of())) {
			DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
			parentTreeNode.add(treeNode);
			if (node.type == NodeType.FOLDER) {
				buildRequestSelectionChildren(treeNode, node.id, byParent);
			}
		}
	}

	private NodeState selectedRequestNode(JTree requestTree) {
		TreePath path = requestTree.getSelectionPath();
		if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode treeNode)) {
			return null;
		}
		Object userObject = treeNode.getUserObject();
		if (userObject instanceof NodeState node && node.type == NodeType.REQUEST) {
			return node;
		}
		return null;
	}

	private void printChainLogs(
		ConsoleView console,
		String logs
	) {
		console.clear();
		if (logs == null || logs.isEmpty()) {
			return;
		}
		String[] lines = logs.split("\\R", -1);
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			ConsoleViewContentType type = chainLogType(line);
			console.print(stripChainLogPrefix(line), type);
			if (i < lines.length - 1) {
				console.print("\n", type);
			}
		}
	}

	private ConsoleViewContentType chainLogType(String line) {
		if (line == null) {
			return WHITE_LOG_OUTPUT;
		}
		if (line.startsWith(SKIP_LOG_PREFIX)) {
			return SKIP_LOG_OUTPUT;
		}
		if (line.startsWith(INTERRUPT_LOG_PREFIX)) {
			return INTERRUPT_LOG_OUTPUT;
		}
		return line.contains("Assertion failed") ? ConsoleViewContentType.ERROR_OUTPUT : WHITE_LOG_OUTPUT;
	}

	private String stripChainLogPrefix(String line) {
		if (line == null) {
			return "";
		}
		if (line.startsWith(SKIP_LOG_PREFIX)) {
			return line.substring(SKIP_LOG_PREFIX.length());
		}
		if (line.startsWith(INTERRUPT_LOG_PREFIX)) {
			return line.substring(INTERRUPT_LOG_PREFIX.length());
		}
		return line;
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
		EditorTextField editor = EditorThemeSupport.configure(new EditorTextField("", project, scriptFileType));
		editor.setOneLineMode(false);
		return editor;
	}

	private EditorTextField createJsonEditor() {
		EditorTextField editor = EditorThemeSupport.configure(new EditorTextField("", project, JsonFileType.INSTANCE));
		editor.setOneLineMode(false);
		return editor;
	}

	private static final class ChainSession {

		int nextIndex = 0;
		VarsStore vars = new VarsStore();
		VarsStore chainContext = new VarsStore();
		Map<String, Object> chainRequests = new LinkedHashMap<>();
		List<String> logs = new ArrayList<>();
		Set<Integer> successCodes = Set.of(200);
		String currentStateJson = "";
		boolean cancelled = false;
	}

	private record RequestSelection(String requestId, boolean copy) {
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
