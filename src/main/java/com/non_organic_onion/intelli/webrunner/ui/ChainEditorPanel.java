package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.intelli.webrunner.execution.ChainExecutionService;
import com.non_organic_onion.intelli.webrunner.execution.ChainEditorStateService;
import com.non_organic_onion.intelli.webrunner.execution.ChainRequestDispatcher;
import com.non_organic_onion.intelli.webrunner.execution.ChainRunner;
import com.non_organic_onion.intelli.webrunner.execution.ChainRunner.ChainRequest;
import com.non_organic_onion.intelli.webrunner.execution.ChainRunner.ChainRunSession;
import com.non_organic_onion.intelli.webrunner.execution.ExecutionResult;
import com.non_organic_onion.intelli.webrunner.execution.HttpStressExecutionService;
import com.non_organic_onion.intelli.webrunner.execution.RequestExecutionService;
import com.non_organic_onion.intelli.webrunner.execution.RequestTimeoutPolicy;
import com.non_organic_onion.intelli.webrunner.execution.RequestTestService;
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
import com.intellij.ui.components.JBTextField;

import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
	private final DefaultListModel<String> chainListModel = new DefaultListModel<>();
	private final JBList<String> chainList = new JBList<>(chainListModel);
	private final JButton chainAddButton = new JButton("Add");
	private final JButton chainCreateButton = new JButton("Create");
	private final JButton chainRemoveButton = new JButton("Remove");
	private final JButton chainRunButton = new JButton(AllIcons.Actions.Execute);
	private final JButton chainStopButton = new JButton("\u25A0");
	private final JButton chainDebugButton = new JButton(AllIcons.Actions.StartDebugger);
	private final JButton chainNextButton = new JButton(AllIcons.Actions.TraceOver);
	private final JButton chainContextButton = new JButton(AllIcons.Nodes.Variable);
	private final LogViewerPanel chainLogsArea;
	private final FileType scriptFileType;
	private final EditorTextField chainCurrentStateArea;
	private final JComboBox<String> chainSuccessCodesCombo =
		new JComboBox<>(new String[]{"200", "200, 201, 204", "200, 400"});
	private final JCheckBox runBasicBeforeRequestCheckbox = new JCheckBox("Run basic Before request");
	private final JCheckBox runBasicAfterRequestCheckbox = new JCheckBox("Run basic After request");
	private final JCheckBox runBasicStressCheckbox = new JCheckBox("Run basic Stress");
	private final JCheckBox runBasicTestsCheckbox = new JCheckBox("Run basic tests");
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
	private final RequestTimeoutPolicy requestTimeoutPolicy = new RequestTimeoutPolicy();
	private final ChainEditorStateService chainEditorStateService = new ChainEditorStateService();

	private ChainRunSession chainSession;
	private Future<?> activeChainExecution;
	private String activeRequestId;
	private String chainLogsText = "";
	private int activeStepIndex = -1;
	private boolean isLoading = false;
	private boolean isLoadingStep = false;
	private static final Dimension ICON_BUTTON_SIZE = new Dimension(28, 28);
	private static final JBColor SKIP_LOG_COLOR = new JBColor(new Color(188, 112, 22), new Color(205, 132, 36));
	private static final JBColor INTERRUPT_LOG_COLOR = new JBColor(new Color(190, 55, 55), new Color(214, 78, 78));
	private static final String SKIP_LOG_PREFIX = ChainExecutionService.SKIP_LOG_PREFIX;
	private static final String INTERRUPT_LOG_PREFIX = ChainExecutionService.INTERRUPT_LOG_PREFIX;

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
		this.chainLogsArea = new LogViewerPanel();
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
		checkboxes.add(runBasicTestsCheckbox);

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
			chainStepStates.addAll(chainEditorStateService.normalizeChainStepStates(chain.requestIds, chain.stepStates));
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
		chainStepStates.add(chainEditorStateService.defaultChainStepState(requestId));
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
		int result = TaskbarWindowSupport.showConfirmDialog(
			root,
			fields,
			"New Chain Request",
			JOptionPane.OK_CANCEL_OPTION
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
		chainSession = new ChainRunSession();
		chainSession.chainContext = loadChainContext();
		if (debug) {
			runChainNext();
			return;
		}
		activeChainExecution = runInBackground(() -> {
			ChainRunner.runRemaining(
				chainSession,
				chainRequestIds(),
				chainStepContext(),
				() -> chainSession.cancelled
			);
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
		activeChainExecution = runInBackground(() -> {
			ChainRunner.RunOutcome outcome = ChainRunner.runNext(
				chainSession,
				chainRequestIds(),
				chainStepContext(),
				() -> chainSession.cancelled
			);
			if (outcome.cancelled()) {
				finishChainRun();
				return;
			}
			invokeLater(() -> chainNextButton.setEnabled(outcome.waitingForNext()));
			if (outcome.finished()) {
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

	private List<String> chainRequestIds() {
		List<String> ids = new ArrayList<>();
		for (int index = 0; index < chainListModel.size(); index++) {
			ids.add(chainListModel.getElementAt(index));
		}
		return ids;
	}

	private void updateRequestBaseTestResult(String requestId, ExecutionResult result) {
		if (requestId == null || result == null) {
			return;
		}
		RequestStatusState status = stateService.getRequestStatus(requestId);
		if (status == null) {
			return;
		}
		RequestTestService.applyResult(status, null, result, RequestTestService.defaultSuccessCodes(), "");
		stateService.saveRequestStatus(status);
	}

	private void updateRequestTestResult(String requestId, String testId, ExecutionResult result) {
		if (requestId == null || testId == null || result == null) {
			return;
		}
		RequestStatusState status = stateService.getRequestStatus(requestId);
		if (status == null || status.tests == null) {
			return;
		}
		RequestTestService.applyResult(status, testId, result, RequestTestService.defaultSuccessCodes(), "");
		stateService.saveRequestStatus(status);
	}

	private ChainRunner.StepContext chainStepContext() {
		return new ChainRunner.StepContext(
			requestId -> {
				NodeState node = stateService.findNode(requestId);
				RequestDetailsState details = stateService.getRequestDetails(requestId);
				RequestStatusState status = stateService.getRequestStatus(requestId);
				return new ChainRequest(node, details, status);
			},
			this::chainStepStateAt,
			(session, request, status, stepState) -> executeChainRequestVariant(
				session,
				request.node() == null ? "" : request.node().id,
				request.details(),
				status,
				stepState
			),
			new ChainRunner.ResultSink() {
				@Override
				public void onRequestTestResult(
					String requestId,
					String testId,
					ExecutionResult result
				) {
					if (testId == null) {
						updateRequestBaseTestResult(requestId, result);
					} else {
						updateRequestTestResult(requestId, testId, result);
					}
				}

				@Override
				public void onStepResult(
					int stepIndex,
					ExecutionResult result,
					Set<Integer> successCodes
				) {
					updateChainStepResultState(stepIndex, result);
					updateChainStepMetadata(stepIndex, result, successCodes);
				}
			},
			new ChainRunner.EventSink() {
				@Override
				public void onMissingRequest(
					ChainRunSession session,
					String requestId
				) {
					updateChainUi(session, null);
				}

				@Override
				public void onMissingDetails(
					ChainRunSession session,
					ChainRequest request
				) {
					updateChainUi(session, null);
				}

				@Override
				public void onStepCompleted(
					ChainRunSession session,
					ChainRequest request,
					int stepIndex,
					ExecutionResult result
				) {
					updateChainResult(stepIndex, result);
					updateChainUi(session, request.node(), stepIndex);
				}
			}
		);
	}

	private ExecutionResult executeChainRequestVariant(
		ChainRunSession session,
		String requestId,
		RequestDetailsState details,
		RequestStatusState status,
		ChainStepState stepState
	) {
		return ChainRequestDispatcher.dispatch(
			new ChainRequestDispatcher.DispatchRequest(
				requestId,
				details,
				status,
				stepState,
				session.vars,
				session.chainContext,
				session.chainRequests,
				requestTimeout(details)
			),
			new ChainRequestDispatcher.DispatchHandlers(
				(method, url, headers, params, body, before, after, vars, chainContext, chainRequests,
				 payloadType, formData, binaryFilePath, timeoutMillis) ->
					executionService.executeWithScripts(
						method,
						url,
						headers,
						params,
						body,
						before,
						after,
						true,
						vars,
						chainContext,
						chainRequests,
						payloadType,
						formData,
						binaryFilePath,
						timeoutMillis
					),
				httpStressExecutionService::execute,
				(grpcDetails, headers, params, body, before, after, vars, chainContext, chainRequests, timeoutMillis) ->
					executionService.executeGrpcWithScripts(
						grpcDetails,
						headers,
						params,
						body,
						before,
						after,
						vars,
						chainContext,
						chainRequests,
						timeoutMillis
					),
				message -> {
					session.logs.add(message);
					updateChainUi(session, null);
				}
			)
		);
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
		if (isLoading || isLoadingStep || activeStepIndex < 0
			|| activeStepIndex >= chainStepStates.size()
			|| activeStepIndex >= chainListModel.size()) {
			return;
		}
		ChainStepState step = chainStepStates.get(activeStepIndex);
		step.requestId = chainListModel.getElementAt(activeStepIndex);
		step.successCodes = comboEditorText(chainSuccessCodesCombo);
		step.runBasicBeforeRequest = runBasicBeforeRequestCheckbox.isSelected();
		step.runBasicAfterRequest = runBasicAfterRequestCheckbox.isSelected();
		step.runBasicStress = runBasicStressCheckbox.isSelected();
		step.runBasicTests = runBasicTestsCheckbox.isSelected();
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
				runBasicTestsCheckbox.setSelected(false);
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
			runBasicTestsCheckbox.setSelected(step.runBasicTests);
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

	private ChainStepState chainStepStateAt(int index) {
		while (index >= chainStepStates.size()) {
			String requestId = index < chainListModel.size() ? chainListModel.getElementAt(index) : "";
			chainStepStates.add(chainEditorStateService.defaultChainStepState(requestId));
		}
		ChainStepState step = chainStepStates.get(index);
		if (index >= 0 && index < chainListModel.size()) {
			step.requestId = chainListModel.getElementAt(index);
		}
		return step;
	}

	private void updateChainStepResultState(int stepIndex, ExecutionResult result) {
		ChainExecutionService.applyResultToStep(chainStepStateAt(stepIndex), result);
	}

	private boolean isFlowControlLog(String line) {
		return ChainExecutionService.isFlowControlLog(line);
	}

	private int requestTimeout(RequestDetailsState details) {
		return requestTimeoutPolicy.resolve(details, stateService.getDefaultTimeoutMillis());
	}

	private void updateChainStepMetadata(int stepIndex, ExecutionResult result, Set<Integer> successCodes) {
		ChainExecutionService.StepMetadata metadata = ChainExecutionService.stepMetadata(result, successCodes);
		invokeLater(() -> {
			chainStepMetadata.put(stepIndex, new ChainNodeRenderer.StepMetadata(metadata.status(), metadata.details()));
			chainList.repaint();
		});
	}

	private Set<Integer> parseSuccessCodes() {
		return parseSuccessCodes(comboEditorText(chainSuccessCodesCombo));
	}

	private Set<Integer> parseSuccessCodes(String text) {
		return ChainExecutionService.parseSuccessCodes(text);
	}

	private String comboEditorText(JComboBox<String> combo) {
		Object item = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
		return item == null ? "" : String.valueOf(item);
	}

	private void clearChainStepMetadata() {
		chainStepMetadata.clear();
		chainList.repaint();
	}

	private void updateChainUi(
		ChainRunSession session,
		NodeState node
	) {
		updateChainUi(session, node, -1);
	}

	private void updateChainUi(
		ChainRunSession session,
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

		JFrame dialog = TaskbarWindowSupport.createFrame("Chain Context", root);
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
		chain.chainContextVariables = chainEditorStateService.cloneContextVariables(variables);
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
			chain == null || chain.chainContextVariables == null
				? new ArrayList<>()
				: chainEditorStateService.cloneContextVariables(chain.chainContextVariables);
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
			variable.value = chainEditorStateService.stringifyContextValue(entry.getValue());
		}
		saveChainContextVariables(variables);
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
		JFrame dialog = TaskbarWindowSupport.createFrame("Add Chain Request", root);
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
		TaskbarWindowSupport.showFrameAndWait(dialog);
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
		LogViewerPanel logsPanel,
		String logs
	) {
		logsPanel.setLogs(logs, this::chainLogColor, chainEditorStateService::stripChainLogPrefix);
	}

	private Color chainLogColor(String line) {
		if (line == null) {
			return JBColor.foreground();
		}
		if (line.startsWith(SKIP_LOG_PREFIX)) {
			return SKIP_LOG_COLOR;
		}
		if (line.startsWith(INTERRUPT_LOG_PREFIX)) {
			return INTERRUPT_LOG_COLOR;
		}
		return line.contains("Assertion failed") ? JBColor.RED : JBColor.foreground();
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
					: chainEditorStateService.defaultChainStepState(data);
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
