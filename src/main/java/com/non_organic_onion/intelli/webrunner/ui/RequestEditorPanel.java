package com.non_organic_onion.intelli.webrunner.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.non_organic_onion.intelli.webrunner.debug.DebugCallSession;
import com.non_organic_onion.intelli.webrunner.execution.DownloadResult;
import com.non_organic_onion.intelli.webrunner.execution.ExecutionResult;
import com.non_organic_onion.intelli.webrunner.execution.HttpExecutor;
import com.non_organic_onion.intelli.webrunner.execution.HttpStressConfig;
import com.non_organic_onion.intelli.webrunner.execution.HttpStressExecutionService;
import com.non_organic_onion.intelli.webrunner.execution.HttpStressRequest;
import com.non_organic_onion.intelli.webrunner.execution.RequestExecutionService;
import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutionResponse;
import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutor;
import com.non_organic_onion.intelli.webrunner.grpc.GrpcServiceInfo;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaListenMessage;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaListenRequest;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaListenerService;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaMetadataService;
import com.non_organic_onion.intelli.webrunner.script.GlobalContextRuntime;
import com.non_organic_onion.intelli.webrunner.script.ScriptContext;
import com.non_organic_onion.intelli.webrunner.script.ScriptHelpers;
import com.non_organic_onion.intelli.webrunner.script.ScriptLogger;
import com.non_organic_onion.intelli.webrunner.script.ScriptRequest;
import com.non_organic_onion.intelli.webrunner.script.ScriptRuntime;
import com.non_organic_onion.intelli.webrunner.script.VarsStore;
import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import com.non_organic_onion.intelli.webrunner.state.HeaderPresetState;
import com.non_organic_onion.intelli.webrunner.state.NodeState;
import com.non_organic_onion.intelli.webrunner.state.NodeType;
import com.non_organic_onion.intelli.webrunner.state.RequestDetailsState;
import com.non_organic_onion.intelli.webrunner.state.RequestStatusState;
import com.non_organic_onion.intelli.webrunner.state.RequestTestState;
import com.non_organic_onion.intelli.webrunner.state.RequestType;
import com.non_organic_onion.intelli.webrunner.util.PayloadTypes;
import com.non_organic_onion.intelli.webrunner.util.JsonUtils;
import com.non_organic_onion.intelli.webrunner.util.TemplateEngine;
import com.non_organic_onion.intelli.webrunner.util.UrlParamUtils;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;


/**
 * The HTTP/gRPC request editor: top bars, request tabs (body/params/headers/scripts), the response
 * viewer split, persistence (load/save), execution, gRPC service discovery, and the step debugger.
 * Tracks its own {@code activeNode}; the host calls {@link #load} / {@link #saveActive} as selection
 * changes and dispatches the "send" / format / focus hotkeys here.
 */
public final class RequestEditorPanel {

	private final Project project;
	private final GlobalWebrunnerStateService stateService;
	private final RequestExecutionService executionService;
	private final ResponseViewerPanel responseViewer;
	private final ScriptRuntime scriptRuntime;
	private final TemplateEngine templateEngine;
	private final HttpExecutor httpExecutor;
	private final GrpcExecutor grpcExecutor;
	private final KafkaMetadataService kafkaMetadataService;
	private final KafkaListenerService kafkaListenerService;
	private final HttpStressExecutionService httpStressExecutionService;
	private final ObjectMapper mapper = new ObjectMapper();

	private final JPanel root = new JPanel(new BorderLayout());
	private static final Dimension ICON_BUTTON_SIZE = new Dimension(28, 28);

	private NodeState activeNode;
	private String activeTestId;
	private boolean isLoading = false;
	private boolean isStoppingTableEditing = false;
	private boolean isSyncingParamsFromUrl = false;
	private final javax.swing.Timer urlParamSyncTimer;

	private final JComboBox<String> httpMethodCombo =
		new JComboBox<>(new String[] {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"});
	private final JComboBox<String> httpPayloadCombo =
		new JComboBox<>(new String[] {"Raw", "Form Data", "x-www-form-urlencoded", "Binary"});
	private final JBTextField httpUrlField = new JBTextField();
	private final JButton httpSendButton = new JButton(AllIcons.Actions.Execute);
	private final JButton httpSendDownloadButton = new JButton(AllIcons.Actions.Download);
	private final JButton httpStopButton = new JButton("\u25A0");
	private final JButton httpDebugButton = new JButton(AllIcons.Actions.StartDebugger);
	private final JSpinner httpTimeoutSpinner = createTimeoutSpinner();

	private final JBTextField grpcTargetField = new JBTextField();
	private final JComboBox<String> grpcServiceCombo = new JComboBox<>();
	private final JComboBox<String> grpcMethodCombo = new JComboBox<>();
	private final JButton grpcReloadButton = new JButton(AllIcons.Actions.Refresh);
	private final JButton grpcSendButton = new JButton(AllIcons.Actions.Execute);
	private final JButton grpcStopButton = new JButton("\u25A0");
	private final JButton grpcDebugButton = new JButton(AllIcons.Actions.StartDebugger);
	private final JSpinner grpcTimeoutSpinner = createTimeoutSpinner();
	private GrpcExecutor.GrpcStreamingCall activeGrpcClientStream;
	private String activeGrpcClientStreamRequestId;

	private final JComboBox<String> kafkaBootstrapCombo = new JComboBox<>();
	private final JComboBox<String> kafkaTopicCombo = new JComboBox<>();
	private final JBTextField kafkaKeyField = new JBTextField();
	private final JButton kafkaReloadButton = new JButton(AllIcons.Actions.Refresh);
	private final JButton kafkaSendButton = new JButton(AllIcons.Actions.Execute);
	private final JButton kafkaStopButton = new JButton("\u25A0");
	private final JSpinner kafkaTimeoutSpinner = createTimeoutSpinner();
	private final JComboBox<String> kafkaListenBootstrapCombo = new JComboBox<>();
	private final JComboBox<String> kafkaListenTopicCombo = new JComboBox<>();
	private final JBTextField kafkaGroupIdField = new JBTextField();
	private final JButton kafkaListenReloadButton = new JButton(AllIcons.Actions.Refresh);
	private final JButton kafkaListenButton = new JButton("Start Listening");

	private final CardLayout requestTopCards = new CardLayout();
	private final JPanel requestTopPanel = new JPanel(requestTopCards);
	private final JTabbedPane requestTabs = new JTabbedPane();
	private final DefaultMutableTreeNode testsRootNode = new DefaultMutableTreeNode("Tests");
	private final DefaultTreeModel testsTreeModel = new DefaultTreeModel(testsRootNode);
	private final JTree testsTree = new JTree(testsTreeModel);
	private final JButton runTestsButton = new JButton("Run");
	private final JButton createTestButton = new JButton("Create");
	private final EditorTextField requestBodyArea;
	private final CardLayout bodyCards = new CardLayout();
	private final JPanel bodyPanel = new JPanel(bodyCards);
	private JComponent headersPanel;
	private JComponent beforeScriptComponent;
	private JComponent afterScriptComponent;
	private final FormDataTableModel formDataTableModel = new FormDataTableModel();
	private final JTable formDataTable = new JTable(formDataTableModel);
	private final JButton addFormDataButton = new JButton("Add");
	private final JButton removeFormDataButton = new JButton("Remove");
	private final JButton chooseFormFileButton = new JButton("Choose File");
	private final JBTextField binaryFileField = new JBTextField();
	private final JButton binaryBrowseButton = new JButton("Browse");
	private final EditorTextField beforeScriptArea;
	private final EditorTextField afterScriptArea;
	private final StressSettingsPanel stressSettingsPanel = new StressSettingsPanel();
	private final HeaderTableModel headersTableModel = new HeaderTableModel();
	private final JTable headersTable = new JTable(headersTableModel);
	private final JButton addHeaderButton = new JButton("Add");
	private final JButton removeHeaderButton = new JButton("Remove");
	private final HeaderTableModel paramsTableModel = new HeaderTableModel();
	private final JTable paramsTable = new JTable(paramsTableModel);
	private final JButton addParamButton = new JButton("Add");
	private final JButton removeParamButton = new JButton("Remove");
	private final CardLayout paramsCards = new CardLayout();
	private final JPanel paramsPanel = new JPanel(paramsCards);
	private final JComboBox<String> kafkaKeyTypeCombo =
		new JComboBox<>(new String[] {"String", "JSON", "Bytes", "Integer", "Long", "UUID"});
	private final JComboBox<String> kafkaBodyTypeCombo =
		new JComboBox<>(new String[] {"JSON", "String", "Bytes"});
	private final JBTextField kafkaPartitionsField = new JBTextField();
	private final JComboBox<String> kafkaOffsetStrategyCombo =
		new JComboBox<>(new String[] {"Latest", "Earliest"});
	private List<HeaderPresetState> headerPresets;

	private final FileType scriptFileType;
	private final BodyGeneratorActions bodyGenerator;

	private static final List<String> COMMON_HEADER_NAMES = List.of(
		"Accept",
		"Accept-Charset",
		"Accept-Encoding",
		"Accept-Language",
		"Authorization",
		"Cache-Control",
		"Connection",
		"Content-Length",
		"Content-Type",
		"Cookie",
		"Host",
		"If-Match",
		"If-Modified-Since",
		"If-None-Match",
		"Origin",
		"Pragma",
		"Referer",
		"User-Agent",
		"X-Api-Key",
		"X-Correlation-Id",
		"X-Request-Id",
		"X-Requested-With"
	);
	private static final List<String> GRPC_HEADER_NAMES = List.of(
		"authorization",
		"grpc-accept-encoding",
		"grpc-encoding",
		"grpc-timeout",
		"user-agent",
		"x-api-key",
		"x-correlation-id",
		"x-request-id"
	);

	private final Map<String, List<GrpcServiceInfo>> grpcServicesCache = new ConcurrentHashMap<>();
	private final Map<String, String> grpcServiceSelection = new ConcurrentHashMap<>();
	private boolean isGrpcReloading = false;
	private boolean isKafkaReloading = false;
	private boolean stressTestsEnabled;
	private int defaultTimeoutMillis;
	private Future<?> activeExecution;
	private final Map<String, List<KafkaListenMessage>> kafkaListenMessagesByRequest = new ConcurrentHashMap<>();
	private final Map<String, VarsStore> kafkaListenVarsByRequest = new ConcurrentHashMap<>();

	private DebugCallSession debugCallSession;

	public RequestEditorPanel(
		Project project,
		GlobalWebrunnerStateService stateService,
		RequestExecutionService executionService,
		ResponseViewerPanel responseViewer,
		ScriptRuntime scriptRuntime,
		TemplateEngine templateEngine,
		HttpExecutor httpExecutor,
		GrpcExecutor grpcExecutor,
		KafkaMetadataService kafkaMetadataService,
		KafkaListenerService kafkaListenerService
	) {
		this.project = project;
		this.stateService = stateService;
		this.executionService = executionService;
		this.responseViewer = responseViewer;
		this.scriptRuntime = scriptRuntime;
		this.templateEngine = templateEngine;
		this.httpExecutor = httpExecutor;
		this.grpcExecutor = grpcExecutor;
		this.kafkaMetadataService = kafkaMetadataService;
		this.kafkaListenerService = kafkaListenerService;
		this.httpStressExecutionService = new HttpStressExecutionService(executionService);
		this.scriptFileType = resolveScriptFileType();
		this.requestBodyArea = new JsonBodyEditorField(project);
		this.beforeScriptArea = createScriptEditor();
		this.afterScriptArea = createScriptEditor();
		this.requestBodyArea.setOneLineMode(false);
		this.beforeScriptArea.setOneLineMode(false);
		this.afterScriptArea.setOneLineMode(false);
		this.grpcServiceCombo.setRenderer(new GrpcServiceCellRenderer());
		this.grpcServiceCombo.setMaximumRowCount(12);
		this.grpcMethodCombo.setRenderer(new DefaultListCellRenderer());
		this.grpcMethodCombo.setMaximumRowCount(12);
		this.urlParamSyncTimer = new javax.swing.Timer(350, e -> syncParamsFromUrlField());
		this.urlParamSyncTimer.setRepeats(false);
		this.headerPresets = stateService.getHeaderPresets();
		this.stressTestsEnabled = stateService.isStressTestsEnabled();
		this.defaultTimeoutMillis = stateService.getDefaultTimeoutMillis();
		this.bodyGenerator = new BodyGeneratorActions(project, root, requestBodyArea);

		buildComponent();
		configureGrpcComboPopups();
		attachAutoSaveListeners();
	}

	public JComponent getComponent() {
		return root;
	}

	public JTabbedPane getRequestTabs() {
		return requestTabs;
	}

	public void focusBody() {
		requestBodyArea.requestFocusInWindow();
	}

	public void dispose() {
		urlParamSyncTimer.stop();
		kafkaListenerService.shutdown();
		if (debugCallSession != null) {
			debugCallSession.abandon(true);
		}
	}

	public void updateHeaderPresets(List<HeaderPresetState> presets) {
		this.headerPresets = presets == null ? new ArrayList<>() : presets;
		updateHeaderNameEditor(
			activeNode != null && activeNode.requestType == RequestType.GRPC ? RequestType.GRPC : RequestType.HTTP
		);
	}

	public void setStressTestsEnabled(boolean enabled) {
		if (stressTestsEnabled == enabled) {
			return;
		}
		stressTestsEnabled = enabled;
		updateStressTabVisibility();
	}

	public void setDefaultTimeoutMillis(int timeoutMillis) {
		defaultTimeoutMillis = normalizeTimeout(timeoutMillis);
		if (activeNode != null && activeNode.type == NodeType.REQUEST && activeNode.requestType != RequestType.KAFKA_LISTEN) {
			RequestDetailsState details = stateService.getRequestDetails(activeNode.id);
			if (details == null) {
				setActiveTimeoutSpinnerValue(defaultTimeoutMillis);
			}
		}
	}

	// ---- build ----

	private void buildComponent() {
		requestTopPanel.add(buildHttpTopBar(), "http");
		requestTopPanel.add(buildGrpcTopBar(), "grpc");
		requestTopPanel.add(buildKafkaTopBar(), "kafka");
		requestTopPanel.add(buildKafkaListenTopBar(), "kafkaListen");
		JPanel topContainer = new JPanel(new BorderLayout());
		topContainer.add(requestTopPanel, BorderLayout.CENTER);
		root.add(topContainer, BorderLayout.NORTH);

		bodyPanel.add(new JBScrollPane(requestBodyArea), "raw");
		bodyPanel.add(buildFormDataPanel(), "form");
		bodyPanel.add(buildBinaryPanel(), "binary");
		headersPanel = buildHeadersPanel();
		beforeScriptComponent = new JBScrollPane(beforeScriptArea);
		afterScriptComponent = new JBScrollPane(afterScriptArea);
		paramsPanel.add(buildParamsTablePanel(), "table");
		paramsPanel.add(buildKafkaParamsPanel(), "kafka");
		paramsPanel.add(buildKafkaListenParamsPanel(), "kafkaListen");
		rebuildRequestTabsFor(RequestType.HTTP);
		requestTabs.setMinimumSize(new Dimension(0, 0));
		responseViewer.getComponent().setMinimumSize(new Dimension(0, 0));

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, requestTabs, responseViewer.getComponent());
		splitPane.setResizeWeight(0.6);
		SplitPaneStyling.applyThinBlackDivider(splitPane);
		JSplitPane workspaceSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane, buildTestsPanel());
		workspaceSplit.setResizeWeight(0.82);
		SplitPaneStyling.applyThinBlackDivider(workspaceSplit);
		root.add(workspaceSplit, BorderLayout.CENTER);
	}

	private JPanel buildTestsPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel header = new JPanel(new BorderLayout());
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		header.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6));
		header.add(new JLabel("Tests"), BorderLayout.WEST);
		actions.add(runTestsButton);
		actions.add(createTestButton);
		header.add(actions, BorderLayout.EAST);
		testsTree.setRootVisible(false);
		testsTree.setShowsRootHandles(true);
		testsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		testsTree.setCellRenderer(new RequestTestTreeRenderer());
		testsTree.addTreeSelectionListener(e -> {
			if (!isLoading) {
				selectTestFromTree();
			}
		});
		testsTree.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				showTestsContextMenu(event);
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				showTestsContextMenu(event);
			}
		});
		runTestsButton.addActionListener(e -> runAllTests());
		createTestButton.addActionListener(e -> createTest());
		panel.add(header, BorderLayout.NORTH);
		panel.add(new JBScrollPane(testsTree), BorderLayout.CENTER);
		panel.setPreferredSize(new Dimension(220, 120));
		return panel;
	}

	private void updateStressTabVisibility() {
		JComponent stressComponent = stressSettingsPanel.getComponent();
		int stressTabIndex = requestTabs.indexOfComponent(stressComponent);
		if (stressTestsEnabled && stressTabIndex < 0) {
			requestTabs.add("Stress", stressComponent);
		} else if (!stressTestsEnabled && stressTabIndex >= 0) {
			requestTabs.removeTabAt(stressTabIndex);
		}
	}

	private void rebuildRequestTabsFor(RequestType requestType) {
		removeRequestTab(bodyPanel);
		removeRequestTab(paramsPanel);
		removeRequestTab(headersPanel);
		removeRequestTab(beforeScriptComponent);
		removeRequestTab(afterScriptComponent);
		removeRequestTab(stressSettingsPanel.getComponent());

		boolean kafkaListen = requestType == RequestType.KAFKA_LISTEN;
		if (!kafkaListen) {
			requestTabs.add("Body", bodyPanel);
		}
		requestTabs.add("Params", paramsPanel);
		requestTabs.add("Headers", headersPanel);
		requestTabs.add(kafkaListen ? "On Message" : "Before Request", beforeScriptComponent);
		if (!kafkaListen) {
			requestTabs.add(requestType == RequestType.GRPC ? "On Message" : "After Request", afterScriptComponent);
		}
		updateStressTabVisibility();
	}

	private void removeRequestTab(JComponent component) {
		int index = component == null ? -1 : requestTabs.indexOfComponent(component);
		if (index >= 0) {
			requestTabs.removeTabAt(index);
		}
	}

	private void rebuildTestsTree(RequestStatusState status) {
		testsRootNode.removeAllChildren();
		testsRootNode.add(new DefaultMutableTreeNode(RequestTestTreeRenderer.TestView.base(status == null ? "" : status.resultStatus)));
		List<RequestTestState> tests = status == null || status.tests == null ? List.of() : status.tests;
		for (RequestTestState test : tests) {
			if (test != null) {
				testsRootNode.add(new DefaultMutableTreeNode(RequestTestTreeRenderer.TestView.test(test)));
			}
		}
		testsTreeModel.reload();
		for (int row = 0; row < testsTree.getRowCount(); row++) {
			testsTree.expandRow(row);
		}
		selectTestTreeNode(activeTestId);
	}

	private void selectTestTreeNode(String testId) {
		for (int index = 0; index < testsRootNode.getChildCount(); index++) {
			DefaultMutableTreeNode child = (DefaultMutableTreeNode) testsRootNode.getChildAt(index);
			Object value = child.getUserObject();
			if (value instanceof RequestTestTreeRenderer.TestView testView
				&& Objects.equals(testView.id(), testId)) {
				testsTree.setSelectionPath(new TreePath(child.getPath()));
				return;
			}
		}
		if (testsRootNode.getChildCount() > 0) {
			DefaultMutableTreeNode child = (DefaultMutableTreeNode) testsRootNode.getChildAt(0);
			testsTree.setSelectionPath(new TreePath(child.getPath()));
		}
	}

	private void selectTestFromTree() {
		RequestTestTreeRenderer.TestView test = selectedTestView();
		if (test == null || activeNode == null) {
			return;
		}
		String nextTestId = test.base() ? null : test.id();
		if (Objects.equals(activeTestId, nextTestId)) {
			return;
		}
		saveActive();
		activeTestId = nextTestId;
		loadActiveTestSnapshot();
	}

	private void loadActiveTestSnapshot() {
		if (activeNode == null) {
			return;
		}
		RequestDetailsState details = stateService.getRequestDetails(activeNode.id);
		RequestStatusState status = stateService.getRequestStatus(activeNode.id);
		RequestTestState test = findTest(status, activeTestId);
		isLoading = true;
		try {
			if (test == null) {
				loadSharedStatus(status);
				formDataTableModel.setEntries(status != null ? status.formData : List.of());
				binaryFileField.setText(status != null ? safe(status.binaryFilePath) : "");
				headersTableModel.setHeaders(status != null ? status.requestHeaders : List.of(), activeNode.requestType != RequestType.GRPC);
				List<HeaderEntryState> params = status != null ? status.requestParams : List.of();
				if (activeNode.requestType == RequestType.HTTP) {
					params = UrlParamUtils.mergeParamsWithUrl(params, details != null ? details.url : null);
				}
				paramsTableModel.setHeaders(params, true);
			} else {
				applyTestSnapshot(test, details);
			}
		} finally {
			isLoading = false;
		}
	}

	private void applyTestSnapshot(RequestTestState test, RequestDetailsState details) {
		requestBodyArea.setText(safe(test.requestBody));
		beforeScriptArea.setText(safe(test.beforeScript));
		afterScriptArea.setText(safe(test.afterScript));
		formDataTableModel.setEntries(test.formData == null ? List.of() : test.formData);
		binaryFileField.setText(safe(test.binaryFilePath));
		headersTableModel.setHeaders(test.requestHeaders == null ? List.of() : test.requestHeaders, activeNode.requestType != RequestType.GRPC);
		List<HeaderEntryState> params = test.requestParams == null ? List.of() : test.requestParams;
		if (activeNode.requestType == RequestType.HTTP) {
			params = UrlParamUtils.mergeParamsWithUrl(params, details != null ? details.url : null);
		}
		paramsTableModel.setHeaders(params, true);
		responseViewer.setContent(
			safe(test.responseBody),
			safe(test.responseHeaders),
			safe(test.responseCookies),
			safe(test.logs)
		);
	}

	private void createTest() {
		if (activeNode == null || activeNode.type != NodeType.REQUEST) {
			return;
		}
		saveActive();
		String name = TaskbarWindowSupport.showInputDialog(root, "Test name:", "Create Test", "");
		if (name == null || name.isBlank()) {
			return;
		}
		RequestStatusState status = stateService.getRequestStatus(activeNode.id);
		if (status == null) {
			status = buildStatus(activeNode.id);
		}
		if (status.tests == null) {
			status.tests = new ArrayList<>();
		}
		RequestTestState test = cloneBaseAsTest(status);
		test.id = UUID.randomUUID().toString();
		test.name = name.trim();
		status.tests.add(test);
		stateService.saveRequestStatus(status);
		activeTestId = test.id;
		rebuildTestsTree(status);
		loadActiveTestSnapshot();
	}

	private void showTestsContextMenu(MouseEvent event) {
		if (!event.isPopupTrigger()) {
			return;
		}
		TreePath path = testsTree.getPathForLocation(event.getX(), event.getY());
		if (path == null) {
			return;
		}
		testsTree.setSelectionPath(path);
		RequestTestTreeRenderer.TestView test = selectedTestView();
		if (test == null || test.base()) {
			return;
		}
		JPopupMenu menu = new JPopupMenu();
		JMenuItem toggleDisabled = new JMenuItem(test.disabled() ? "Enable" : "Disable");
		JMenuItem delete = new JMenuItem("Delete");
		toggleDisabled.addActionListener(e -> toggleSelectedTestDisabled());
		delete.addActionListener(e -> deleteSelectedTest());
		menu.add(toggleDisabled);
		menu.add(delete);
		menu.show(testsTree, event.getX(), event.getY());
	}

	private RequestTestTreeRenderer.TestView selectedTestView() {
		TreePath path = testsTree.getSelectionPath();
		if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)) {
			return null;
		}
		Object value = node.getUserObject();
		return value instanceof RequestTestTreeRenderer.TestView test ? test : null;
	}

	private void deleteSelectedTest() {
		RequestTestTreeRenderer.TestView selected = selectedTestView();
		if (selected == null || selected.base() || activeNode == null) {
			return;
		}
		saveActive();
		RequestStatusState status = stateService.getRequestStatus(activeNode.id);
		if (status == null || status.tests == null) {
			return;
		}
		status.tests.removeIf(test -> test != null && Objects.equals(test.id, selected.id()));
		if (Objects.equals(activeTestId, selected.id())) {
			activeTestId = null;
		}
		stateService.saveRequestStatus(status);
		rebuildTestsTree(status);
		loadActiveTestSnapshot();
	}

	private void toggleSelectedTestDisabled() {
		RequestTestTreeRenderer.TestView selected = selectedTestView();
		if (selected == null || selected.base() || activeNode == null) {
			return;
		}
		saveActive();
		RequestStatusState status = stateService.getRequestStatus(activeNode.id);
		RequestTestState test = findTest(status, selected.id());
		if (test == null) {
			return;
		}
		test.disabled = !test.disabled;
		stateService.saveRequestStatus(status);
		rebuildTestsTree(status);
	}

	private JPanel buildHttpTopBar() {
		JPanel topBar = new JPanel(new BorderLayout());
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		httpUrlField.setColumns(40);
		configureIconButton(httpSendButton, "Send");
		configureIconButton(httpSendDownloadButton, "Send and Download");
		configureStopButton(httpStopButton);
		configureIconButton(httpDebugButton, "Debug Call");
		controls.add(httpMethodCombo);
		controls.add(httpPayloadCombo);
		controls.add(new JLabel("URL"));
		controls.add(httpUrlField);
		controls.add(httpSendButton);
		controls.add(httpStopButton);
		controls.add(httpSendDownloadButton);
		controls.add(httpDebugButton);
		controls.add(createRequestMenuButton());
		topBar.add(controls, BorderLayout.CENTER);
		topBar.add(buildTimeoutPanel(httpTimeoutSpinner), BorderLayout.EAST);
		httpSendButton.addActionListener(e -> executeHttp());
		httpSendDownloadButton.addActionListener(e -> executeHttpDownload());
		httpStopButton.addActionListener(e -> stopActiveExecution());
		httpDebugButton.addActionListener(e -> startDebugCall());
		return topBar;
	}

	private JPanel buildKafkaTopBar() {
		JPanel topBar = new JPanel(new BorderLayout());
		JPanel controls = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 4, 0, 4);
		constraints.anchor = GridBagConstraints.WEST;

		kafkaBootstrapCombo.setEditable(true);
		kafkaTopicCombo.setEditable(true);
		kafkaBootstrapCombo.setPrototypeDisplayValue("localhost:9092,localhost:9093");
		kafkaTopicCombo.setPrototypeDisplayValue("example.kafka.topic.name");
		kafkaKeyField.setColumns(18);

		constraints.gridx = 0;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		controls.add(new JLabel("Bootstrap servers"), constraints);

		constraints.gridx = 1;
		constraints.weightx = 0.35;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		controls.add(kafkaBootstrapCombo, constraints);

		constraints.gridx = 2;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		controls.add(new JLabel("Topic"), constraints);

		constraints.gridx = 3;
		constraints.weightx = 0.35;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		controls.add(kafkaTopicCombo, constraints);

		constraints.gridx = 4;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		controls.add(new JLabel("Key"), constraints);

		constraints.gridx = 5;
		constraints.weightx = 0.3;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		controls.add(kafkaKeyField, constraints);

		configureIconButton(kafkaReloadButton, "Refresh Kafka metadata");
		configureIconButton(kafkaSendButton, "Send Kafka message");
		configureStopButton(kafkaStopButton);

		constraints.gridx = 6;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		controls.add(kafkaReloadButton, constraints);

		constraints.gridx = 7;
		controls.add(kafkaSendButton, constraints);

		constraints.gridx = 8;
		controls.add(kafkaStopButton, constraints);

		constraints.gridx = 9;
		controls.add(createRequestMenuButton(), constraints);
		topBar.add(controls, BorderLayout.CENTER);
		topBar.add(buildTimeoutPanel(kafkaTimeoutSpinner), BorderLayout.EAST);

		kafkaReloadButton.addActionListener(e -> refreshKafkaMetadata());
		kafkaSendButton.addActionListener(e -> executeKafka());
		kafkaStopButton.addActionListener(e -> stopActiveExecution());
		return topBar;
	}

	private JPanel buildKafkaListenTopBar() {
		JPanel topBar = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 4, 0, 4);
		constraints.anchor = GridBagConstraints.WEST;

		kafkaListenBootstrapCombo.setEditable(true);
		kafkaListenTopicCombo.setEditable(true);
		kafkaListenBootstrapCombo.setPrototypeDisplayValue("localhost:9092,localhost:9093");
		kafkaListenTopicCombo.setPrototypeDisplayValue("example.kafka.topic.name");
		kafkaGroupIdField.setColumns(18);

		constraints.gridx = 0;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		topBar.add(new JLabel("Bootstrap servers"), constraints);

		constraints.gridx = 1;
		constraints.weightx = 0.35;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		topBar.add(kafkaListenBootstrapCombo, constraints);

		constraints.gridx = 2;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		topBar.add(new JLabel("Topic"), constraints);

		constraints.gridx = 3;
		constraints.weightx = 0.35;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		topBar.add(kafkaListenTopicCombo, constraints);

		constraints.gridx = 4;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		topBar.add(new JLabel("Group ID"), constraints);

		constraints.gridx = 5;
		constraints.weightx = 0.3;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		topBar.add(kafkaGroupIdField, constraints);

		configureIconButton(kafkaListenReloadButton, "Refresh Kafka metadata");
		kafkaListenButton.setToolTipText("Start or stop Kafka listening");
		disableButtonFocus(kafkaListenButton);

		constraints.gridx = 6;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		topBar.add(kafkaListenReloadButton, constraints);

		constraints.gridx = 7;
		topBar.add(kafkaListenButton, constraints);

		constraints.gridx = 8;
		topBar.add(createRequestMenuButton(), constraints);

		kafkaListenReloadButton.addActionListener(e -> refreshKafkaListenMetadata());
		kafkaListenButton.addActionListener(e -> toggleKafkaListening());
		return topBar;
	}

	private JPanel buildGrpcTopBar() {
		JPanel topBar = new JPanel(new BorderLayout());
		JPanel controls = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 4, 0, 4);
		constraints.anchor = GridBagConstraints.WEST;

		grpcTargetField.setColumns(18);
		grpcServiceCombo.setPrototypeDisplayValue("com.example.very.long.grpc.ServiceNameForPreview");
		grpcMethodCombo.setPrototypeDisplayValue("VeryLongMethodNameForPreviewSelection");

		constraints.gridx = 0;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		controls.add(new JLabel("Target"), constraints);

		constraints.gridx = 1;
		constraints.weightx = 0.2;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		controls.add(grpcTargetField, constraints);

		constraints.gridx = 2;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		controls.add(new JLabel("Service"), constraints);

		constraints.gridx = 3;
		constraints.weightx = 0.4;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		controls.add(grpcServiceCombo, constraints);

		constraints.gridx = 4;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		controls.add(new JLabel("Method"), constraints);

		constraints.gridx = 5;
		constraints.weightx = 0.4;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		controls.add(grpcMethodCombo, constraints);

		configureIconButton(grpcReloadButton, "Reload");
		configureIconButton(grpcSendButton, "Send");
		configureStopButton(grpcStopButton);
		configureIconButton(grpcDebugButton, "Debug Call");

		constraints.gridx = 6;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		controls.add(grpcReloadButton, constraints);

		constraints.gridx = 7;
		controls.add(grpcSendButton, constraints);

		constraints.gridx = 8;
		controls.add(grpcStopButton, constraints);

		constraints.gridx = 9;
		controls.add(grpcDebugButton, constraints);

		constraints.gridx = 10;
		JButton menuButton = createRequestMenuButton();
		controls.add(menuButton, constraints);
		topBar.add(controls, BorderLayout.CENTER);
		topBar.add(buildTimeoutPanel(grpcTimeoutSpinner), BorderLayout.EAST);

		grpcReloadButton.addActionListener(e -> reloadGrpcServices());
		grpcSendButton.addActionListener(e -> executeGrpc());
		grpcStopButton.addActionListener(e -> stopActiveExecution());
		grpcDebugButton.addActionListener(e -> startDebugCall());
		return topBar;
	}

	private JButton createRequestMenuButton() {
		JButton button = new JButton("\u22EE");
		configureIconButton(button, "Request actions");
		button.addActionListener(e -> showRequestMenu(button));
		return button;
	}

	private void configureIconButton(JButton button, String tooltip) {
		button.setToolTipText(tooltip);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setPreferredSize(ICON_BUTTON_SIZE);
		disableButtonFocus(button);
	}

	private void configureStopButton(JButton button) {
		configureIconButton(button, "Stop");
		button.setForeground(Color.RED);
		button.setEnabled(false);
	}

	private static JSpinner createTimeoutSpinner() {
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 3_600_000, 5));
		spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
		JComponent editor = spinner.getEditor();
		if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
			defaultEditor.getTextField().setColumns(7);
		}
		spinner.setToolTipText("Timeout in milliseconds");
		return spinner;
	}

	private JPanel buildTimeoutPanel(JSpinner spinner) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		panel.add(new JLabel("Timeout"));
		panel.add(spinner);
		panel.add(new JLabel("ms"));
		return panel;
	}

	private int timeoutValue(JSpinner spinner) {
		try {
			spinner.commitEdit();
		} catch (Exception ignored) {
			// Keep the last valid spinner value if the user typed invalid text.
		}
		Object value = spinner.getValue();
		return value instanceof Number number ? normalizeTimeout(number.intValue()) : defaultTimeoutMillis;
	}

	private int normalizeTimeout(int timeoutMillis) {
		return Math.max(0, timeoutMillis);
	}

	private int requestTimeout(RequestDetailsState details) {
		return details == null ? defaultTimeoutMillis : normalizeTimeout(details.timeoutMillis);
	}

	private void setActiveTimeoutSpinnerValue(int timeoutMillis) {
		if (activeNode == null) {
			return;
		}
		if (activeNode.requestType == RequestType.HTTP) {
			httpTimeoutSpinner.setValue(normalizeTimeout(timeoutMillis));
		} else if (activeNode.requestType == RequestType.GRPC) {
			grpcTimeoutSpinner.setValue(normalizeTimeout(timeoutMillis));
		} else if (activeNode.requestType == RequestType.KAFKA) {
			kafkaTimeoutSpinner.setValue(normalizeTimeout(timeoutMillis));
		}
	}

	private void disableButtonFocus(JButton button) {
		button.setFocusable(false);
		button.setRequestFocusEnabled(false);
	}

	private void showRequestMenu(JButton anchor) {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem openRequestItem = new JMenuItem("Open Request");
		JMenuItem openResponseItem = new JMenuItem("Open Response");
		JMenuItem protoBodyItem = new JMenuItem("Proto body");
		openRequestItem.addActionListener(e -> openRequestWindow());
		openResponseItem.addActionListener(e -> openResponseWindow());
		protoBodyItem.addActionListener(e -> generateBodyFromProto());
		boolean enabled =
			activeNode != null && activeNode.type == NodeType.REQUEST && activeNode.requestType != RequestType.CHAIN;
		openRequestItem.setEnabled(enabled);
		openResponseItem.setEnabled(enabled);
		protoBodyItem.setEnabled(enabled);
		menu.add(openRequestItem);
		menu.add(openResponseItem);
		menu.addSeparator();
		menu.add(protoBodyItem);
		menu.show(anchor, 0, anchor.getHeight());
	}

	private JPanel buildHeadersPanel() {
		return buildTablePanel(
			headersTable,
			this::configureHeadersTableColumns,
			addHeaderButton,
			removeHeaderButton,
			headersTableModel::addEmptyRow,
			headersTableModel::removeRow
		);
	}

	private JPanel buildParamsTablePanel() {
		return buildTablePanel(
			paramsTable,
			this::configureParamsTableColumns,
			addParamButton,
			removeParamButton,
			paramsTableModel::addEmptyRow,
			paramsTableModel::removeRow
		);
	}

	private JPanel buildKafkaParamsPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(8, 8, 0, 8);
		constraints.anchor = GridBagConstraints.WEST;

		addKafkaParamRow(form, constraints, 0, "Key type", kafkaKeyTypeCombo);
		addKafkaParamRow(form, constraints, 1, "Body type", kafkaBodyTypeCombo);
		kafkaPartitionsField.setColumns(16);
		addKafkaParamRow(form, constraints, 2, "Partitions", kafkaPartitionsField);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(form, BorderLayout.NORTH);
		panel.add(wrapper, BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildKafkaListenParamsPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(8, 8, 0, 8);
		constraints.anchor = GridBagConstraints.WEST;
		addKafkaParamRow(form, constraints, 0, "Offset strategy", kafkaOffsetStrategyCombo);
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(form, BorderLayout.NORTH);
		panel.add(wrapper, BorderLayout.CENTER);
		return panel;
	}

	private void addKafkaParamRow(
		JPanel form,
		GridBagConstraints constraints,
		int row,
		String label,
		JComponent field
	) {
		constraints.gridy = row;
		constraints.gridx = 0;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		form.add(new JLabel(label), constraints);

		constraints.gridx = 1;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		form.add(field, constraints);
	}

	private JPanel buildFormDataPanel() {
		chooseFormFileButton.addActionListener(e -> chooseFormDataFile());
		return buildTablePanel(
			formDataTable,
			this::configureFormDataTableColumns,
			addFormDataButton,
			removeFormDataButton,
			formDataTableModel::addEmptyRow,
			formDataTableModel::removeRow,
			chooseFormFileButton
		);
	}

	private JPanel buildTablePanel(
		JTable table,
		Runnable configureColumns,
		JButton addButton,
		JButton removeButton,
		Runnable addRow,
		IntConsumer removeRow,
		JButton... extraButtons
	) {
		JPanel panel = new JPanel(new BorderLayout());
		table.setFillsViewportHeight(true);
		configureColumns.run();
		panel.add(new JBScrollPane(table), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		disableButtonFocus(addButton);
		disableButtonFocus(removeButton);
		actions.add(addButton);
		actions.add(removeButton);
		for (JButton extraButton : extraButtons) {
			disableButtonFocus(extraButton);
			actions.add(extraButton);
		}
		addButton.addActionListener(e -> addRow.run());
		removeButton.addActionListener(e -> removeRow.accept(table.getSelectedRow()));
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	private JPanel buildBinaryPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel content = new JPanel(new FlowLayout(FlowLayout.LEFT));
		binaryFileField.setColumns(36);
		disableButtonFocus(binaryBrowseButton);
		content.add(new JLabel("File"));
		content.add(binaryFileField);
		content.add(binaryBrowseButton);
		binaryBrowseButton.addActionListener(e -> chooseBinaryFile());
		panel.add(content, BorderLayout.NORTH);
		return panel;
	}

	private void chooseBinaryFile() {
		File file = chooseFile("Select Binary File");
		if (file != null) {
			binaryFileField.setText(file.getAbsolutePath());
		}
	}

	private void chooseFormDataFile() {
		int row = formDataTable.getSelectedRow();
		if (row < 0) {
			return;
		}
		File file = chooseFile("Select Form Data File");
		if (file != null) {
			formDataTableModel.setValueAt("File", row, 2);
			formDataTableModel.setValueAt(file.getAbsolutePath(), row, 3);
		}
	}

	private File chooseFile(String title) {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(title);
		return TaskbarWindowSupport.showOpenDialog(chooser, root) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
	}

	private void configureHeadersTableColumns() {
		configureEnabledColumn(headersTable);

		headersTable.getColumnModel().getColumn(1).setCellEditor(createHeaderNameEditor(COMMON_HEADER_NAMES));
		headersTable.getColumnModel().getColumn(2).setCellEditor(new HeaderValueCellEditor(this::buildHeaderPresetMap));
	}

	private void configureParamsTableColumns() {
		configureEnabledColumn(paramsTable);
	}

	private void configureFormDataTableColumns() {
		configureEnabledColumn(formDataTable);

		TableColumn typeColumn = formDataTable.getColumnModel().getColumn(2);
		JComboBox<String> typeCombo = new JComboBox<>(new String[] {"Text", "File"});
		typeColumn.setCellEditor(new javax.swing.DefaultCellEditor(typeCombo));
		typeColumn.setPreferredWidth(90);
		typeColumn.setMaxWidth(120);
	}

	private void configureEnabledColumn(JTable table) {
		TableColumn enabledColumn = table.getColumnModel().getColumn(0);
		enabledColumn.setPreferredWidth(60);
		enabledColumn.setMinWidth(60);
		enabledColumn.setMaxWidth(60);
		enabledColumn.setResizable(false);
	}

	private void updateHeaderNameEditor(RequestType type) {
		List<String> variants = type == RequestType.GRPC ? GRPC_HEADER_NAMES : COMMON_HEADER_NAMES;
		headersTable.getColumnModel().getColumn(1).setCellEditor(createHeaderNameEditor(variants));
	}

	private void switchPayloadType() {
		String label = PayloadTypes.resolveLabel(httpPayloadCombo.getSelectedItem());
		if ("Form Data".equals(label) || "x-www-form-urlencoded".equals(label)) {
			bodyCards.show(bodyPanel, "form");
		} else if ("Binary".equals(label)) {
			bodyCards.show(bodyPanel, "binary");
		} else {
			bodyCards.show(bodyPanel, "raw");
		}
	}

	private TableCellEditor createHeaderNameEditor(List<String> variants) {
		return new HeaderNameCellEditor(project, mergeHeaderVariants(variants));
	}

	private List<String> mergeHeaderVariants(List<String> base) {
		List<String> merged = new ArrayList<>();
		if (base != null) {
			merged.addAll(base);
		}
		for (HeaderPresetState preset : headerPresets) {
			if (preset == null || preset.name == null || preset.name.isBlank()) {
				continue;
			}
			if (!merged.contains(preset.name)) {
				merged.add(preset.name);
			}
		}
		return merged;
	}

	private Map<String, List<String>> buildHeaderPresetMap() {
		Map<String, List<String>> map = new LinkedHashMap<>();
		for (HeaderPresetState preset : headerPresets) {
			if (preset == null || preset.name == null || preset.name.isBlank()) {
				continue;
			}
			String key = preset.name.trim().toLowerCase(Locale.ROOT);
			List<String> values = preset.values == null ? List.of() : new ArrayList<>(preset.values);
			map.put(key, values);
		}
		return map;
	}

	private void configureGrpcComboPopups() {
		ComboPopupSizer.install(grpcServiceCombo, this::resolveGrpcPopupMaxWidth);
		ComboPopupSizer.install(grpcMethodCombo, this::resolveGrpcPopupMaxWidth);
	}

	private String longestServiceName(List<GrpcServiceInfo> services) {
		String longest = "";
		for (GrpcServiceInfo info : services) {
			String shortName = shortGrpcServiceName(info == null ? null : info.name);
			if (shortName.length() > longest.length()) {
				longest = shortName;
			}
		}
		return longest.isEmpty() ? "Service" : longest;
	}

	private String shortGrpcServiceName(String fullName) {
		String safeName = fullName == null ? "" : fullName.trim();
		int separator = safeName.lastIndexOf('.');
		return separator >= 0 && separator < safeName.length() - 1 ? safeName.substring(separator + 1) : safeName;
	}

	private String longestMethodName(
		List<GrpcServiceInfo> services,
		String selectedService
	) {
		String longest = "";
		for (GrpcServiceInfo info : services) {
			if (info == null || !Objects.equals(info.name, selectedService)) {
				continue;
			}
			for (String method : info.methods) {
				if (method != null && method.length() > longest.length()) {
					longest = method;
				}
			}
			break;
		}
		return longest.isEmpty() ? "MethodName" : longest;
	}

	private int resolveGrpcPopupMaxWidth() {
		int width = root.getWidth();
		int max = width > 0 ? (int) (width * 0.9) : 900;
		if (max < 500) {
			max = 500;
		}
		if (max > 1200) {
			max = 1200;
		}
		return max;
	}

	// ---- load / save ----

	public void load(NodeState node) {
		this.activeNode = node;
		this.activeTestId = null;
		if (node == null) {
			return;
		}
		responseViewer.setCookiesVisible(node.requestType == RequestType.HTTP);
		rebuildRequestTabsFor(node.requestType);
		if (node.requestType == RequestType.HTTP) {
			loadHttp(node.id);
			requestTopCards.show(requestTopPanel, "http");
		} else if (node.requestType == RequestType.GRPC) {
			loadGrpc(node.id);
			requestTopCards.show(requestTopPanel, "grpc");
		} else if (node.requestType == RequestType.KAFKA) {
			loadKafka(node.id);
			requestTopCards.show(requestTopPanel, "kafka");
		} else if (node.requestType == RequestType.KAFKA_LISTEN) {
			loadKafkaListen(node);
			requestTopCards.show(requestTopPanel, "kafkaListen");
		}
		rebuildTestsTree(stateService.getRequestStatus(node.id));
	}

	private void loadHttp(String requestId) {
		isLoading = true;
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		RequestStatusState status = stateService.getRequestStatus(requestId);
		updateHeaderNameEditor(RequestType.HTTP);
		httpMethodCombo.setSelectedItem(details != null && details.method != null ? details.method : "GET");
		httpPayloadCombo.setSelectedItem(PayloadTypes.resolveLabel(details != null ? details.payloadType : null));
		httpUrlField.setText(details != null && details.url != null ? details.url : "");
		httpTimeoutSpinner.setValue(requestTimeout(details));
		loadSharedStatus(status);
		formDataTableModel.setEntries(status != null ? status.formData : List.of());
		binaryFileField.setText(status != null ? safe(status.binaryFilePath) : "");
		headersTableModel.setHeaders(status != null ? status.requestHeaders : List.of(), true);
		List<HeaderEntryState> mergedParams =
			UrlParamUtils.mergeParamsWithUrl(status != null ? status.requestParams : List.of(),
				details != null ? details.url : null);
		paramsTableModel.setHeaders(mergedParams, true);
		paramsCards.show(paramsPanel, "table");
		stressSettingsPanel.showFor(RequestType.HTTP);
		switchPayloadType();
		isLoading = false;
	}

	private void loadGrpc(String requestId) {
		isLoading = true;
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		RequestStatusState status = stateService.getRequestStatus(requestId);
		updateHeaderNameEditor(RequestType.GRPC);
		grpcTargetField.setText(details != null ? safe(details.target) : "");
		grpcTimeoutSpinner.setValue(requestTimeout(details));
		loadSharedStatus(status);
		bodyCards.show(bodyPanel, "raw");
		headersTableModel.setHeaders(status != null ? status.requestHeaders : List.of(), false);
		paramsTableModel.setHeaders(status != null ? status.requestParams : List.of(), true);
		paramsCards.show(paramsPanel, "table");
		stressSettingsPanel.showFor(RequestType.GRPC);
		isLoading = false;
		if (details != null && details.target != null && !details.target.isBlank()) {
			if (details.service != null) {
				grpcServiceSelection.put(requestId, details.service);
			}
			reloadGrpcServices();
		}
	}

	private void loadKafka(String requestId) {
		isLoading = true;
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		RequestStatusState status = stateService.getRequestStatus(requestId);
		updateHeaderNameEditor(RequestType.HTTP);
		setComboEditorText(kafkaBootstrapCombo, details != null ? safe(details.kafkaBootstrapServers) : "");
		setComboEditorText(kafkaTopicCombo, details != null ? safe(details.kafkaTopic) : "");
		kafkaKeyField.setText(details != null ? safe(details.kafkaKey) : "");
		kafkaTimeoutSpinner.setValue(requestTimeout(details));
		loadSharedStatus(status);
		bodyCards.show(bodyPanel, "raw");
		headersTableModel.setHeaders(status != null ? status.requestHeaders : List.of(), true);
		kafkaKeyTypeCombo.setSelectedItem(status != null && status.kafkaKeyType != null ? status.kafkaKeyType : "String");
		kafkaBodyTypeCombo.setSelectedItem(status != null && status.kafkaBodyType != null ? status.kafkaBodyType : "JSON");
		kafkaPartitionsField.setText(status != null ? safe(status.kafkaPartitions) : "");
		paramsCards.show(paramsPanel, "kafka");
		stressSettingsPanel.showFor(RequestType.KAFKA);
		isLoading = false;
	}

	private void loadKafkaListen(NodeState node) {
		isLoading = true;
		RequestDetailsState details = stateService.getRequestDetails(node.id);
		RequestStatusState status = stateService.getRequestStatus(node.id);
		updateHeaderNameEditor(RequestType.HTTP);
		setComboEditorText(kafkaListenBootstrapCombo, details != null ? safe(details.kafkaBootstrapServers) : "");
		setComboEditorText(kafkaListenTopicCombo, details != null ? safe(details.kafkaTopic) : "");
		String groupId = details != null ? safe(details.kafkaGroupId) : "";
		kafkaGroupIdField.setText(groupId.isBlank() ? safe(node.name) + "-webrunner" : groupId);
		loadSharedStatus(status);
		bodyCards.show(bodyPanel, "raw");
		headersTableModel.setHeaders(status != null ? status.requestHeaders : List.of(), true);
		kafkaOffsetStrategyCombo.setSelectedItem(
			status != null && status.kafkaOffsetStrategy != null ? status.kafkaOffsetStrategy : "Latest"
		);
		paramsCards.show(paramsPanel, "kafkaListen");
		stressSettingsPanel.showFor(RequestType.KAFKA_LISTEN);
		kafkaListenButton.setText(isKafkaListening(node.id) ? "Stop Listening" : "Start Listening");
		isLoading = false;
	}

	private void loadSharedStatus(RequestStatusState status) {
		requestBodyArea.setText(status != null ? safe(status.requestBody) : "");
		beforeScriptArea.setText(status != null ? safe(status.beforeScript) : "");
		afterScriptArea.setText(status != null ? safe(status.afterScript) : "");
		stressSettingsPanel.load(status);
		responseViewer.setContent(
			status != null ? safe(status.responseBody) : "",
			status != null ? safe(status.responseHeaders) : "",
			status != null ? safe(status.responseCookies) : "",
			status != null ? safe(status.logs) : ""
		);
	}

	public void saveActive() {
		if (isLoading || isStoppingTableEditing || isSyncingParamsFromUrl || activeNode == null
			|| activeNode.type != NodeType.REQUEST || isKafkaReloading) {
			return;
		}
		if (activeNode.requestType == RequestType.HTTP) {
			saveHttp(activeNode.id);
		} else if (activeNode.requestType == RequestType.GRPC) {
			saveGrpc(activeNode.id);
		} else if (activeNode.requestType == RequestType.KAFKA) {
			saveKafka(activeNode.id);
		} else if (activeNode.requestType == RequestType.KAFKA_LISTEN) {
			saveKafkaListen(activeNode.id);
		}
	}

	public void stopTableEditing() {
		if (isStoppingTableEditing) {
			return;
		}
		isStoppingTableEditing = true;
		try {
			stopTableEditing(headersTable);
			stopTableEditing(paramsTable);
			stopTableEditing(formDataTable);
		} finally {
			isStoppingTableEditing = false;
		}
	}

	private void stopTableEditing(JTable table) {
		if (!table.isEditing()) {
			return;
		}
		TableCellEditor editor = table.getCellEditor();
		if (editor != null) {
			editor.stopCellEditing();
		}
	}

	private void saveHttp(String requestId) {
		RequestDetailsState details = requestDetailsForSave(requestId, RequestType.HTTP);
		details.method = String.valueOf(httpMethodCombo.getSelectedItem());
		details.payloadType = PayloadTypes.resolveValue(httpPayloadCombo.getSelectedItem());
		details.url = httpUrlField.getText();
		details.timeoutMillis = timeoutValue(httpTimeoutSpinner);
		stateService.saveRequestDetails(details);

		RequestStatusState status = buildStatus(requestId);
		stateService.saveRequestStatus(status);
	}

	private void saveKafkaListen(String requestId) {
		RequestDetailsState details = requestDetailsForSave(requestId, RequestType.KAFKA_LISTEN);
		details.kafkaBootstrapServers = comboEditorText(kafkaListenBootstrapCombo);
		details.kafkaTopic = comboEditorText(kafkaListenTopicCombo);
		details.kafkaGroupId = kafkaGroupIdField.getText();
		stateService.saveRequestDetails(details);

		RequestStatusState status = buildStatus(requestId);
		status.kafkaOffsetStrategy = kafkaOffsetStrategyCombo.getSelectedItem() == null
			? ""
			: String.valueOf(kafkaOffsetStrategyCombo.getSelectedItem());
		stateService.saveRequestStatus(status);
	}

	private void saveGrpc(String requestId) {
		RequestDetailsState details = requestDetailsForSave(requestId, RequestType.GRPC);
		details.target = grpcTargetField.getText();
		details.service =
			grpcServiceCombo.getSelectedItem() == null ? "" : String.valueOf(grpcServiceCombo.getSelectedItem());
		details.grpcMethod =
			grpcMethodCombo.getSelectedItem() == null ? "" : String.valueOf(grpcMethodCombo.getSelectedItem());
		details.grpcStreamingKind = resolveSelectedGrpcStreamingKind(requestId, details.service, details.grpcMethod);
		details.timeoutMillis = timeoutValue(grpcTimeoutSpinner);
		stateService.saveRequestDetails(details);

		RequestStatusState status = buildStatus(requestId);
		stateService.saveRequestStatus(status);
	}

	private void saveKafka(String requestId) {
		RequestDetailsState details = requestDetailsForSave(requestId, RequestType.KAFKA);
		details.kafkaBootstrapServers = comboEditorText(kafkaBootstrapCombo);
		details.kafkaTopic = comboEditorText(kafkaTopicCombo);
		details.kafkaKey = kafkaKeyField.getText();
		details.timeoutMillis = timeoutValue(kafkaTimeoutSpinner);
		stateService.saveRequestDetails(details);

		RequestStatusState status = buildStatus(requestId);
		status.kafkaKeyType =
			kafkaKeyTypeCombo.getSelectedItem() == null ? "" : String.valueOf(kafkaKeyTypeCombo.getSelectedItem());
		status.kafkaBodyType =
			kafkaBodyTypeCombo.getSelectedItem() == null ? "" : String.valueOf(kafkaBodyTypeCombo.getSelectedItem());
		status.kafkaPartitions = kafkaPartitionsField.getText();
		stateService.saveRequestStatus(status);
	}

	private RequestDetailsState requestDetailsForSave(String requestId, RequestType type) {
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		if (details == null) {
			details = new RequestDetailsState();
			details.requestId = requestId;
		}
		details.type = type;
		return details;
	}

	private RequestStatusState buildStatus(String requestId) {
		RequestStatusState existing = stateService.getRequestStatus(requestId);
		RequestStatusState status = copyBaseStatus(existing, requestId);
		status.requestId = requestId;
		if (activeTestId == null) {
			writeEditorSnapshotToBase(status);
		} else {
			RequestTestState test = findTest(status, activeTestId);
			if (test != null) {
				writeEditorSnapshotToTest(test);
			}
		}
		stressSettingsPanel.saveTo(status);
		return status;
	}

	private RequestStatusState copyBaseStatus(RequestStatusState source, String requestId) {
		RequestStatusState copy = new RequestStatusState();
		copy.requestId = requestId;
		if (source == null) {
			return copy;
		}
		copy.requestBody = source.requestBody;
		copy.requestHeaders = cloneHeaders(source.requestHeaders);
		copy.requestParams = cloneHeaders(source.requestParams);
		copy.formData = cloneFormData(source.formData);
		copy.binaryFilePath = source.binaryFilePath;
		copy.responseBody = source.responseBody;
		copy.responseHeaders = source.responseHeaders;
		copy.responseCookies = source.responseCookies;
		copy.logs = source.logs;
		copy.resultStatus = source.resultStatus;
		copy.resultDetails = source.resultDetails;
		copy.beforeScript = source.beforeScript;
		copy.afterScript = source.afterScript;
		copy.tests = cloneTests(source.tests);
		copy.kafkaKeyType = source.kafkaKeyType;
		copy.kafkaBodyType = source.kafkaBodyType;
		copy.kafkaPartitions = source.kafkaPartitions;
		copy.kafkaOffsetStrategy = source.kafkaOffsetStrategy;
		copy.stressEnabled = source.stressEnabled;
		copy.stressRequestsPerSec = source.stressRequestsPerSec;
		copy.stressTotalDuration = source.stressTotalDuration;
		copy.stressTotalDurationUnit = source.stressTotalDurationUnit;
		copy.stressNumberOfRequests = source.stressNumberOfRequests;
		copy.stressParallelWorkers = source.stressParallelWorkers;
		copy.stressRampUpTime = source.stressRampUpTime;
		copy.stressRampUpTimeUnit = source.stressRampUpTimeUnit;
		copy.stressDelayBetweenRequests = source.stressDelayBetweenRequests;
		copy.stressDelayBetweenRequestsUnit = source.stressDelayBetweenRequestsUnit;
		copy.stressJitterFrom = source.stressJitterFrom;
		copy.stressJitterTo = source.stressJitterTo;
		return copy;
	}

	private void writeEditorSnapshotToBase(RequestStatusState status) {
		status.requestBody = requestBodyArea.getText();
		status.requestHeaders = headersTableModel.getHeaders();
		status.requestParams = paramsTableModel.getHeaders();
		status.formData = formDataTableModel.getEntries();
		status.binaryFilePath = binaryFileField.getText();
		status.responseBody = responseViewer.getResponseBody();
		status.responseHeaders = responseViewer.getResponseHeaders();
		status.responseCookies = responseViewer.getResponseCookies();
		status.logs = responseViewer.getLogs();
		status.beforeScript = beforeScriptArea.getText();
		status.afterScript = afterScriptArea.getText();
	}

	private void writeEditorSnapshotToTest(RequestTestState test) {
		test.requestBody = requestBodyArea.getText();
		test.requestHeaders = headersTableModel.getHeaders();
		test.requestParams = paramsTableModel.getHeaders();
		test.formData = formDataTableModel.getEntries();
		test.binaryFilePath = binaryFileField.getText();
		test.responseBody = responseViewer.getResponseBody();
		test.responseHeaders = responseViewer.getResponseHeaders();
		test.responseCookies = responseViewer.getResponseCookies();
		test.logs = responseViewer.getLogs();
		test.beforeScript = beforeScriptArea.getText();
		test.afterScript = afterScriptArea.getText();
	}

	private RequestTestState cloneBaseAsTest(RequestStatusState status) {
		RequestTestState test = new RequestTestState();
		test.requestBody = status == null ? "" : safe(status.requestBody);
		test.requestHeaders = status == null ? new ArrayList<>() : cloneHeaders(status.requestHeaders);
		test.requestParams = status == null ? new ArrayList<>() : cloneHeaders(status.requestParams);
		test.formData = status == null ? new ArrayList<>() : cloneFormData(status.formData);
		test.binaryFilePath = status == null ? "" : safe(status.binaryFilePath);
		test.beforeScript = status == null ? "" : safe(status.beforeScript);
		test.afterScript = status == null ? "" : safe(status.afterScript);
		test.responseBody = "";
		test.responseHeaders = "";
		test.responseCookies = "";
		test.logs = "";
		return test;
	}

	private RequestStatusState activeStatusView(RequestStatusState status) {
		if (status == null || activeTestId == null) {
			return status;
		}
		RequestTestState test = findTest(status, activeTestId);
		if (test == null) {
			return status;
		}
		RequestStatusState view = copyBaseStatus(status, status.requestId);
		view.requestBody = test.requestBody;
		view.requestHeaders = cloneHeaders(test.requestHeaders);
		view.requestParams = cloneHeaders(test.requestParams);
		view.formData = cloneFormData(test.formData);
		view.binaryFilePath = test.binaryFilePath;
		view.responseBody = test.responseBody;
		view.responseHeaders = test.responseHeaders;
		view.responseCookies = test.responseCookies;
		view.logs = test.logs;
		view.beforeScript = test.beforeScript;
		view.afterScript = test.afterScript;
		return view;
	}

	private RequestTestState findTest(RequestStatusState status, String testId) {
		if (status == null || testId == null || status.tests == null) {
			return null;
		}
		for (RequestTestState test : status.tests) {
			if (test != null && Objects.equals(test.id, testId)) {
				return test;
			}
		}
		return null;
	}

	private List<RequestTestState> cloneTests(List<RequestTestState> tests) {
		List<RequestTestState> copy = new ArrayList<>();
		if (tests == null) {
			return copy;
		}
		for (RequestTestState test : tests) {
			if (test == null) {
				continue;
			}
			RequestTestState clone = new RequestTestState();
			clone.id = test.id;
			clone.name = test.name;
			clone.disabled = test.disabled;
			clone.resultStatus = test.resultStatus;
			clone.resultDetails = test.resultDetails;
			clone.requestBody = test.requestBody;
			clone.requestHeaders = cloneHeaders(test.requestHeaders);
			clone.requestParams = cloneHeaders(test.requestParams);
			clone.formData = cloneFormData(test.formData);
			clone.binaryFilePath = test.binaryFilePath;
			clone.beforeScript = test.beforeScript;
			clone.afterScript = test.afterScript;
			clone.responseBody = test.responseBody;
			clone.responseHeaders = test.responseHeaders;
			clone.responseCookies = test.responseCookies;
			clone.logs = test.logs;
			copy.add(clone);
		}
		return copy;
	}

	private List<HeaderEntryState> cloneHeaders(List<HeaderEntryState> headers) {
		List<HeaderEntryState> copy = new ArrayList<>();
		if (headers == null) {
			return copy;
		}
		for (HeaderEntryState header : headers) {
			if (header == null) {
				continue;
			}
			HeaderEntryState clone = new HeaderEntryState();
			clone.id = header.id;
			clone.name = header.name;
			clone.value = header.value;
			clone.enabled = header.enabled;
			copy.add(clone);
		}
		return copy;
	}

	private List<FormEntryState> cloneFormData(List<FormEntryState> entries) {
		List<FormEntryState> copy = new ArrayList<>();
		if (entries == null) {
			return copy;
		}
		for (FormEntryState entry : entries) {
			if (entry == null) {
				continue;
			}
			FormEntryState clone = new FormEntryState();
			clone.id = entry.id;
			clone.name = entry.name;
			clone.value = entry.value;
			clone.enabled = entry.enabled;
			clone.file = entry.file;
			copy.add(clone);
		}
		return copy;
	}

	private boolean isActiveTestDisabled() {
		if (activeNode == null || activeTestId == null) {
			return false;
		}
		RequestTestState test = findTest(stateService.getRequestStatus(activeNode.id), activeTestId);
		return test != null && test.disabled;
	}

	private void updateActiveTestResult(ExecutionResult result) {
		if (activeNode == null || result == null) {
			return;
		}
		updateTestResult(activeNode.id, activeTestId, result);
	}

	private void updateTestResult(String requestId, String testId, ExecutionResult result) {
		if (requestId == null || result == null) {
			return;
		}
		RequestStatusState status = stateService.getRequestStatus(activeNode.id);
		if (status == null) {
			return;
		}
		String resultStatus = resolveTestResultStatus(result);
		String details = result.statusCode + " | " + formatDuration(result.durationMillis) + " | " +
			formatSize(responseBodySize(result.responseBody));
		if (testId == null) {
			status.resultStatus = resultStatus;
			status.resultDetails = details;
			status.responseBody = result.responseBody;
			status.responseHeaders = result.responseHeaders;
			status.responseCookies = result.responseCookies;
			status.logs = result.logs;
		} else {
			RequestTestState test = findTest(status, testId);
			if (test == null) {
				return;
			}
			test.resultStatus = resultStatus;
			test.resultDetails = details;
			test.responseBody = result.responseBody;
			test.responseHeaders = result.responseHeaders;
			test.responseCookies = result.responseCookies;
			test.logs = result.logs;
		}
		stateService.saveRequestStatus(status);
		rebuildTestsTree(status);
	}

	private String resolveTestResultStatus(ExecutionResult result) {
		if (result.logs != null && result.logs.contains("Assertion failed")) {
			return "Failed";
		}
		return result.statusCode >= 200 && result.statusCode < 300 ? "Passed" : "Failed";
	}

	private String formatDuration(long durationMillis) {
		return durationMillis < 0 ? "n/a" : durationMillis + " ms";
	}

	private int responseBodySize(String responseBody) {
		return responseBody == null ? 0 : responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
	}

	private String formatSize(int bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		double kib = bytes / 1024.0;
		if (kib < 1024) {
			return String.format("%.1f KB", kib);
		}
		return String.format("%.1f MB", kib / 1024.0);
	}

	private void runAllTests() {
		if (activeNode == null || activeNode.type != NodeType.REQUEST) {
			return;
		}
		if (activeNode.requestType == RequestType.KAFKA_LISTEN) {
			responseViewer.showLog("Tests run is not available for Kafka listeners.");
			return;
		}
		if (activeExecution != null && !activeExecution.isDone()) {
			return;
		}
		saveActive();
		NodeState node = activeNode;
		RequestDetailsState details = stateService.getRequestDetails(node.id);
		RequestStatusState status = stateService.getRequestStatus(node.id);
		List<TestRunTarget> targets = buildTestRunTargets(status);
		if (targets.isEmpty()) {
			responseViewer.showLog("No enabled tests to run.");
			return;
		}
		responseViewer.clearStatus();
		runRequestInBackground(() -> runTestTargets(node, details, targets));
	}

	private List<TestRunTarget> buildTestRunTargets(RequestStatusState status) {
		List<TestRunTarget> targets = new ArrayList<>();
		if (status == null) {
			return targets;
		}
		targets.add(new TestRunTarget(null, "Base", copyBaseStatus(status, status.requestId)));
		if (status.tests != null) {
			for (RequestTestState test : status.tests) {
				if (test == null || test.disabled) {
					continue;
				}
				targets.add(new TestRunTarget(test.id, safe(test.name), statusViewForTest(status, test)));
			}
		}
		return targets;
	}

	private RequestStatusState statusViewForTest(RequestStatusState base, RequestTestState test) {
		RequestStatusState view = copyBaseStatus(base, base.requestId);
		view.requestBody = test.requestBody;
		view.requestHeaders = cloneHeaders(test.requestHeaders);
		view.requestParams = cloneHeaders(test.requestParams);
		view.formData = cloneFormData(test.formData);
		view.binaryFilePath = test.binaryFilePath;
		view.responseBody = test.responseBody;
		view.responseHeaders = test.responseHeaders;
		view.responseCookies = test.responseCookies;
		view.logs = test.logs;
		view.beforeScript = test.beforeScript;
		view.afterScript = test.afterScript;
		return view;
	}

	private void runTestTargets(
		NodeState node,
		RequestDetailsState details,
		List<TestRunTarget> targets
	) {
		int completed = 0;
		for (TestRunTarget target : targets) {
			if (Thread.currentThread().isInterrupted()) {
				return;
			}
			ExecutionResult result = executeTestTarget(node, details, target.status);
			if (result == null) {
				continue;
			}
			completed++;
			invokeLater(() -> {
				updateTestResult(node.id, target.testId, result);
				if (Objects.equals(activeTestId, target.testId)) {
					responseViewer.updateResponse(result, node.requestType == RequestType.GRPC);
				}
			});
		}
		int total = targets.size();
		int done = completed;
		invokeLater(() -> responseViewer.showLog("Tests completed: " + done + "/" + total + "."));
	}

	private ExecutionResult executeTestTarget(
		NodeState node,
		RequestDetailsState details,
		RequestStatusState status
	) {
		if (node.requestType == RequestType.HTTP) {
			return executeHttpTestTarget(details, status);
		}
		if (node.requestType == RequestType.GRPC) {
			return executeGrpcTestTarget(details, status);
		}
		if (node.requestType == RequestType.KAFKA) {
			return executeKafkaTestTarget(details, status);
		}
		return ExecutionResult.failure(List.of("Unsupported request type for tests: " + node.requestType));
	}

	private ExecutionResult executeHttpTestTarget(RequestDetailsState details, RequestStatusState status) {
		if (details == null || details.url == null || details.url.isBlank()) {
			return ExecutionResult.failure(List.of("Missing URL."));
		}
		HttpExecutionContext context = new HttpExecutionContext(
			details.method == null ? "GET" : details.method,
			details.url,
			status != null ? status.requestHeaders : List.of(),
			status != null ? status.requestParams : List.of(),
			status != null ? status.requestBody : "",
			status != null ? status.beforeScript : "",
			status != null ? status.afterScript : "",
			details.payloadType == null ? "RAW" : details.payloadType,
			status != null ? status.formData : List.of(),
			status != null ? status.binaryFilePath : "",
			requestTimeout(details)
		);
		return executionService.executeWithScripts(
			context.method, context.url, context.headers, context.params, context.body, context.before,
			context.after, false, null, context.payloadType, context.formData, context.binaryFilePath,
			context.timeoutMillis
		);
	}

	private ExecutionResult executeGrpcTestTarget(RequestDetailsState details, RequestStatusState status) {
		if (details == null || details.target == null || details.target.isBlank()) {
			return ExecutionResult.failure(List.of("Missing gRPC target."));
		}
		if (details.service == null || details.service.isBlank() || details.grpcMethod == null ||
			details.grpcMethod.isBlank()) {
			return ExecutionResult.failure(List.of("Missing gRPC service or method."));
		}
		return executionService.executeGrpcWithScripts(
			details,
			status != null ? status.requestHeaders : List.of(),
			status != null ? status.requestParams : List.of(),
			status != null ? status.requestBody : "",
			status != null ? status.beforeScript : "",
			status != null ? status.afterScript : "",
			null,
			requestTimeout(details)
		);
	}

	private ExecutionResult executeKafkaTestTarget(RequestDetailsState details, RequestStatusState status) {
		if (details == null || details.kafkaBootstrapServers == null || details.kafkaBootstrapServers.isBlank()) {
			return ExecutionResult.failure(List.of("Missing Kafka bootstrap servers."));
		}
		if (details.kafkaTopic == null || details.kafkaTopic.isBlank()) {
			return ExecutionResult.failure(List.of("Missing Kafka topic."));
		}
		return executionService.executeKafkaWithScripts(
			details,
			status != null ? status.requestHeaders : List.of(),
			status != null ? status.requestBody : "",
			status != null ? status.beforeScript : "",
			status != null ? status.afterScript : "",
			status != null && status.kafkaKeyType != null ? status.kafkaKeyType : "String",
			status != null && status.kafkaBodyType != null ? status.kafkaBodyType : "JSON",
			status != null ? status.kafkaPartitions : "",
			null,
			requestTimeout(details)
		);
	}

	private record HttpExecutionContext(
		String method,
		String url,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		String payloadType,
		List<FormEntryState> formData,
		String binaryFilePath,
		int timeoutMillis
	) {
	}

	private record TestRunTarget(
		String testId,
		String name,
		RequestStatusState status
	) {
	}

	// ---- execution ----

	public void executeHttp() {
		if (isActiveTestDisabled()) {
			responseViewer.showLog("Selected test is disabled.");
			return;
		}
		HttpExecutionContext context = prepareHttpExecution();
		if (context == null) {
			return;
		}

		responseViewer.clearStatus();
		HttpStressConfig stressConfig = loadStressConfig(stressSettingsPanel.snapshot());
		if (stressConfig.enabled()) {
			if (!stressConfig.hasLimit()) {
				responseViewer.showLog("Stress test requires Total Duration or Number of requests.");
				return;
			}
			runRequestInBackground(() -> {
				ExecutionResult result = httpStressExecutionService.execute(toStressRequest(context), stressConfig);
				if (result != null && !Thread.currentThread().isInterrupted()) {
					invokeLater(() -> updateActiveTestResult(result));
					responseViewer.updateResponse(result, false);
				}
			});
			return;
		}
		runRequestInBackground(() -> {
			ExecutionResult result = executionService.executeWithScripts(
				context.method, context.url, context.headers, context.params, context.body, context.before,
				context.after, false, null, context.payloadType, context.formData, context.binaryFilePath,
				context.timeoutMillis
			);
			if (Thread.currentThread().isInterrupted()) {
				return;
			}
			invokeLater(() -> updateActiveTestResult(result));
			responseViewer.updateResponse(result, false);
		});
	}

	private HttpStressConfig loadStressConfig(RequestStatusState status) {
		if (!stressTestsEnabled || status == null || !status.stressEnabled) {
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

	private HttpStressRequest toStressRequest(HttpExecutionContext context) {
		return new HttpStressRequest(
			context.method,
			context.url,
			context.headers,
			context.params,
			context.body,
			context.before,
			context.after,
			context.payloadType,
			context.formData,
			context.binaryFilePath,
			context.timeoutMillis
		);
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

	private void executeHttpDownload() {
		if (isActiveTestDisabled()) {
			responseViewer.showLog("Selected test is disabled.");
			return;
		}
		HttpExecutionContext context = prepareHttpExecution();
		if (context == null) {
			return;
		}

		responseViewer.clearStatus();
		runRequestInBackground(() -> {
			DownloadResult result = executionService.executeWithScriptsDownload(
				context.method, context.url, context.headers, context.params, context.body, context.before,
				context.after, context.payloadType, context.formData, context.binaryFilePath, context.timeoutMillis
			);
			if (Thread.currentThread().isInterrupted()) {
				return;
			}
			invokeLater(() -> updateActiveTestResult(result.result));
			responseViewer.updateResponse(result.result, false);
			if (result.bodyBytes != null) {
				invokeLater(() -> responseViewer.promptSaveDownload(result, root));
			}
		});
	}

	private HttpExecutionContext prepareHttpExecution() {
		if (activeNode == null || activeNode.requestType != RequestType.HTTP) {
			return null;
		}
		saveActive();
		RequestDetailsState details = stateService.getRequestDetails(activeNode.id);
		if (details == null || details.url == null || details.url.isBlank()) {
			responseViewer.showLog("Missing URL.");
			return null;
		}
		RequestStatusState status = activeStatusView(stateService.getRequestStatus(activeNode.id));
		return new HttpExecutionContext(
			details.method == null ? "GET" : details.method,
			details.url,
			status != null ? status.requestHeaders : List.of(),
			status != null ? status.requestParams : List.of(),
			status != null ? status.requestBody : "",
			status != null ? status.beforeScript : "",
			status != null ? status.afterScript : "",
			details.payloadType == null ? "RAW" : details.payloadType,
			status != null ? status.formData : List.of(),
			status != null ? status.binaryFilePath : "",
			requestTimeout(details)
		);
	}

	public void executeGrpc() {
		if (activeNode == null || activeNode.requestType != RequestType.GRPC) {
			return;
		}
		if (isActiveTestDisabled()) {
			responseViewer.showLog("Selected test is disabled.");
			return;
		}
		if (activeGrpcClientStream != null) {
			sendGrpcClientStreamMessage();
			return;
		}
		saveActive();
		RequestDetailsState details = stateService.getRequestDetails(activeNode.id);
		if (details == null || details.target == null || details.target.isBlank()) {
			responseViewer.showLog("Missing gRPC target.");
			return;
		}
		if (details.service == null || details.service.isBlank() || details.grpcMethod == null ||
			details.grpcMethod.isBlank()) {
			responseViewer.showLog("Missing gRPC service or method.");
			return;
		}
		RequestStatusState status = activeStatusView(stateService.getRequestStatus(activeNode.id));
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();
		List<HeaderEntryState> params = status != null ? status.requestParams : List.of();
		String body = status != null ? status.requestBody : "";
		String before = status != null ? status.beforeScript : "";
		String after = status != null ? status.afterScript : "";

		responseViewer.clearStatus();
		runRequestInBackground(() -> {
			Map<String, Object> varsSnapshot = globalTemplateVars();
			String target = templateEngine.applyToText(details.target, varsSnapshot);
			String service = templateEngine.applyToText(details.service, varsSnapshot);
			String method = templateEngine.applyToText(details.grpcMethod, varsSnapshot);
			if (grpcExecutor.isBidirectionalStreaming(target, service, method)) {
				List<HeaderEntryState> templatedHeaders = templateEngine.applyToHeaders(headers, varsSnapshot);
				String templatedBody = templateEngine.applyToBody(body, varsSnapshot);
				GrpcExecutor.GrpcStreamingCall stream = grpcExecutor.openBidirectionalStreaming(
					target,
					service,
					method,
					templatedHeaders,
					requestTimeout(details),
					message -> invokeLater(() -> responseViewer.showLog("gRPC bidi stream message received: " + message.body))
				);
				try {
					stream.send(templatedBody);
				} catch (Exception error) {
					stream.cancel();
					throw new IllegalArgumentException(error.getMessage(), error);
				}
				invokeLater(() -> {
					if (activeNode == null || !Objects.equals(activeNode.id, details.requestId)) {
						stream.cancel();
						return;
					}
					activeGrpcClientStream = stream;
					activeGrpcClientStreamRequestId = details.requestId;
					updateExecutionButtons(false);
					grpcStopButton.setEnabled(true);
					responseViewer.showLog("gRPC bidirectional stream opened. Message sent. Press Send to send another message, Stop to finish.");
				});
				return;
			}
			if (grpcExecutor.isClientStreaming(target, service, method)) {
				List<HeaderEntryState> templatedHeaders = templateEngine.applyToHeaders(headers, varsSnapshot);
				String templatedBody = templateEngine.applyToBody(body, varsSnapshot);
				GrpcExecutor.GrpcStreamingCall stream = grpcExecutor.openClientStreaming(
					target,
					service,
					method,
					templatedHeaders,
					requestTimeout(details)
				);
				try {
					stream.send(templatedBody);
				} catch (Exception error) {
					stream.cancel();
					throw new IllegalArgumentException(error.getMessage(), error);
				}
				invokeLater(() -> {
					if (activeNode == null || !Objects.equals(activeNode.id, details.requestId)) {
						stream.cancel();
						return;
					}
					activeGrpcClientStream = stream;
					activeGrpcClientStreamRequestId = details.requestId;
					updateExecutionButtons(false);
					grpcStopButton.setEnabled(true);
					responseViewer.showLog("gRPC client stream opened. Message sent. Press Send to send another message, Stop to finish.");
				});
				return;
			}
			ExecutionResult result =
				executionService.executeGrpcWithScripts(details, headers, params, body, before, after, null, requestTimeout(details));
			if (Thread.currentThread().isInterrupted()) {
				return;
			}
			invokeLater(() -> updateActiveTestResult(result));
			responseViewer.updateResponse(result, true);
		});
	}

	private void sendGrpcClientStreamMessage() {
		if (activeNode == null || activeGrpcClientStream == null) {
			return;
		}
		if (!Objects.equals(activeNode.id, activeGrpcClientStreamRequestId)) {
			responseViewer.showLog("Stop the active gRPC client stream before switching requests.");
			return;
		}
		saveActive();
		RequestStatusState status = activeStatusView(stateService.getRequestStatus(activeNode.id));
		String body = status == null ? "" : status.requestBody;
		grpcSendButton.setEnabled(false);
		activeExecution = ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try {
				String templatedBody = templateEngine.applyToBody(body, globalTemplateVars());
				activeGrpcClientStream.send(templatedBody);
				invokeLater(() -> responseViewer.showLog("gRPC client stream message sent."));
			} catch (Exception error) {
				invokeLater(() -> responseViewer.showLog("gRPC client stream send failed: " + error.getMessage()));
			} finally {
				invokeLater(() -> {
					activeExecution = null;
					grpcSendButton.setEnabled(activeGrpcClientStream != null);
					grpcStopButton.setEnabled(activeGrpcClientStream != null);
				});
			}
		});
	}

	private void stopGrpcClientStream() {
		GrpcExecutor.GrpcStreamingCall stream = activeGrpcClientStream;
		if (stream == null) {
			return;
		}
		activeGrpcClientStream = null;
		activeGrpcClientStreamRequestId = null;
		grpcSendButton.setEnabled(false);
		grpcStopButton.setEnabled(false);
		activeExecution = ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try {
				GrpcExecutionResponse response = stream.complete();
				ExecutionResult result = new ExecutionResult(
					response.statusCode,
					response.statusMessage,
					JsonUtils.prettyPrint(response.body),
					JsonUtils.toJson(response.headers),
					"",
					"gRPC client stream completed."
				);
				invokeLater(() -> responseViewer.updateResponse(result, true));
			} catch (Exception error) {
				invokeLater(() -> responseViewer.showLog("gRPC client stream stop failed: " + error.getMessage()));
			} finally {
				invokeLater(() -> {
					activeExecution = null;
					updateExecutionButtons(false);
				});
			}
		});
	}

	public void executeKafka() {
		if (activeNode == null || activeNode.requestType != RequestType.KAFKA) {
			return;
		}
		if (isActiveTestDisabled()) {
			responseViewer.showLog("Selected test is disabled.");
			return;
		}
		saveActive();
		RequestDetailsState details = stateService.getRequestDetails(activeNode.id);
		if (details == null || details.kafkaBootstrapServers == null || details.kafkaBootstrapServers.isBlank()) {
			responseViewer.showLog("Missing Kafka bootstrap servers.");
			return;
		}
		if (details.kafkaTopic == null || details.kafkaTopic.isBlank()) {
			responseViewer.showLog("Missing Kafka topic.");
			return;
		}
		RequestStatusState status = activeStatusView(stateService.getRequestStatus(activeNode.id));
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();
		String body = status != null ? status.requestBody : "";
		String before = status != null ? status.beforeScript : "";
		String after = status != null ? status.afterScript : "";
		String keyType = status != null && status.kafkaKeyType != null ? status.kafkaKeyType : "String";
		String bodyType = status != null && status.kafkaBodyType != null ? status.kafkaBodyType : "JSON";
		String partition = status != null ? status.kafkaPartitions : "";

		responseViewer.clearStatus();
		runRequestInBackground(() -> {
			ExecutionResult result = executionService.executeKafkaWithScripts(
				details,
				headers,
				body,
				before,
				after,
				keyType,
				bodyType,
				partition,
				null,
				requestTimeout(details)
			);
			if (Thread.currentThread().isInterrupted()) {
				return;
			}
			invokeLater(() -> updateActiveTestResult(result));
			responseViewer.updateResponse(result, false);
		});
	}

	public void toggleKafkaListeningFromShortcut() {
		toggleKafkaListening();
	}

	private void refreshKafkaMetadata() {
		if (activeNode == null || activeNode.requestType != RequestType.KAFKA) {
			return;
		}
		saveActive();
		String requestId = activeNode.id;
		String bootstrapServers = comboEditorText(kafkaBootstrapCombo);
		if (bootstrapServers.isBlank()) {
			responseViewer.showLog("Missing Kafka bootstrap servers.");
			return;
		}
		responseViewer.showLog("Loading Kafka topics...");
		runInBackground(() -> {
			try {
				Map<String, Object> varsSnapshot = globalTemplateVars();
				List<String> topics = kafkaMetadataService.listTopics(
					templateEngine.applyToText(bootstrapServers, varsSnapshot)
				);
				invokeLater(() -> applyKafkaTopics(requestId, topics));
			} catch (Exception error) {
				invokeLater(() -> responseViewer.showLog(error.getMessage()));
			}
		});
	}

	private void applyKafkaTopics(
		String requestId,
		List<String> topics
	) {
		if (activeNode == null || !Objects.equals(activeNode.id, requestId)) {
			return;
		}
		String selectedTopic = comboEditorText(kafkaTopicCombo);
		isKafkaReloading = true;
		try {
			kafkaTopicCombo.removeAllItems();
			for (String topic : topics) {
				kafkaTopicCombo.addItem(topic);
			}
			if (!selectedTopic.isBlank()) {
				setComboEditorText(kafkaTopicCombo, selectedTopic);
			} else if (!topics.isEmpty()) {
				kafkaTopicCombo.setSelectedItem(topics.get(0));
			}
		} finally {
			isKafkaReloading = false;
		}
		saveActive();
		responseViewer.showLog(topics.isEmpty() ? "No Kafka topics found." : "Loaded Kafka topics: " + topics.size());
	}

	private void refreshKafkaListenMetadata() {
		if (activeNode == null || activeNode.requestType != RequestType.KAFKA_LISTEN) {
			return;
		}
		saveActive();
		String requestId = activeNode.id;
		String bootstrapServers = comboEditorText(kafkaListenBootstrapCombo);
		if (bootstrapServers.isBlank()) {
			responseViewer.showLog("Missing Kafka bootstrap servers.");
			return;
		}
		responseViewer.showLog("Loading Kafka topics...");
		runInBackground(() -> {
			try {
				Map<String, Object> varsSnapshot = globalTemplateVars();
				List<String> topics = kafkaMetadataService.listTopics(
					templateEngine.applyToText(bootstrapServers, varsSnapshot)
				);
				invokeLater(() -> applyKafkaListenTopics(requestId, topics));
			} catch (Exception error) {
				invokeLater(() -> responseViewer.showLog(error.getMessage()));
			}
		});
	}

	private void applyKafkaListenTopics(
		String requestId,
		List<String> topics
	) {
		if (activeNode == null || !Objects.equals(activeNode.id, requestId)) {
			return;
		}
		String selectedTopic = comboEditorText(kafkaListenTopicCombo);
		isKafkaReloading = true;
		try {
			kafkaListenTopicCombo.removeAllItems();
			for (String topic : topics) {
				kafkaListenTopicCombo.addItem(topic);
			}
			if (!selectedTopic.isBlank()) {
				setComboEditorText(kafkaListenTopicCombo, selectedTopic);
			} else if (!topics.isEmpty()) {
				kafkaListenTopicCombo.setSelectedItem(topics.get(0));
			}
		} finally {
			isKafkaReloading = false;
		}
		saveActive();
		responseViewer.showLog(topics.isEmpty() ? "No Kafka topics found." : "Loaded Kafka topics: " + topics.size());
	}

	private void toggleKafkaListening() {
		if (activeNode == null || activeNode.requestType != RequestType.KAFKA_LISTEN) {
			return;
		}
		if (isKafkaListening(activeNode.id)) {
			stopKafkaListening();
			return;
		}
		startKafkaListening();
	}

	private void startKafkaListening() {
		if (activeNode == null || activeNode.requestType != RequestType.KAFKA_LISTEN) {
			return;
		}
		saveActive();
		RequestDetailsState details = stateService.getRequestDetails(activeNode.id);
		RequestStatusState status = stateService.getRequestStatus(activeNode.id);
		if (details == null || details.kafkaBootstrapServers == null || details.kafkaBootstrapServers.isBlank()) {
			responseViewer.showLog("Missing Kafka bootstrap servers.");
			return;
		}
		if (details.kafkaTopic == null || details.kafkaTopic.isBlank()) {
			responseViewer.showLog("Missing Kafka topic.");
			return;
		}
		if (details.kafkaGroupId == null || details.kafkaGroupId.isBlank()) {
			responseViewer.showLog("Missing Kafka group id.");
			return;
		}

		String requestId = activeNode.id;
		KafkaListenRequest request = new KafkaListenRequest();
		try {
			Map<String, Object> varsSnapshot = globalTemplateVars();
			request.bootstrapServers = templateEngine.applyToText(details.kafkaBootstrapServers, varsSnapshot);
			request.topic = templateEngine.applyToText(details.kafkaTopic, varsSnapshot);
			request.groupId = templateEngine.applyToText(details.kafkaGroupId, varsSnapshot);
		} catch (Exception error) {
			responseViewer.showLog("Global context error: " + error.getMessage());
			return;
		}
		request.offsetStrategy =
			status != null && status.kafkaOffsetStrategy != null ? status.kafkaOffsetStrategy : "Latest";
		List<KafkaListenMessage> existingMessages = loadKafkaListenMessages(requestId);
		kafkaListenMessagesByRequest.put(requestId, existingMessages);
		kafkaListenVarsByRequest.put(requestId, new VarsStore());
		kafkaListenButton.setText("Stop Listening");
		updateKafkaListenResponse(
			requestId,
			toPrettyJson(existingMessages),
			"Listening for Kafka messages... Received: " + existingMessages.size()
		);
		try {
			kafkaListenerService.start(
				requestId,
				request,
				message -> appendKafkaListenMessage(requestId, message),
				error -> invokeLater(() -> handleKafkaListenError(requestId, error))
			);
		} catch (Exception error) {
			handleKafkaListenError(requestId, error);
		}
	}

	private void appendKafkaListenMessage(
		String requestId,
		KafkaListenMessage message
	) {
		List<String> scriptLogs = runKafkaListenOnMessageScript(requestId, message);
		List<KafkaListenMessage> messages =
			kafkaListenMessagesByRequest.computeIfAbsent(requestId, this::loadKafkaListenMessages);
		messages.add(0, message);
		String logs = buildKafkaListenLogs(requestId, scriptLogs, messages.size());
		updateKafkaListenResponse(
			requestId,
			toPrettyJson(messages),
			logs
		);
	}

	private List<String> runKafkaListenOnMessageScript(
		String requestId,
		KafkaListenMessage message
	) {
		RequestStatusState status = stateService.getRequestStatus(requestId);
		String script = status == null ? "" : safe(status.beforeScript);
		if (script.isBlank()) {
			return List.of();
		}
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = logs::add;
		ScriptHelpers helpers = new ScriptHelpers(logger);
		GlobalContextRuntime globalContextRuntime = new GlobalContextRuntime(stateService, scriptRuntime);
		VarsStore globalContext = globalContextRuntime.loadAndRun(logger);
		VarsStore vars = kafkaListenVarsByRequest.computeIfAbsent(requestId, key -> new VarsStore());
		ScriptRequest rawRequest = kafkaListenMessageRequest(message);
		ScriptRequest scriptRequest = kafkaListenMessageRequest(message);
		try {
			scriptRuntime.runScript(
				script,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, message, globalContext)
			);
			message.body = scriptRequest.getBody();
			message.headers = toKafkaListenHeaders(scriptRequest.getHeaders());
		} catch (Exception error) {
			logs.add("On Message error: " + error.getMessage());
		}
		globalContextRuntime.persist(globalContext);
		return logs;
	}

	private ScriptRequest kafkaListenMessageRequest(KafkaListenMessage message) {
		return new ScriptRequest(
			message == null ? "" : safe(message.body),
			message == null ? List.of() : toHeaderEntries(message.headers),
			List.of()
		);
	}

	private List<HeaderEntryState> toHeaderEntries(List<KafkaListenMessage.Header> headers) {
		List<HeaderEntryState> entries = new ArrayList<>();
		if (headers == null) {
			return entries;
		}
		for (KafkaListenMessage.Header header : headers) {
			HeaderEntryState entry = new HeaderEntryState();
			entry.name = header == null ? "" : safe(header.name);
			entry.value = header == null ? "" : safe(header.value);
			entry.enabled = true;
			entries.add(entry);
		}
		return entries;
	}

	private List<KafkaListenMessage.Header> toKafkaListenHeaders(List<HeaderEntryState> headers) {
		List<KafkaListenMessage.Header> entries = new ArrayList<>();
		if (headers == null) {
			return entries;
		}
		for (HeaderEntryState header : headers) {
			if (header == null || !header.enabled) {
				continue;
			}
			entries.add(new KafkaListenMessage.Header(safe(header.name), safe(header.value)));
		}
		return entries;
	}

	private String buildKafkaListenLogs(
		String requestId,
		List<String> scriptLogs,
		int receivedCount
	) {
		RequestStatusState status = stateService.getRequestStatus(requestId);
		List<String> lines = new ArrayList<>();
		String existing = status == null ? "" : safe(status.logs);
		if (!existing.isBlank()) {
			lines.add(existing);
		}
		if (scriptLogs != null) {
			lines.addAll(scriptLogs);
		}
		lines.add("Listening for Kafka messages... Received: " + receivedCount);
		return String.join("\n", lines);
	}

	private void handleKafkaListenError(
		String requestId,
		Throwable error
	) {
		kafkaListenVarsByRequest.remove(requestId);
		if (activeNode != null && Objects.equals(activeNode.id, requestId)) {
			kafkaListenButton.setText("Start Listening");
		}
		appendKafkaListenLog(requestId, "Kafka listen failed: " + error.getMessage());
	}

	private void stopKafkaListening() {
		if (activeNode == null || activeNode.requestType != RequestType.KAFKA_LISTEN) {
			kafkaListenButton.setText("Start Listening");
			return;
		}
		String requestId = activeNode.id;
		kafkaListenerService.stop(requestId);
		kafkaListenVarsByRequest.remove(requestId);
		kafkaListenButton.setText("Start Listening");
		appendKafkaListenLog(requestId, "Kafka listening stopped.");
	}

	private boolean isKafkaListening(String requestId) {
		return requestId != null && kafkaListenerService.isListening(requestId);
	}

	private void updateKafkaListenResponse(
		String requestId,
		String body,
		String logs
	) {
		ExecutionResult result = new ExecutionResult(200, "Listening", body, "{}", logs);
		if (activeNode != null && Objects.equals(activeNode.id, requestId)) {
			responseViewer.updateResponse(result, false);
			return;
		}
		saveKafkaListenResponse(requestId, result);
	}

	private void appendKafkaListenLog(
		String requestId,
		String message
	) {
		RequestStatusState status = stateService.getRequestStatus(requestId);
		String existing = status == null ? "" : safe(status.logs);
		String logs = existing.isBlank() ? message : existing + "\n" + message;
		String body = status == null ? "" : safe(status.responseBody);
		updateKafkaListenResponse(requestId, body, logs);
	}

	private void saveKafkaListenResponse(
		String requestId,
		ExecutionResult result
	) {
		RequestStatusState status = stateService.getRequestStatus(requestId);
		if (status == null) {
			status = new RequestStatusState();
			status.requestId = requestId;
		}
		status.responseBody = result.responseBody;
		status.responseHeaders = result.responseHeaders;
		status.logs = result.logs;
		stateService.saveRequestStatus(status);
	}

	private List<KafkaListenMessage> loadKafkaListenMessages(String requestId) {
		RequestStatusState status = stateService.getRequestStatus(requestId);
		String body = status == null ? "" : safe(status.responseBody);
		if (body.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return new ArrayList<>(mapper.readValue(body, new TypeReference<List<KafkaListenMessage>>() {}));
		} catch (Exception ignored) {
			return new ArrayList<>();
		}
	}

	private void reloadGrpcServices() {
		if (activeNode == null || activeNode.requestType != RequestType.GRPC) {
			return;
		}
		String requestId = activeNode.id;
		String target = grpcTargetField.getText();
		if (target == null || target.isBlank()) {
			return;
		}
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		String desiredService =
			details != null && details.service != null ? details.service : grpcServiceSelection.get(requestId);
		String desiredMethod = details != null ? details.grpcMethod : null;
		runInBackground(() -> {
			try {
				Map<String, Object> varsSnapshot = globalTemplateVars();
				List<GrpcServiceInfo> services = grpcExecutor.listServices(
					templateEngine.applyToText(target, varsSnapshot)
				);
				grpcServicesCache.put(requestId, services);
				invokeLater(() -> {
					if (activeNode == null || !Objects.equals(activeNode.id, requestId)) {
						return;
					}
					isGrpcReloading = true;
					grpcServiceCombo.removeAllItems();
					grpcMethodCombo.removeAllItems();
					for (GrpcServiceInfo info : services) {
						grpcServiceCombo.addItem(info.name);
					}
					grpcServiceCombo.setPrototypeDisplayValue(longestServiceName(services));
					if (desiredService != null && !desiredService.isBlank()) {
						grpcServiceCombo.setSelectedItem(desiredService);
					}
					updateGrpcMethods(desiredMethod);
					isGrpcReloading = false;
					saveActive();
					if (services.isEmpty()) {
						responseViewer.showLog("No gRPC services found.");
					}
				});
			} catch (Exception error) {
				invokeLater(() -> responseViewer.showLog("gRPC reload failed: " + error.getMessage()));
			}
		});
	}

	private void updateGrpcMethods(String desiredMethod) {
		if (activeNode == null) {
			return;
		}
		List<GrpcServiceInfo> services = grpcServicesCache.getOrDefault(activeNode.id, List.of());
		String selectedService =
			grpcServiceCombo.getSelectedItem() == null ? "" : String.valueOf(grpcServiceCombo.getSelectedItem());
		grpcMethodCombo.removeAllItems();
		for (GrpcServiceInfo info : services) {
			if (Objects.equals(info.name, selectedService)) {
				for (String method : info.methods) {
					grpcMethodCombo.addItem(method);
				}
				break;
			}
		}
		if (desiredMethod != null && !desiredMethod.isBlank()) {
			grpcMethodCombo.setSelectedItem(desiredMethod);
		}
		grpcMethodCombo.setPrototypeDisplayValue(longestMethodName(services, selectedService));
	}

	private String resolveSelectedGrpcStreamingKind(String requestId, String service, String method) {
		if (requestId == null || service == null || method == null) {
			return "UNARY";
		}
		for (GrpcServiceInfo info : grpcServicesCache.getOrDefault(requestId, List.of())) {
			if (Objects.equals(info.name, service) && info.methodStreamingKinds != null) {
				return info.methodStreamingKinds.getOrDefault(method, "UNARY");
			}
		}
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		return details == null || details.grpcStreamingKind == null || details.grpcStreamingKind.isBlank()
			? "UNARY"
			: details.grpcStreamingKind;
	}

	private void startDebugCall() {
		if (!hasEditableRequest()) {
			return;
		}
		saveActive();
		if (debugCallSession != null) {
			debugCallSession.abandon(true);
		}
		debugCallSession = new DebugCallSession(
			project,
			root,
			stateService,
			scriptRuntime,
			templateEngine,
			httpExecutor,
			grpcExecutor,
			activeNode.id,
			activeNode.requestType
		);
		debugCallSession.open();
	}

	// ---- auto-save / sync ----

	private void attachAutoSaveListeners() {
		httpUrlField.getDocument().addDocumentListener(new AutoSaveListener());
		httpUrlField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				scheduleParamsSyncFromUrl();
			}
		});
		grpcTargetField.getDocument().addDocumentListener(new AutoSaveListener());
		addEditableComboAutoSave(kafkaBootstrapCombo);
		addEditableComboAutoSave(kafkaTopicCombo);
		kafkaKeyField.getDocument().addDocumentListener(new AutoSaveListener());
		addEditableComboAutoSave(kafkaListenBootstrapCombo);
		addEditableComboAutoSave(kafkaListenTopicCombo);
		kafkaGroupIdField.getDocument().addDocumentListener(new AutoSaveListener());
		kafkaPartitionsField.getDocument().addDocumentListener(new AutoSaveListener());
		stressSettingsPanel.addAutoSaveListeners(new AutoSaveListener(), e -> saveActive());
		requestBodyArea.addDocumentListener(new EditorAutoSaveListener());
		beforeScriptArea.addDocumentListener(new EditorAutoSaveListener());
		afterScriptArea.addDocumentListener(new EditorAutoSaveListener());
		headersTableModel.addTableModelListener((TableModelEvent e) -> saveActive());
		paramsTableModel.addTableModelListener((TableModelEvent e) -> {
			syncUrlFromParamsTable();
			saveActive();
		});
		formDataTableModel.addTableModelListener((TableModelEvent e) -> saveActive());
		binaryFileField.getDocument().addDocumentListener(new AutoSaveListener());
		httpMethodCombo.addActionListener(e -> saveActive());
		httpPayloadCombo.addActionListener(e -> {
			switchPayloadType();
			if (!isLoading) {
				saveActive();
			}
		});
		grpcServiceCombo.addActionListener(e -> {
			if (!isLoading && !isGrpcReloading) {
				if (activeNode != null) {
					Object selected = grpcServiceCombo.getSelectedItem();
					grpcServiceSelection.put(activeNode.id, selected == null ? "" : String.valueOf(selected));
				}
				updateGrpcMethods(null);
				saveActive();
			}
		});
		grpcMethodCombo.addActionListener(e -> {
			if (!isLoading && !isGrpcReloading) {
				saveActive();
			}
		});
		kafkaBootstrapCombo.addActionListener(e -> {
			if (!isKafkaReloading) {
				saveActive();
			}
		});
		kafkaTopicCombo.addActionListener(e -> {
			if (!isKafkaReloading) {
				saveActive();
			}
		});
		kafkaListenBootstrapCombo.addActionListener(e -> {
			if (!isKafkaReloading) {
				saveActive();
			}
		});
		kafkaListenTopicCombo.addActionListener(e -> {
			if (!isKafkaReloading) {
				saveActive();
			}
		});
		kafkaKeyTypeCombo.addActionListener(e -> saveActive());
		kafkaBodyTypeCombo.addActionListener(e -> saveActive());
		kafkaOffsetStrategyCombo.addActionListener(e -> saveActive());
		httpTimeoutSpinner.addChangeListener(e -> saveActive());
		grpcTimeoutSpinner.addChangeListener(e -> saveActive());
		kafkaTimeoutSpinner.addChangeListener(e -> saveActive());
	}

	private void addEditableComboAutoSave(JComboBox<String> combo) {
		if (combo.getEditor().getEditorComponent() instanceof javax.swing.text.JTextComponent textComponent) {
			textComponent.getDocument().addDocumentListener(new AutoSaveListener());
		}
	}

	private void syncUrlFromParamsTable() {
		if (isLoading || isSyncingParamsFromUrl || activeNode == null || activeNode.requestType != RequestType.HTTP
			|| activeTestId != null) {
			return;
		}
		String currentUrl = httpUrlField.getText();
		String updatedUrl = UrlParamUtils.replaceQueryParams(currentUrl, paramsTableModel.getHeaders());
		if (!Objects.equals(currentUrl, updatedUrl)) {
			httpUrlField.setText(updatedUrl);
		}
	}

	private void syncParamsFromUrlField() {
		if (isLoading || isSyncingParamsFromUrl || activeNode == null || activeNode.requestType != RequestType.HTTP) {
			return;
		}
		List<HeaderEntryState> merged = UrlParamUtils.mergeParamsWithUrl(paramsTableModel.getHeaders(), httpUrlField.getText());
		if (merged == null) {
			return;
		}
		isSyncingParamsFromUrl = true;
		try {
			paramsTableModel.setHeaders(merged, true);
		} finally {
			isSyncingParamsFromUrl = false;
		}
		saveActive();
	}

	private void scheduleParamsSyncFromUrl() {
		if (isLoading || isSyncingParamsFromUrl) {
			return;
		}
		urlParamSyncTimer.restart();
	}

	// ---- format ----

	public void formatEditors() {
		WriteCommandAction.runWriteCommandAction(project, () -> {
			formatJsonField(requestBodyArea);
			formatJsonField(responseViewer.getBodyField());
			formatJsonField(responseViewer.getHeadersField());
			formatScriptField(beforeScriptArea);
			formatScriptField(afterScriptArea);
		});
	}

	private void formatJsonField(EditorTextField field) {
		String text = field.getText();
		if (text == null || text.isBlank()) {
			return;
		}
		try {
			PsiFile psiFile = PsiFileFactory.getInstance(project)
				.createFileFromText("payload.json", JsonFileType.INSTANCE, text, System.currentTimeMillis(), true);
			CodeStyleManager.getInstance(project)
				.reformatText(psiFile, List.of(new TextRange(0, psiFile.getTextLength())));
			field.setText(psiFile.getText());
			return;
		} catch (Exception ignored) {
		}
		try {
			Object parsed = mapper.readValue(text, Object.class);
			String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
			field.setText(pretty);
		} catch (Exception ignored) {
		}
	}

	private void formatScriptField(EditorTextField field) {
		if (scriptFileType == PlainTextFileType.INSTANCE) {
			return;
		}
		String text = field.getText();
		if (text == null || text.isBlank()) {
			return;
		}
		PsiFile psiFile = PsiFileFactory.getInstance(project)
			.createFileFromText("script.js", scriptFileType, text, System.currentTimeMillis(), true);
		CodeStyleManager.getInstance(project).reformatText(psiFile, List.of(new TextRange(0, psiFile.getTextLength())));
		field.setText(psiFile.getText());
	}

	// ---- body generation ----

	private void generateBodyFromProto() {
		if (!hasEditableRequest()) {
			return;
		}
		bodyGenerator.generateFromProto();
	}

	// ---- open in window ----

	private void openRequestWindow() {
		if (!hasEditableRequest()) {
			return;
		}
		JTabbedPane tabs = new JTabbedPane();

		EditorTextField bodyField = new JsonBodyEditorField(project, requestBodyArea.getDocument());
		EditorTextField beforeField = createScriptMirrorEditor(beforeScriptArea);
		EditorTextField afterField = createScriptMirrorEditor(afterScriptArea);

		tabs.add("Body", new JBScrollPane(bodyField));
		tabs.add("Before Request", new JBScrollPane(beforeField));
		tabs.add("After Request", new JBScrollPane(afterField));

		showEditorDialog("Request Editor - " + activeNode.name, tabs);
	}

	private void openBeforeRequestWindow() {
		if (!hasEditableRequest()) {
			return;
		}
		showEditorDialog(
			"Before Request - " + activeNode.name,
			new JBScrollPane(createScriptMirrorEditor(beforeScriptArea))
		);
	}

	private void openAfterRequestWindow() {
		if (!hasEditableRequest()) {
			return;
		}
		showEditorDialog(
			"After Request - " + activeNode.name,
			new JBScrollPane(createScriptMirrorEditor(afterScriptArea))
		);
	}

	private EditorTextField createScriptMirrorEditor(EditorTextField source) {
		EditorTextField editor =
			EditorThemeSupport.configure(new EditorTextField(source.getDocument(), project, scriptFileType, false, false));
		editor.setOneLineMode(false);
		return editor;
	}

	private void showEditorDialog(String title, JComponent content) {
		JFrame dialog = TaskbarWindowSupport.createFrame(title, root);
		dialog.getContentPane().add(content);
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(root);
		dialog.setVisible(true);
	}

	private void openResponseWindow() {
		if (!hasEditableRequest()) {
			return;
		}
		responseViewer.openInWindow("Response Viewer - " + activeNode.name, root);
	}

	// ---- helpers ----

	private void runInBackground(Runnable runnable) {
		ApplicationManager.getApplication().executeOnPooledThread(runnable);
	}

	private void runRequestInBackground(Runnable runnable) {
		if (activeExecution != null && !activeExecution.isDone()) {
			return;
		}
		updateExecutionButtons(true);
		responseViewer.startElapsedTimer();
		activeExecution = ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try {
				runnable.run();
			} finally {
				invokeLater(() -> {
					responseViewer.stopElapsedTimer();
					activeExecution = null;
					updateExecutionButtons(false);
				});
			}
		});
	}

	private void stopActiveExecution() {
		if (activeGrpcClientStream != null) {
			stopGrpcClientStream();
			return;
		}
		if (activeExecution == null || activeExecution.isDone()) {
			updateExecutionButtons(false);
			return;
		}
		activeExecution.cancel(true);
		responseViewer.showLog("Request stopped.");
		responseViewer.stopElapsedTimer();
		updateExecutionButtons(false);
	}

	private void updateExecutionButtons(boolean running) {
		httpSendButton.setEnabled(!running);
		httpSendDownloadButton.setEnabled(!running);
		grpcSendButton.setEnabled(!running || activeGrpcClientStream != null);
		kafkaSendButton.setEnabled(!running);
		httpStopButton.setEnabled(running);
		grpcStopButton.setEnabled(running || activeGrpcClientStream != null);
		kafkaStopButton.setEnabled(running);
	}

	private void invokeLater(Runnable runnable) {
		ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
	}

	private boolean hasEditableRequest() {
		return activeNode != null && activeNode.type == NodeType.REQUEST && activeNode.requestType != RequestType.CHAIN;
	}

	private FileType resolveScriptFileType() {
		FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension("js");
		if (fileType == null || fileType == PlainTextFileType.INSTANCE) {
			fileType = PlainTextFileType.INSTANCE;
		}
		return fileType;
	}

	private EditorTextField createScriptEditor() {
		return EditorThemeSupport.configure(new EditorTextField("", project, scriptFileType));
	}

	private String comboEditorText(JComboBox<String> combo) {
		Object item = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
		return item == null ? "" : String.valueOf(item);
	}

	private void setComboEditorText(JComboBox<String> combo, String value) {
		if (combo.isEditable()) {
			combo.getEditor().setItem(value == null ? "" : value);
		} else {
			combo.setSelectedItem(value);
		}
	}

	private Map<String, Object> globalTemplateVars() {
		GlobalContextRuntime runtime = new GlobalContextRuntime(stateService, scriptRuntime);
		VarsStore globalContext = runtime.loadAndRun(message -> {
		});
		return runtime.mergeForTemplates(globalContext, new VarsStore());
	}

	private String toPrettyJson(Object value) {
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
		} catch (Exception error) {
			return String.valueOf(value);
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private final class AutoSaveListener implements javax.swing.event.DocumentListener {

		@Override
		public void insertUpdate(javax.swing.event.DocumentEvent e) {
			saveActive();
		}

		@Override
		public void removeUpdate(javax.swing.event.DocumentEvent e) {
			saveActive();
		}

		@Override
		public void changedUpdate(javax.swing.event.DocumentEvent e) {
			saveActive();
		}
	}

	private final class EditorAutoSaveListener implements DocumentListener {

		@Override
		public void documentChanged(DocumentEvent event) {
			saveActive();
		}
	}

	private final class GrpcServiceCellRenderer extends DefaultListCellRenderer {
		@Override
		public Component getListCellRendererComponent(
			JList<?> list,
			Object value,
			int index,
			boolean isSelected,
			boolean cellHasFocus
		) {
			Component component = super.getListCellRendererComponent(
				list,
				shortGrpcServiceName(value == null ? "" : String.valueOf(value)),
				index,
				isSelected,
				cellHasFocus
			);
			String fullName = value == null ? "" : String.valueOf(value);
			if (component instanceof JComponent jComponent) {
				jComponent.setToolTipText(fullName);
			}
			list.setToolTipText(fullName);
			return component;
		}
	}
}
