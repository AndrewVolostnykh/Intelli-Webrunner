package com.intelli.webrunner.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.intelli.webrunner.debug.DebugCallSession;
import com.intelli.webrunner.execution.DownloadResult;
import com.intelli.webrunner.execution.ExecutionResult;
import com.intelli.webrunner.execution.HttpExecutor;
import com.intelli.webrunner.execution.RequestExecutionService;
import com.intelli.webrunner.grpc.GrpcExecutor;
import com.intelli.webrunner.grpc.GrpcServiceInfo;
import com.intelli.webrunner.kafka.KafkaListenMessage;
import com.intelli.webrunner.kafka.KafkaListenRequest;
import com.intelli.webrunner.kafka.KafkaListenerService;
import com.intelli.webrunner.kafka.KafkaMetadataService;
import com.intelli.webrunner.script.ScriptRuntime;
import com.intelli.webrunner.state.FormEntryState;
import com.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.intelli.webrunner.state.HeaderEntryState;
import com.intelli.webrunner.state.HeaderPresetState;
import com.intelli.webrunner.state.NodeState;
import com.intelli.webrunner.state.NodeType;
import com.intelli.webrunner.state.RequestDetailsState;
import com.intelli.webrunner.state.RequestStatusState;
import com.intelli.webrunner.state.RequestType;
import com.intelli.webrunner.util.CurlCommandBuilder;
import com.intelli.webrunner.util.PayloadTypes;
import com.intelli.webrunner.util.TemplateEngine;
import com.intelli.webrunner.util.UrlParamUtils;
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
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
	private final ObjectMapper mapper = new ObjectMapper();

	private final JPanel root = new JPanel(new BorderLayout());
	private static final Dimension ICON_BUTTON_SIZE = new Dimension(28, 28);

	private NodeState activeNode;
	private boolean isLoading = false;
	private boolean isStoppingTableEditing = false;
	private boolean isSyncingParamsFromUrl = false;
	private final javax.swing.Timer urlParamSyncTimer;

	private final JComboBox<String> httpMethodCombo =
		new JComboBox<>(new String[] {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"});
	private final JComboBox<String> httpPayloadCombo =
		new JComboBox<>(new String[] {"Raw", "Form Data", "Binary"});
	private final JBTextField httpUrlField = new JBTextField();
	private final JButton httpSendButton = new JButton(AllIcons.Actions.Execute);
	private final JButton httpSendDownloadButton = new JButton(AllIcons.Actions.Download);
	private final JButton httpDebugButton = new JButton(AllIcons.Actions.StartDebugger);
	private final JButton httpGlobalContextButton = new JButton(AllIcons.Nodes.Variable);

	private final JBTextField grpcTargetField = new JBTextField();
	private final JComboBox<String> grpcServiceCombo = new JComboBox<>();
	private final JComboBox<String> grpcMethodCombo = new JComboBox<>();
	private final JButton grpcReloadButton = new JButton(AllIcons.Actions.Refresh);
	private final JButton grpcSendButton = new JButton(AllIcons.Actions.Execute);
	private final JButton grpcDebugButton = new JButton(AllIcons.Actions.StartDebugger);

	private final JComboBox<String> kafkaBootstrapCombo = new JComboBox<>();
	private final JComboBox<String> kafkaTopicCombo = new JComboBox<>();
	private final JBTextField kafkaKeyField = new JBTextField();
	private final JButton kafkaReloadButton = new JButton(AllIcons.Actions.Refresh);
	private final JButton kafkaSendButton = new JButton(AllIcons.Actions.Execute);
	private final JComboBox<String> kafkaListenBootstrapCombo = new JComboBox<>();
	private final JComboBox<String> kafkaListenTopicCombo = new JComboBox<>();
	private final JBTextField kafkaGroupIdField = new JBTextField();
	private final JButton kafkaListenReloadButton = new JButton(AllIcons.Actions.Refresh);
	private final JButton kafkaListenButton = new JButton("Start Listening");

	private final CardLayout requestTopCards = new CardLayout();
	private final JPanel requestTopPanel = new JPanel(requestTopCards);
	private final JTabbedPane requestTabs = new JTabbedPane();
	private final EditorTextField requestBodyArea;
	private final CardLayout bodyCards = new CardLayout();
	private final JPanel bodyPanel = new JPanel(bodyCards);
	private final FormDataTableModel formDataTableModel = new FormDataTableModel();
	private final JTable formDataTable = new JTable(formDataTableModel);
	private final JButton addFormDataButton = new JButton("Add");
	private final JButton removeFormDataButton = new JButton("Remove");
	private final JButton chooseFormFileButton = new JButton("Choose File");
	private final JBTextField binaryFileField = new JBTextField();
	private final JButton binaryBrowseButton = new JButton("Browse");
	private final EditorTextField beforeScriptArea;
	private final EditorTextField afterScriptArea;
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
	private final Map<String, List<KafkaListenMessage>> kafkaListenMessagesByRequest = new ConcurrentHashMap<>();

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
		this.scriptFileType = resolveScriptFileType();
		this.requestBodyArea = new JsonBodyEditorField(project);
		this.beforeScriptArea = createScriptEditor();
		this.afterScriptArea = createScriptEditor();
		this.requestBodyArea.setOneLineMode(false);
		this.beforeScriptArea.setOneLineMode(false);
		this.afterScriptArea.setOneLineMode(false);
		this.grpcServiceCombo.setRenderer(new javax.swing.DefaultListCellRenderer());
		this.grpcMethodCombo.setRenderer(new javax.swing.DefaultListCellRenderer());
		this.urlParamSyncTimer = new javax.swing.Timer(350, e -> syncParamsFromUrlField());
		this.urlParamSyncTimer.setRepeats(false);
		this.headerPresets = stateService.getHeaderPresets();
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
		paramsPanel.add(buildParamsTablePanel(), "table");
		paramsPanel.add(buildKafkaParamsPanel(), "kafka");
		paramsPanel.add(buildKafkaListenParamsPanel(), "kafkaListen");
		requestTabs.add("Body", bodyPanel);
		requestTabs.add("Params", paramsPanel);
		requestTabs.add("Headers", buildHeadersPanel());
		requestTabs.add("Before Request", new JBScrollPane(beforeScriptArea));
		requestTabs.add("After Request", new JBScrollPane(afterScriptArea));

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, requestTabs, responseViewer.getComponent());
		splitPane.setResizeWeight(0.6);
		root.add(splitPane, BorderLayout.CENTER);
	}

	private JPanel buildHttpTopBar() {
		JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
		httpUrlField.setColumns(40);
		configureIconButton(httpSendButton, "Send");
		configureIconButton(httpSendDownloadButton, "Send and Download");
		configureIconButton(httpDebugButton, "Debug Call");
		configureIconButton(httpGlobalContextButton, "Global Context");
		topBar.add(httpMethodCombo);
		topBar.add(httpPayloadCombo);
		topBar.add(new JLabel("URL"));
		topBar.add(httpUrlField);
		topBar.add(httpSendButton);
		topBar.add(httpSendDownloadButton);
		topBar.add(httpDebugButton);
		topBar.add(httpGlobalContextButton);
		topBar.add(createRequestMenuButton());
		httpSendButton.addActionListener(e -> executeHttp());
		httpSendDownloadButton.addActionListener(e -> executeHttpDownload());
		httpDebugButton.addActionListener(e -> startDebugCall());
		httpGlobalContextButton.addActionListener(e -> GlobalContextDialog.show(root, project, stateService));
		return topBar;
	}

	private JPanel buildKafkaTopBar() {
		JPanel topBar = new JPanel(new GridBagLayout());
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
		topBar.add(new JLabel("Bootstrap servers"), constraints);

		constraints.gridx = 1;
		constraints.weightx = 0.35;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		topBar.add(kafkaBootstrapCombo, constraints);

		constraints.gridx = 2;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		topBar.add(new JLabel("Topic"), constraints);

		constraints.gridx = 3;
		constraints.weightx = 0.35;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		topBar.add(kafkaTopicCombo, constraints);

		constraints.gridx = 4;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		topBar.add(new JLabel("Key"), constraints);

		constraints.gridx = 5;
		constraints.weightx = 0.3;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		topBar.add(kafkaKeyField, constraints);

		configureIconButton(kafkaReloadButton, "Refresh Kafka metadata");
		configureIconButton(kafkaSendButton, "Send Kafka message");

		constraints.gridx = 6;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		topBar.add(kafkaReloadButton, constraints);

		constraints.gridx = 7;
		topBar.add(kafkaSendButton, constraints);

		constraints.gridx = 8;
		topBar.add(createRequestMenuButton(), constraints);

		kafkaReloadButton.addActionListener(e -> refreshKafkaMetadata());
		kafkaSendButton.addActionListener(e -> executeKafka());
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
		JPanel topBar = new JPanel(new GridBagLayout());
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
		topBar.add(new JLabel("Target"), constraints);

		constraints.gridx = 1;
		constraints.weightx = 0.2;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		topBar.add(grpcTargetField, constraints);

		constraints.gridx = 2;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		topBar.add(new JLabel("Service"), constraints);

		constraints.gridx = 3;
		constraints.weightx = 0.4;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		topBar.add(grpcServiceCombo, constraints);

		constraints.gridx = 4;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		topBar.add(new JLabel("Method"), constraints);

		constraints.gridx = 5;
		constraints.weightx = 0.4;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		topBar.add(grpcMethodCombo, constraints);

		configureIconButton(grpcReloadButton, "Reload");
		configureIconButton(grpcSendButton, "Send");
		configureIconButton(grpcDebugButton, "Debug Call");

		constraints.gridx = 6;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		topBar.add(grpcReloadButton, constraints);

		constraints.gridx = 7;
		topBar.add(grpcSendButton, constraints);

		constraints.gridx = 8;
		topBar.add(grpcDebugButton, constraints);

		constraints.gridx = 9;
		JButton menuButton = createRequestMenuButton();
		topBar.add(menuButton, constraints);

		grpcReloadButton.addActionListener(e -> reloadGrpcServices());
		grpcSendButton.addActionListener(e -> executeGrpc());
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
	}

	private void showRequestMenu(JButton anchor) {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem getCurlItem = new JMenuItem("Get cURL");
		JMenuItem openRequestItem = new JMenuItem("Open Request");
		JMenuItem openResponseItem = new JMenuItem("Open Response");
		JMenuItem classBodyItem = new JMenuItem("Class body");
		JMenuItem protoBodyItem = new JMenuItem("Proto body");
		getCurlItem.addActionListener(e -> copyCurl());
		openRequestItem.addActionListener(e -> openRequestWindow());
		openResponseItem.addActionListener(e -> openResponseWindow());
		classBodyItem.addActionListener(e -> generateBodyFromClass());
		protoBodyItem.addActionListener(e -> generateBodyFromProto());
		boolean enabled =
			activeNode != null && activeNode.type == NodeType.REQUEST && activeNode.requestType != RequestType.CHAIN;
		getCurlItem.setEnabled(enabled && activeNode.requestType == RequestType.HTTP);
		openRequestItem.setEnabled(enabled);
		openResponseItem.setEnabled(enabled);
		classBodyItem.setEnabled(enabled);
		protoBodyItem.setEnabled(enabled);
		menu.add(getCurlItem);
		menu.addSeparator();
		menu.add(openRequestItem);
		menu.add(openResponseItem);
		menu.addSeparator();
		menu.add(classBodyItem);
		menu.add(protoBodyItem);
		menu.show(anchor, 0, anchor.getHeight());
	}

	private void copyCurl() {
		HttpExecutionContext context = prepareHttpExecution();
		if (context == null) {
			return;
		}
		String curl = CurlCommandBuilder.build(
			context.method,
			context.url,
			context.headers,
			context.params,
			context.body,
			context.payloadType,
			context.formData,
			context.binaryFilePath
		);
		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(curl), null);
		responseViewer.showLog("cURL copied to clipboard.");
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
		actions.add(addButton);
		actions.add(removeButton);
		for (JButton extraButton : extraButtons) {
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
		return chooser.showOpenDialog(root) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
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
		if ("Form Data".equals(label)) {
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
			if (info != null && info.name != null && info.name.length() > longest.length()) {
				longest = info.name;
			}
		}
		return longest.isEmpty() ? "com.example.Service" : longest;
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
		if (node == null) {
			return;
		}
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
	}

	private void loadHttp(String requestId) {
		isLoading = true;
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		RequestStatusState status = stateService.getRequestStatus(requestId);
		updateHeaderNameEditor(RequestType.HTTP);
		httpMethodCombo.setSelectedItem(details != null && details.method != null ? details.method : "GET");
		httpPayloadCombo.setSelectedItem(PayloadTypes.resolveLabel(details != null ? details.payloadType : null));
		httpUrlField.setText(details != null && details.url != null ? details.url : "");
		loadSharedStatus(status);
		formDataTableModel.setEntries(status != null ? status.formData : List.of());
		binaryFileField.setText(status != null ? safe(status.binaryFilePath) : "");
		headersTableModel.setHeaders(status != null ? status.requestHeaders : List.of(), true);
		List<HeaderEntryState> mergedParams =
			UrlParamUtils.mergeParamsWithUrl(status != null ? status.requestParams : List.of(),
				details != null ? details.url : null);
		paramsTableModel.setHeaders(mergedParams, true);
		paramsCards.show(paramsPanel, "table");
		switchPayloadType();
		isLoading = false;
	}

	private void loadGrpc(String requestId) {
		isLoading = true;
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		RequestStatusState status = stateService.getRequestStatus(requestId);
		updateHeaderNameEditor(RequestType.GRPC);
		grpcTargetField.setText(details != null ? safe(details.target) : "");
		loadSharedStatus(status);
		bodyCards.show(bodyPanel, "raw");
		headersTableModel.setHeaders(status != null ? status.requestHeaders : List.of(), false);
		paramsTableModel.setHeaders(status != null ? status.requestParams : List.of(), true);
		paramsCards.show(paramsPanel, "table");
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
		loadSharedStatus(status);
		bodyCards.show(bodyPanel, "raw");
		headersTableModel.setHeaders(status != null ? status.requestHeaders : List.of(), true);
		kafkaKeyTypeCombo.setSelectedItem(status != null && status.kafkaKeyType != null ? status.kafkaKeyType : "String");
		kafkaBodyTypeCombo.setSelectedItem(status != null && status.kafkaBodyType != null ? status.kafkaBodyType : "JSON");
		kafkaPartitionsField.setText(status != null ? safe(status.kafkaPartitions) : "");
		paramsCards.show(paramsPanel, "kafka");
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
		kafkaListenButton.setText(isKafkaListening(node.id) ? "Stop Listening" : "Start Listening");
		isLoading = false;
	}

	private void loadSharedStatus(RequestStatusState status) {
		requestBodyArea.setText(status != null ? safe(status.requestBody) : "");
		beforeScriptArea.setText(status != null ? safe(status.beforeScript) : "");
		afterScriptArea.setText(status != null ? safe(status.afterScript) : "");
		responseViewer.setContent(
			status != null ? safe(status.responseBody) : "",
			status != null ? safe(status.responseHeaders) : "",
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
		stateService.saveRequestDetails(details);

		RequestStatusState status = buildStatus(requestId);
		stateService.saveRequestStatus(status);
	}

	private void saveKafka(String requestId) {
		RequestDetailsState details = requestDetailsForSave(requestId, RequestType.KAFKA);
		details.kafkaBootstrapServers = comboEditorText(kafkaBootstrapCombo);
		details.kafkaTopic = comboEditorText(kafkaTopicCombo);
		details.kafkaKey = kafkaKeyField.getText();
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
		RequestStatusState status = new RequestStatusState();
		status.requestId = requestId;
		status.requestBody = requestBodyArea.getText();
		status.requestHeaders = headersTableModel.getHeaders();
		status.requestParams = paramsTableModel.getHeaders();
		status.formData = formDataTableModel.getEntries();
		status.binaryFilePath = binaryFileField.getText();
		status.responseBody = responseViewer.getResponseBody();
		status.responseHeaders = responseViewer.getResponseHeaders();
		status.logs = responseViewer.getLogs();
		status.beforeScript = beforeScriptArea.getText();
		status.afterScript = afterScriptArea.getText();
		return status;
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
		String binaryFilePath
	) {
	}

	// ---- execution ----

	public void executeHttp() {
		HttpExecutionContext context = prepareHttpExecution();
		if (context == null) {
			return;
		}

		responseViewer.clearStatus();
		runInBackground(() -> {
			ExecutionResult result = executionService.executeWithScripts(
				context.method, context.url, context.headers, context.params, context.body, context.before,
				context.after, false, null, context.payloadType, context.formData, context.binaryFilePath
			);
			responseViewer.updateResponse(result, false);
		});
	}

	private void executeHttpDownload() {
		HttpExecutionContext context = prepareHttpExecution();
		if (context == null) {
			return;
		}

		responseViewer.clearStatus();
		runInBackground(() -> {
			DownloadResult result = executionService.executeWithScriptsDownload(
				context.method, context.url, context.headers, context.params, context.body, context.before,
				context.after, context.payloadType, context.formData, context.binaryFilePath
			);
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
		RequestStatusState status = stateService.getRequestStatus(activeNode.id);
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
			status != null ? status.binaryFilePath : ""
		);
	}

	public void executeGrpc() {
		if (activeNode == null || activeNode.requestType != RequestType.GRPC) {
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
		RequestStatusState status = stateService.getRequestStatus(activeNode.id);
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();
		List<HeaderEntryState> params = status != null ? status.requestParams : List.of();
		String body = status != null ? status.requestBody : "";
		String before = status != null ? status.beforeScript : "";
		String after = status != null ? status.afterScript : "";

		responseViewer.clearStatus();
		runInBackground(() -> {
			ExecutionResult result =
				executionService.executeGrpcWithScripts(details, headers, params, body, before, after, null);
			responseViewer.updateResponse(result, true);
		});
	}

	public void executeKafka() {
		if (activeNode == null || activeNode.requestType != RequestType.KAFKA) {
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
		RequestStatusState status = stateService.getRequestStatus(activeNode.id);
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();
		String body = status != null ? status.requestBody : "";
		String before = status != null ? status.beforeScript : "";
		String after = status != null ? status.afterScript : "";
		String keyType = status != null && status.kafkaKeyType != null ? status.kafkaKeyType : "String";
		String bodyType = status != null && status.kafkaBodyType != null ? status.kafkaBodyType : "JSON";
		String partition = status != null ? status.kafkaPartitions : "";

		responseViewer.clearStatus();
		runInBackground(() -> {
			ExecutionResult result = executionService.executeKafkaWithScripts(
				details,
				headers,
				body,
				before,
				after,
				keyType,
				bodyType,
				partition,
				null
			);
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
				List<String> topics = kafkaMetadataService.listTopics(bootstrapServers);
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
				List<String> topics = kafkaMetadataService.listTopics(bootstrapServers);
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
		request.bootstrapServers = details.kafkaBootstrapServers;
		request.topic = details.kafkaTopic;
		request.groupId = details.kafkaGroupId;
		request.offsetStrategy =
			status != null && status.kafkaOffsetStrategy != null ? status.kafkaOffsetStrategy : "Latest";
		List<KafkaListenMessage> existingMessages = loadKafkaListenMessages(requestId);
		kafkaListenMessagesByRequest.put(requestId, existingMessages);
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
				message -> invokeLater(() -> appendKafkaListenMessage(requestId, message)),
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
		List<KafkaListenMessage> messages =
			kafkaListenMessagesByRequest.computeIfAbsent(requestId, this::loadKafkaListenMessages);
		messages.add(0, message);
		updateKafkaListenResponse(
			requestId,
			toPrettyJson(messages),
			"Listening for Kafka messages... Received: " + messages.size()
		);
	}

	private void handleKafkaListenError(
		String requestId,
		Throwable error
	) {
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
				List<GrpcServiceInfo> services = grpcExecutor.listServices(target);
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

	private void startDebugCall() {
		if (!hasEditableRequest()) {
			return;
		}
		saveActive();
		if (debugCallSession != null) {
			debugCallSession.abandon(true);
		}
		debugCallSession = new DebugCallSession(
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
	}

	private void addEditableComboAutoSave(JComboBox<String> combo) {
		if (combo.getEditor().getEditorComponent() instanceof javax.swing.text.JTextComponent textComponent) {
			textComponent.getDocument().addDocumentListener(new AutoSaveListener());
		}
	}

	private void syncUrlFromParamsTable() {
		if (isLoading || isSyncingParamsFromUrl || activeNode == null || activeNode.requestType != RequestType.HTTP) {
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

	private void generateBodyFromClass() {
		if (!hasEditableRequest()) {
			return;
		}
		bodyGenerator.generateFromClass();
	}

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
		EditorTextField editor = new EditorTextField(source.getDocument(), project, scriptFileType, false, false);
		editor.setOneLineMode(false);
		return editor;
	}

	private void showEditorDialog(String title, JComponent content) {
		JDialog dialog = new JDialog();
		dialog.setTitle(title);
		dialog.getContentPane().add(content);
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(root);
		dialog.setModal(false);
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
		return new EditorTextField("", project, scriptFileType);
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
}
