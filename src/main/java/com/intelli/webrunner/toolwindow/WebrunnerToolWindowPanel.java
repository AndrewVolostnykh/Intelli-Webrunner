package com.intelli.webrunner.toolwindow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.webrunner.execution.HttpExecutionResponse;
import com.intelli.webrunner.execution.HttpExecutor;
import com.intelli.webrunner.execution.HttpPayloadType;
import com.intelli.webrunner.grpc.GrpcExecutionResponse;
import com.intelli.webrunner.grpc.GrpcExecutor;
import com.intelli.webrunner.grpc.GrpcServiceInfo;
import com.intelli.webrunner.script.ScriptContext;
import com.intelli.webrunner.script.ScriptHelpers;
import com.intelli.webrunner.script.ScriptLogger;
import com.intelli.webrunner.script.ScriptRequest;
import com.intelli.webrunner.script.ScriptRuntime;
import com.intelli.webrunner.script.VarsStore;
import com.intelli.webrunner.state.ChainState;
import com.intelli.webrunner.state.FormEntryState;
import com.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.intelli.webrunner.state.HeaderEntryState;
import com.intelli.webrunner.state.HeaderPresetState;
import com.intelli.webrunner.state.NodeState;
import com.intelli.webrunner.state.NodeType;
import com.intelli.webrunner.state.RequestDetailsState;
import com.intelli.webrunner.state.RequestStatusState;
import com.intelli.webrunner.state.RequestType;
import com.intelli.webrunner.state.WebrunnerState;
import com.intelli.webrunner.ui.FormDataTableModel;
import com.intelli.webrunner.ui.HeaderTableModel;
import com.intelli.webrunner.ui.HeaderPresetTableModel;
import com.intelli.webrunner.ui.JsonBodyEditorField;
import com.intelli.webrunner.util.JsonUtils;
import com.intelli.webrunner.util.TemplateEngine;
import com.intellij.icons.AllIcons;
import com.intellij.icons.AllIcons.Actions;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class WebrunnerToolWindowPanel {

	private final Project project;
	private final GlobalWebrunnerStateService stateService;
	private final JPanel root;
	private final JTree tree;
	private final DefaultTreeModel treeModel;
	private final JButton newFolderButton;
	private final JButton newRequestButton;
	private final JButton moreButton;
	private final JButton deleteButton;
	private final CardLayout editorCards;
	private final JPanel editorPanel;
	private final JPanel emptyPanel;

	private NodeState currentNode;
	private boolean isLoading = false;
	private boolean isStoppingTableEditing = false;
	private boolean isSyncingParamsFromUrl = false;
	private final javax.swing.Timer urlParamSyncTimer;

	private final ObjectMapper mapper = new ObjectMapper();
	private final TemplateEngine templateEngine = new TemplateEngine();
	private final ScriptRuntime scriptRuntime = new ScriptRuntime();
	private final HttpExecutor httpExecutor = new HttpExecutor();
	private final GrpcExecutor grpcExecutor = new GrpcExecutor();

	private final JComboBox<String> httpMethodCombo =
		new JComboBox<>(new String[] {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"});
	private final JComboBox<String> httpPayloadCombo =
		new JComboBox<>(new String[] {"Raw", "Form Data", "Binary"});
	private final JBTextField httpUrlField = new JBTextField();
	private final JButton httpSendButton = new JButton(AllIcons.Actions.Execute);
	private final JButton httpSendDownloadButton = new JButton(AllIcons.Actions.Download);
	private final JButton httpDebugButton = new JButton(AllIcons.Actions.StartDebugger);
	private final JBLabel httpStatusLabel = new JBLabel("");

	private final JBTextField grpcTargetField = new JBTextField();
	private final JComboBox<String> grpcServiceCombo = new JComboBox<>();
	private final JComboBox<String> grpcMethodCombo = new JComboBox<>();
	private final JButton grpcReloadButton = new JButton(AllIcons.Actions.Refresh);
	private final JButton grpcSendButton = new JButton(AllIcons.Actions.Execute);
	private final JButton grpcDebugButton = new JButton(AllIcons.Actions.StartDebugger);
	private final JBLabel grpcStatusLabel = new JBLabel("");

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
	private final HeaderPresetTableModel headerPresetTableModel = new HeaderPresetTableModel();
	private List<HeaderPresetState> headerPresets = new ArrayList<>();

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

	private final JTabbedPane responseTabs = new JTabbedPane();
	private final JBLabel responseStatusLabel = new JBLabel("");
	private final EditorTextField responseBodyArea;
	private final EditorTextField responseHeadersArea;
	private final JBTextArea responseLogsArea = new JBTextArea();

	private final DefaultListModel<String> chainListModel = new DefaultListModel<>();
	private final JBList<String> chainList = new JBList<>(chainListModel);
	private final JComboBox<String> chainRequestCombo = new JComboBox<>();
	private final JButton chainAddButton = new JButton("Add");
	private final JButton chainRemoveButton = new JButton("Remove");
	private final JButton chainRunButton = new JButton("Run");
	private final JButton chainDebugButton = new JButton("Debug");
	private final JButton chainNextButton = new JButton("Next");
	private final JBTextArea chainLogsArea = new JBTextArea();
	private final EditorTextField chainCurrentStateArea;
	private final JTabbedPane chainResponseTabs = new JTabbedPane();

	private final JButton openRequestWindowButton = new JButton("Open Request");
	private final JButton openResponseWindowButton = new JButton("Open Response");
	private final JButton openChainWindowButton = new JButton("Open Chain");
	private final FileType scriptFileType;

	private final Map<String, List<GrpcServiceInfo>> grpcServicesCache = new ConcurrentHashMap<>();
	private final Map<String, String> grpcServiceSelection = new ConcurrentHashMap<>();
	private boolean isGrpcReloading = false;

	private ChainSession chainSession;
	private DebugCallSession debugCallSession;

	public WebrunnerToolWindowPanel(Project project) {
		this.project = project;
		this.stateService = GlobalWebrunnerStateService.getInstance();
		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Requests");
		this.treeModel = new DefaultTreeModel(rootNode);
		this.tree = new JTree(treeModel);
		this.newFolderButton = new JButton("", AllIcons.Actions.NewFolder);
		this.newRequestButton = new JButton("", Actions.AddFile);
		this.moreButton = new JButton("⋮");
		this.deleteButton = new JButton("-");

		this.editorCards = new CardLayout();
		this.editorPanel = new JPanel(editorCards);
		this.emptyPanel = new JPanel(new BorderLayout());
		this.root = new JPanel(new BorderLayout());
		this.scriptFileType = resolveScriptFileType();
		this.requestBodyArea = new JsonBodyEditorField(project);
		this.beforeScriptArea = createScriptEditor();
		this.afterScriptArea = createScriptEditor();
		this.responseBodyArea = new EditorTextField("", project, JsonFileType.INSTANCE);
		this.responseHeadersArea = new EditorTextField("", project, JsonFileType.INSTANCE);
		this.chainCurrentStateArea = new EditorTextField("", project, JsonFileType.INSTANCE);
		this.requestBodyArea.setOneLineMode(false);
		this.beforeScriptArea.setOneLineMode(false);
		this.afterScriptArea.setOneLineMode(false);
		this.responseBodyArea.setOneLineMode(false);
		this.responseHeadersArea.setOneLineMode(false);
		this.chainCurrentStateArea.setOneLineMode(false);
		this.grpcServiceCombo.setRenderer(new DefaultListCellRenderer());
		this.grpcMethodCombo.setRenderer(new DefaultListCellRenderer());
		this.urlParamSyncTimer = new javax.swing.Timer(350, e -> syncParamsFromUrlField());
		this.urlParamSyncTimer.setRepeats(false);
		this.headerPresets = stateService.getHeaderPresets();

		buildUi();
		configureGrpcComboPopups();
		reloadTree();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
		JPanel leftPanel = new JPanel(new BorderLayout());
		tree.setRootVisible(true);
		tree.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tree.addTreeSelectionListener(this::handleTreeSelection);
		tree.setComponentPopupMenu(buildTreePopupMenu());
		tree.setDragEnabled(true);
		tree.setDropMode(DropMode.ON_OR_INSERT);
		tree.setTransferHandler(new TreeTransferHandler());
		tree.setCellRenderer(new NodeTreeCellRenderer());

		JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		Dimension compactButton = new Dimension(28, 28);
		moreButton.setPreferredSize(compactButton);
		moreButton.setMargin(new Insets(0, 0, 0, 0));
		deleteButton.setPreferredSize(compactButton);
		deleteButton.setMargin(new Insets(0, 0, 0, 0));
		leftActions.add(newFolderButton);
		leftActions.add(newRequestButton);
		leftActions.add(deleteButton);
		leftActions.add(moreButton);
		newFolderButton.addActionListener(e -> createFolder());
		newRequestButton.addActionListener(e -> createRequest());
		deleteButton.addActionListener(e -> deleteSelected());
		moreButton.addActionListener(e -> showMoreMenu());

		leftPanel.add(leftActions, BorderLayout.NORTH);
		JScrollPane treeScroll = new JBScrollPane(tree);
		treeScroll.setMinimumSize(new Dimension(200, 0));
		leftPanel.setMinimumSize(new Dimension(220, 0));
		leftPanel.add(treeScroll, BorderLayout.CENTER);

		emptyPanel.add(new JBLabel("Select or create a request to begin."), BorderLayout.CENTER);

		editorPanel.add(emptyPanel, "empty");
		editorPanel.add(buildRequestPanel(), "request");
		editorPanel.add(buildChainPanel(), "chain");

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, editorPanel);
		splitPane.setResizeWeight(0.25);
		root.add(splitPane, BorderLayout.CENTER);

		attachAutoSaveListeners();
		attachHotkeys();
		openRequestWindowButton.setEnabled(false);
		openResponseWindowButton.setEnabled(false);
		openChainWindowButton.setEnabled(false);
		editorCards.show(editorPanel, "empty");
	}

	private JPanel buildRequestPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		requestTopPanel.add(buildHttpTopBar(), "http");
		requestTopPanel.add(buildGrpcTopBar(), "grpc");
		JPanel topContainer = new JPanel(new BorderLayout());
		topContainer.add(requestTopPanel, BorderLayout.CENTER);
		panel.add(topContainer, BorderLayout.NORTH);

		bodyPanel.add(new JBScrollPane(requestBodyArea), "raw");
		bodyPanel.add(buildFormDataPanel(), "form");
		bodyPanel.add(buildBinaryPanel(), "binary");
		requestTabs.add("Body", bodyPanel);
		requestTabs.add("Params", buildParamsPanel());
		requestTabs.add("Headers", buildHeadersPanel());
		requestTabs.add("Before Request", new JBScrollPane(beforeScriptArea));
		requestTabs.add("After Request", new JBScrollPane(afterScriptArea));

		responseTabs.add("Response Body", new JBScrollPane(responseBodyArea));
		responseTabs.add("Response Headers", new JBScrollPane(responseHeadersArea));
		responseTabs.add("Logs", new JBScrollPane(responseLogsArea));

		JPanel responseContainer = new JPanel(new BorderLayout());
		responseStatusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		responseContainer.add(responseStatusLabel, BorderLayout.NORTH);
		responseContainer.add(responseTabs, BorderLayout.CENTER);

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, requestTabs, responseContainer);
		splitPane.setResizeWeight(0.6);
		panel.add(splitPane, BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildHttpTopBar() {
		JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
		httpUrlField.setColumns(40);
		httpSendButton.setToolTipText("Send");
		httpSendButton.setMargin(new Insets(0, 0, 0, 0));
		httpSendButton.setPreferredSize(new Dimension(28, 28));
		httpSendDownloadButton.setToolTipText("Send and Download");
		httpSendDownloadButton.setMargin(new Insets(0, 0, 0, 0));
		httpSendDownloadButton.setPreferredSize(new Dimension(28, 28));
		httpDebugButton.setToolTipText("Debug Call");
		httpDebugButton.setMargin(new Insets(0, 0, 0, 0));
		httpDebugButton.setPreferredSize(new Dimension(28, 28));
		topBar.add(httpMethodCombo);
		topBar.add(httpPayloadCombo);
		topBar.add(new JLabel("URL"));
		topBar.add(httpUrlField);
		topBar.add(httpSendButton);
		topBar.add(httpSendDownloadButton);
		topBar.add(httpDebugButton);
		topBar.add(createRequestMenuButton());
		httpSendButton.addActionListener(e -> executeHttp());
		httpSendDownloadButton.addActionListener(e -> executeHttpDownload());
		httpDebugButton.addActionListener(e -> startDebugCall());
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

		grpcReloadButton.setToolTipText("Reload");
		grpcReloadButton.setMargin(new Insets(0, 0, 0, 0));
		grpcReloadButton.setPreferredSize(new Dimension(28, 28));
		grpcSendButton.setToolTipText("Send");
		grpcSendButton.setMargin(new Insets(0, 0, 0, 0));
		grpcSendButton.setPreferredSize(new Dimension(28, 28));
		grpcDebugButton.setToolTipText("Debug Call");
		grpcDebugButton.setMargin(new Insets(0, 0, 0, 0));
		grpcDebugButton.setPreferredSize(new Dimension(28, 28));

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
		menuButton.setPreferredSize(new Dimension(28, 28));
		menuButton.setMargin(new Insets(0, 0, 0, 0));
		topBar.add(menuButton, constraints);

		grpcReloadButton.addActionListener(e -> reloadGrpcServices());
		grpcSendButton.addActionListener(e -> executeGrpc());
		grpcDebugButton.addActionListener(e -> startDebugCall());
		return topBar;
	}

	private JPanel buildChainPanel() {
		JPanel panel = new JPanel(new BorderLayout());

		JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
		chainNextButton.setEnabled(false);
		topBar.add(chainRunButton);
		topBar.add(chainDebugButton);
		topBar.add(chainNextButton);
		topBar.add(openChainWindowButton);
		chainRunButton.addActionListener(e -> runChain(false));
		chainDebugButton.addActionListener(e -> runChain(true));
		chainNextButton.addActionListener(e -> runChainNext());
		openChainWindowButton.addActionListener(e -> openChainWindow());
		panel.add(topBar, BorderLayout.NORTH);

		JPanel chainEditor = new JPanel(new BorderLayout());
		chainList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		chainList.setCellRenderer(new ChainCellRenderer());
		chainList.setDragEnabled(true);
		chainList.setDropMode(DropMode.INSERT);
		chainList.setTransferHandler(new ChainListTransferHandler());
		chainEditor.add(new JBScrollPane(chainList), BorderLayout.CENTER);

		JPanel chainControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		chainRequestCombo.setRenderer(new ChainComboRenderer());
		chainControls.add(new JLabel("Request"));
		chainControls.add(chainRequestCombo);
		chainControls.add(chainAddButton);
		chainControls.add(chainRemoveButton);
		chainAddButton.addActionListener(e -> addChainRequest());
		chainRemoveButton.addActionListener(e -> removeChainRequest());
		chainEditor.add(chainControls, BorderLayout.SOUTH);

		chainResponseTabs.add("Logs", new JBScrollPane(chainLogsArea));
		chainResponseTabs.add("Current State", new JBScrollPane(chainCurrentStateArea));

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chainEditor, chainResponseTabs);
		splitPane.setResizeWeight(0.6);
		panel.add(splitPane, BorderLayout.CENTER);

		return panel;
	}

	private JPopupMenu buildTreePopupMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem rename = new JMenuItem("Rename");
		JMenuItem remove = new JMenuItem("Delete");
		JMenuItem importHttpItem = new JMenuItem("Import .http");
		JMenuItem exportHttpItem = new JMenuItem("Export .http");
		JMenuItem importOpenApiItem = new JMenuItem("Import OpenAPI");
		JMenuItem exportOpenApiItem = new JMenuItem("Export OpenAPI");
		rename.addActionListener(e -> renameSelected());
		remove.addActionListener(e -> deleteSelected());
		importHttpItem.addActionListener(e -> importHttpFromTree());
		exportHttpItem.addActionListener(e -> exportHttpFromTree());
		importOpenApiItem.addActionListener(e -> importOpenApiFromTree());
		exportOpenApiItem.addActionListener(e -> exportOpenApiFromTree());
		menu.add(rename);
		menu.add(remove);
		menu.addSeparator();
		menu.add(importHttpItem);
		menu.add(exportHttpItem);
		menu.addSeparator();
		menu.add(importOpenApiItem);
		menu.add(exportOpenApiItem);
		menu.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				TreeFolderSelection selection = getTreeFolderSelection();
				boolean enable = selection != null;
				importHttpItem.setEnabled(enable);
				exportHttpItem.setEnabled(enable);
				importOpenApiItem.setEnabled(enable);
				exportOpenApiItem.setEnabled(enable);
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}
		});
		return menu;
	}

	private void showMoreMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem refreshItem = new JMenuItem("Refresh");
		JMenuItem classBodyItem = new JMenuItem("Class body");
		JMenuItem protoBodyItem = new JMenuItem("Proto body");
		JMenuItem importCollectionsItem = new JMenuItem("Import Collections (JSON)");
		JMenuItem exportCollectionsItem = new JMenuItem("Export Collections (JSON)");
		JMenuItem importHttpItem = new JMenuItem("Import .http");
		JMenuItem exportHttpItem = new JMenuItem("Export .http");
		JMenuItem settingsItem = new JMenuItem("Settings");
		JMenuItem infoItem = new JMenuItem("Info");
		refreshItem.addActionListener(e -> reloadTree());
		importCollectionsItem.addActionListener(e -> importCollectionsJson());
		exportCollectionsItem.addActionListener(e -> exportCollectionsJson());
		importHttpItem.addActionListener(e -> importHttpFromChooser());
		exportHttpItem.addActionListener(e -> exportHttpFromChooser());
		settingsItem.addActionListener(e -> openSettingsDialog());
		infoItem.addActionListener(e -> showInfoDialog());
		menu.add(refreshItem);
		menu.addSeparator();
		menu.add(importCollectionsItem);
		menu.add(exportCollectionsItem);
		menu.addSeparator();
		menu.add(importHttpItem);
		menu.add(exportHttpItem);
		menu.addSeparator();
		menu.add(settingsItem);
		menu.add(infoItem);
		menu.show(moreButton, 0, moreButton.getHeight());
	}

	private JButton createRequestMenuButton() {
		JButton button = new JButton("\u22ee");
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setPreferredSize(new Dimension(28, 28));
		button.addActionListener(e -> showRequestMenu(button));
		return button;
	}

	private void showRequestMenu(JButton anchor) {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem openRequestItem = new JMenuItem("Open Request");
		JMenuItem openResponseItem = new JMenuItem("Open Response");
		JMenuItem openBeforeItem = new JMenuItem("Open Before Request");
		JMenuItem openAfterItem = new JMenuItem("Open After Request");
		JMenuItem classBodyItem = new JMenuItem("Class body");
		JMenuItem protoBodyItem = new JMenuItem("Proto body");
		openRequestItem.addActionListener(e -> openRequestWindow());
		openResponseItem.addActionListener(e -> openResponseWindow());
		openBeforeItem.addActionListener(e -> openBeforeRequestWindow());
		openAfterItem.addActionListener(e -> openAfterRequestWindow());
		classBodyItem.addActionListener(e -> generateBodyFromClass());
		protoBodyItem.addActionListener(e -> generateBodyFromProto());
		boolean enabled =
			currentNode != null && currentNode.type == NodeType.REQUEST && currentNode.requestType != RequestType.CHAIN;
		openRequestItem.setEnabled(enabled);
		openResponseItem.setEnabled(enabled);
		openBeforeItem.setEnabled(enabled);
		openAfterItem.setEnabled(enabled);
		classBodyItem.setEnabled(enabled);
		protoBodyItem.setEnabled(enabled);
		menu.add(openRequestItem);
		menu.add(openResponseItem);
		menu.add(openBeforeItem);
		menu.add(openAfterItem);
		menu.addSeparator();
		menu.add(classBodyItem);
		menu.add(protoBodyItem);
		menu.show(anchor, 0, anchor.getHeight());
	}

	private void renameSelected() {
		if (currentNode == null) {
			return;
		}
		String name = JOptionPane.showInputDialog(root, "New name:", currentNode.name);
		if (name == null || name.isBlank()) {
			return;
		}
		stateService.updateNodeName(currentNode.id, name);
		reloadTree();
	}

	private JPanel buildHeadersPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		headersTable.setFillsViewportHeight(true);
		configureHeadersTableColumns();
		panel.add(new JBScrollPane(headersTable), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(addHeaderButton);
		actions.add(removeHeaderButton);
		addHeaderButton.addActionListener(e -> headersTableModel.addEmptyRow());
		removeHeaderButton.addActionListener(e -> {
			int index = headersTable.getSelectedRow();
			headersTableModel.removeRow(index);
		});
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	private JPanel buildParamsPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		paramsTable.setFillsViewportHeight(true);
		configureParamsTableColumns();
		panel.add(new JBScrollPane(paramsTable), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(addParamButton);
		actions.add(removeParamButton);
		addParamButton.addActionListener(e -> paramsTableModel.addEmptyRow());
		removeParamButton.addActionListener(e -> {
			int index = paramsTable.getSelectedRow();
			paramsTableModel.removeRow(index);
		});
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	private JPanel buildFormDataPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		formDataTable.setFillsViewportHeight(true);
		configureFormDataTableColumns();
		panel.add(new JBScrollPane(formDataTable), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(addFormDataButton);
		actions.add(removeFormDataButton);
		actions.add(chooseFormFileButton);
		addFormDataButton.addActionListener(e -> formDataTableModel.addEmptyRow());
		removeFormDataButton.addActionListener(e -> {
			int index = formDataTable.getSelectedRow();
			formDataTableModel.removeRow(index);
		});
		chooseFormFileButton.addActionListener(e -> chooseFormDataFile());
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
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select Binary File");
		int result = chooser.showOpenDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		if (file != null) {
			binaryFileField.setText(file.getAbsolutePath());
		}
	}

	private void chooseFormDataFile() {
		int row = formDataTable.getSelectedRow();
		if (row < 0) {
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select Form Data File");
		int result = chooser.showOpenDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		if (file != null) {
			formDataTableModel.setValueAt("File", row, 2);
			formDataTableModel.setValueAt(file.getAbsolutePath(), row, 3);
		}
	}

	private void showInfoDialog() {
		JDialog dialog = new JDialog();
		dialog.setTitle("Webrunner Info");
		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Overview", createInfoTab(overviewText()));
		tabs.add("Before Request", createInfoTab(beforeRequestText()));
		tabs.add("After Request", createInfoTab(afterRequestText()));
		tabs.add("Chain", createInfoTab(chainText()));
		tabs.add("Scripting", createInfoTab(scriptingText()));
		tabs.add("Debug Call", createInfoTab(debugCallText()));
		tabs.add("Scripting API", createInfoTab(scriptingApiText()));
		dialog.getContentPane().add(tabs);
		dialog.setSize(900, 650);
		dialog.setLocationRelativeTo(root);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private JComponent createInfoTab(String text) {
		JBTextArea area = new JBTextArea(text);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		return new JBScrollPane(area);
	}

	private String overviewText() {
		return """
			Webrunner — інструмент для виконання HTTP та gRPC запитів прямо в IDE.
			
			Основні можливості:
			- Дерево запитів і папок: створення, перейменування, видалення, drag&drop.
			- HTTP запити: метод, URL, тіло, заголовки.
			- gRPC запити: target, service, method, автозавантаження сервісів через Reload.
			- Headers: таблиця керування заголовками/metadata (enabled, name, value).
			- Response: перегляд тіла відповіді, заголовків та логів.
			- Scripts: JS-скрипти до і після запиту.
			                - Import/export IntelliJ .http files for HTTP requests.
			""";
	}

	private String beforeRequestText() {
		return """
			Before Request — скрипт, який виконується ДО відправки запиту.
			
			Типові сценарії:
			- Підготовка/обчислення даних для body або headers.
			- Створення тимчасових змінних через vars.
			- Логування або швидкі перевірки.
			
			Доступні об'єкти:
			- vars: сховище змінних між запитами/кроками.
			- request: поточний запит (body + headers), можна змінювати перед відправкою.
			- context: повний контекст скрипта (vars, request, response, helpers, log).
			
			Приклад:
			log("Before: preparing token");
			vars.add("token", "Bearer " + uuid());
			""";
	}

	private String afterRequestText() {
		return """
			After Request — скрипт, який виконується ПІСЛЯ отримання відповіді.
			
			Типові сценарії:
			- Валідація відповіді (status, fields, business-правила).
			- Збереження значень у vars для наступних запитів.
			- Логування ключових подій.
			
			Доступні об'єкти:
			- response: відповідь (statusCode, headers, body; для gRPC також statusMessage).
			- vars: змінні для наступних кроків.
			- request: запит, який був відправлений.
			- context: повний контекст скрипта.
			
			Приклад:
			log("Status:", response.statusCode);
			assert(response.statusCode, 200, "Expected 200 OK");
			""";
	}

	private String chainText() {
		return """
			Chain — режим ланцюжка, який виконує кілька запитів послідовно.
			
			Особливості:
			- Кожен запит виконується по черзі.
			- Спільне сховище vars дозволяє передавати дані між кроками.
			- Логи та поточний стан доступні у вкладках Chain.
			
			Типовий сценарій:
			1) Логін (отримати токен)
			2) Зберегти токен у vars
			3) Виконати наступний бізнес-запит з цим токеном
			""";
	}

	private String scriptingText() {
		return """
			Scripting
			
			Скрипти виконуються в двох точках: Before Request та After Request. Обидва виконуються
			в одному й тому ж контексті VarsStore та логів, тому дані можуть передаватися між етапами.
			
			Before Request:
			- Запускається ДО шаблонізації (template engine).
			- Має доступ до `request` (ScriptRequest) і може змінювати body/headers/params/form data/binary path.
			- Має доступ до `rawRequest` (початковий стан до змін скриптом).
			- Будь-який виняток у скрипті зупиняє виконання запиту.
			- Після виконання скрипта формується snapshot `vars`, і саме по ньому виконується шаблонізація:
			  body, headers, params, form data, binary path, а також URL (з params).
			
			After Request:
			- Запускається ТІЛЬКИ якщо запит успішно відправлено і є відповідь.
			- Має доступ до `response` (HTTP або gRPC), до `request` (вже шаблонізований запит),
			  і до `rawRequest`.
			- Логи скрипта додаються в загальні logs.
			
			VarsStore:
			- `vars.add(key, value)` додає значення.
			- `vars.get(key)` читає значення.
			- Використовується для шаблонізації через `${...}` у body/headers/params/URL.
			
			Порядок виконання (звичайний Send):
			1) Before Request (скрипт)
			2) Шаблонізація даних і URL
			3) Відправка запиту
			4) After Request (скрипт)
			""";
	}

	private String debugCallText() {
		return """
			Debug Call
			
			Debug Call дозволяє покроково виконати запит та побачити стан на кожному етапі.
			Кнопка `Next` переходить до наступного етапу.
			
			Етапи:
			1) Current Request
			   - Сніппет поточного стану: body, params, headers, form data, binary path.
			   - Метод/URL (HTTP) або target/service/method (gRPC).
			
			2) Sent Request
			   - Виконується Before Request (як у звичайному запуску).
			   - Будуються шаблонізовані body/headers/params/form data/binary path та URL.
			   - Показується запит, який буде відправлено, + логи Before Request.
			
			3) Response Received
			   - Фактична відправка запиту.
			   - Показується статус і відповідь (body + headers).
			
			4) After Request Logs
			   - Виконується After Request.
			   - Показуються логи After Request.
			
			5) Final State
			   - Фінальний snapshot запиту після всіх скриптів.
			   - Повні логи.
			
			Inline Script:
			- Можна виконувати короткі JS-скрипти в поточному контексті.
			- Контекст з’являється після першого `Next` (коли сформовані vars/logger/helpers).
			""";
	}

	private String scriptingApiText() {
		return """
			Scripting API (JS):
			
			Глобальні функції:
			- log(...args): запис у Logs. Приймає кілька аргументів.
			- assert(actual, expected, message): проста перевірка, пише в лог при невідповідності.
			- uuid(): повертає випадковий UUID.
			
			Глобальні об'єкти:
			- vars: VarsStore для збереження значень між запитами.
			  Приклади:
			  vars.add("token", "abc");
			  vars.get("token");
			
			- request: ScriptRequest (body + headers).
			  Можна змінювати request перед відправкою.
			
			- response: доступний у After Request.
			  Містить statusCode, headers, body (для gRPC також statusMessage).
			
			- context: повний ScriptContext (vars, log, helpers, request, response).
			""";
	}

	private void configureHeadersTableColumns() {
		TableColumn enabledColumn = headersTable.getColumnModel().getColumn(0);
		enabledColumn.setPreferredWidth(60);
		enabledColumn.setMinWidth(60);
		enabledColumn.setMaxWidth(60);
		enabledColumn.setResizable(false);

		headersTable.getColumnModel().getColumn(1).setCellEditor(createHeaderNameEditor(COMMON_HEADER_NAMES));
		headersTable.getColumnModel().getColumn(2).setCellEditor(new HeaderValueCellEditor());
	}

	private void configureParamsTableColumns() {
		TableColumn enabledColumn = paramsTable.getColumnModel().getColumn(0);
		enabledColumn.setPreferredWidth(60);
		enabledColumn.setMinWidth(60);
		enabledColumn.setMaxWidth(60);
		enabledColumn.setResizable(false);
	}

	private void configureFormDataTableColumns() {
		TableColumn enabledColumn = formDataTable.getColumnModel().getColumn(0);
		enabledColumn.setPreferredWidth(60);
		enabledColumn.setMinWidth(60);
		enabledColumn.setMaxWidth(60);
		enabledColumn.setResizable(false);

		TableColumn typeColumn = formDataTable.getColumnModel().getColumn(2);
		JComboBox<String> typeCombo = new JComboBox<>(new String[] {"Text", "File"});
		typeColumn.setCellEditor(new javax.swing.DefaultCellEditor(typeCombo));
		typeColumn.setPreferredWidth(90);
		typeColumn.setMaxWidth(120);
	}

	private void updateHeaderNameEditor(RequestType type) {
		List<String> variants = type == RequestType.GRPC ? GRPC_HEADER_NAMES : COMMON_HEADER_NAMES;
		headersTable.getColumnModel().getColumn(1).setCellEditor(createHeaderNameEditor(variants));
	}

	private void switchPayloadType() {
		String label = resolvePayloadLabel(httpPayloadCombo.getSelectedItem());
		if ("Form Data".equals(label)) {
			bodyCards.show(bodyPanel, "form");
		} else if ("Binary".equals(label)) {
			bodyCards.show(bodyPanel, "binary");
		} else {
			bodyCards.show(bodyPanel, "raw");
		}
	}

	private String resolvePayloadLabel(Object value) {
		String normalized = value == null ? "" : String.valueOf(value).trim();
		if (normalized.equalsIgnoreCase("FORM_DATA") || normalized.equalsIgnoreCase("Form Data")) {
			return "Form Data";
		}
		if (normalized.equalsIgnoreCase("BINARY") || normalized.equalsIgnoreCase("Binary")) {
			return "Binary";
		}
		return "Raw";
	}

	private String resolvePayloadValue(Object value) {
		String label = resolvePayloadLabel(value);
		if ("Form Data".equals(label)) {
			return "FORM_DATA";
		}
		if ("Binary".equals(label)) {
			return "BINARY";
		}
		return "RAW";
	}

	private HttpPayloadType resolvePayloadType(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "FORM_DATA" -> HttpPayloadType.FORM_DATA;
			case "BINARY" -> HttpPayloadType.BINARY;
			default -> HttpPayloadType.RAW;
		};
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
		configureComboPopupWidth(grpcServiceCombo);
		configureComboPopupWidth(grpcMethodCombo);
	}

	private void configureComboPopupWidth(JComboBox<String> comboBox) {
		comboBox.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				int width = calculatePopupWidth(comboBox, resolveGrpcPopupMaxWidth());
				Object popup = comboBox.getUI().getAccessibleChild(comboBox, 0);
				if (popup instanceof BasicComboPopup basicPopup) {
					JList<?> list = basicPopup.getList();
					list.setFixedCellWidth(width);
					setPrototypeValue(list, findLongestValue(comboBox));
					Dimension size = basicPopup.getPreferredSize();
					size.width = width;
					basicPopup.setPreferredSize(size);
					basicPopup.setSize(size);
					Component component = basicPopup.getComponent(0);
					if (component instanceof JScrollPane scrollPane) {
						scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
						Dimension scrollSize = scrollPane.getPreferredSize();
						scrollSize.width = width;
						scrollPane.setPreferredSize(scrollSize);
						scrollPane.setSize(scrollSize);
					}
				}
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}
		});
	}

	private void setPrototypeValue(
		JList<?> list,
		String value
	) {
		@SuppressWarnings("unchecked")
		JList<Object> typed = (JList<Object>) list;
		typed.setPrototypeCellValue(value);
	}

	private int calculatePopupWidth(
		JComboBox<String> comboBox,
		int maxWidth
	) {
		ListCellRenderer<? super String> renderer = comboBox.getRenderer();
		if (renderer == null) {
			renderer = new DefaultListCellRenderer();
		}
		JList<String> list = new JList<>();
		list.setFont(comboBox.getFont());
		FontMetrics metrics = list.getFontMetrics(list.getFont());
		int width = 0;
		ComboBoxModel<String> model = comboBox.getModel();
		for (int i = 0; i < model.getSize(); i++) {
			String value = model.getElementAt(i);
			int textWidth = value == null ? 0 : metrics.stringWidth(value);
			Component component = renderer.getListCellRendererComponent(list, value, i, false, false);
			int preferredWidth = component.getPreferredSize().width;
			width = Math.max(width, Math.max(textWidth, preferredWidth));
		}
		Insets insets = list.getInsets();
		width += (insets.left + insets.right + 12);
		if (width < 240) {
			width = 240;
		}
		if (maxWidth > 0) {
			width = Math.min(width, maxWidth);
		}
		return width;
	}

	private String findLongestValue(JComboBox<String> comboBox) {
		ComboBoxModel<String> model = comboBox.getModel();
		String longest = "";
		for (int i = 0; i < model.getSize(); i++) {
			String value = model.getElementAt(i);
			if (value != null && value.length() > longest.length()) {
				longest = value;
			}
		}
		return longest;
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
		int width = root != null ? root.getWidth() : 0;
		int max = width > 0 ? (int) (width * 0.9) : 900;
		if (max < 500) {
			max = 500;
		}
		if (max > 1200) {
			max = 1200;
		}
		return max;
	}

	private static final class HeaderNameCellEditor extends AbstractCellEditor implements TableCellEditor {

		private final TextFieldWithAutoCompletion<String> field;

		private HeaderNameCellEditor(
			Project project,
			List<String> variants
		) {
			this.field = TextFieldWithAutoCompletion.create(project, variants, true, "");
		}

		@Override
		public Object getCellEditorValue() {
			return field.getText();
		}

		@Override
		public Component getTableCellEditorComponent(
			JTable table,
			Object value,
			boolean isSelected,
			int row,
			int column
		) {
			field.setText(value == null ? "" : String.valueOf(value));
			return field;
		}
	}

	private class HeaderValueCellEditor extends AbstractCellEditor implements TableCellEditor {

		private final JComboBox<String> combo = new JComboBox<>();

		private HeaderValueCellEditor() {
			combo.setEditable(true);
		}

		@Override
		public Object getCellEditorValue() {
			Object value = combo.getEditor().getItem();
			return value == null ? "" : String.valueOf(value);
		}

		@Override
		public Component getTableCellEditorComponent(
			JTable table,
			Object value,
			boolean isSelected,
			int row,
			int column
		) {
			String current = value == null ? "" : String.valueOf(value);
			combo.removeAllItems();
			String headerName = "";
			Object nameValue = table.getValueAt(row, 1);
			if (nameValue != null) {
				headerName = String.valueOf(nameValue);
			}
			Map<String, List<String>> presetMap = buildHeaderPresetMap();
			List<String> values =
				presetMap.getOrDefault(headerName.trim().toLowerCase(Locale.ROOT), List.of());
			if (!values.isEmpty()) {
				for (String item : values) {
					combo.addItem(item);
				}
			}
			combo.setSelectedItem(current);
			return combo;
		}
	}

	private void handleTreeSelection(TreeSelectionEvent event) {
		stopTableEditing();
		saveCurrentEditors();
		Object selected = tree.getLastSelectedPathComponent();
		if (!(selected instanceof DefaultMutableTreeNode)) {
			currentNode = null;
			editorCards.show(editorPanel, "empty");
			return;
		}
		DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) selected;
		Object userObject = treeNode.getUserObject();
		if (!(userObject instanceof NodeState)) {
			currentNode = null;
			editorCards.show(editorPanel, "empty");
			openRequestWindowButton.setEnabled(false);
			openResponseWindowButton.setEnabled(false);
			openChainWindowButton.setEnabled(false);
			return;
		}
		currentNode = (NodeState) userObject;
		if (currentNode.type == NodeType.FOLDER) {
			editorCards.show(editorPanel, "empty");
			openRequestWindowButton.setEnabled(false);
			openResponseWindowButton.setEnabled(false);
			openChainWindowButton.setEnabled(false);
			return;
		}
		if (currentNode.requestType == RequestType.HTTP) {
			loadHttp(currentNode.id);
			requestTopCards.show(requestTopPanel, "http");
			editorCards.show(editorPanel, "request");
			openRequestWindowButton.setEnabled(true);
			openResponseWindowButton.setEnabled(true);
			openChainWindowButton.setEnabled(false);
		} else if (currentNode.requestType == RequestType.GRPC) {
			loadGrpc(currentNode.id);
			requestTopCards.show(requestTopPanel, "grpc");
			editorCards.show(editorPanel, "request");
			openRequestWindowButton.setEnabled(true);
			openResponseWindowButton.setEnabled(true);
			openChainWindowButton.setEnabled(false);
		} else if (currentNode.requestType == RequestType.CHAIN) {
			loadChain(currentNode.id);
			editorCards.show(editorPanel, "chain");
			openRequestWindowButton.setEnabled(false);
			openResponseWindowButton.setEnabled(false);
			openChainWindowButton.setEnabled(true);
		}
	}

	private void reloadTree() {
		reloadTree(null);
	}

	private void reloadTree(String focusNodeId) {
		Set<String> expandedIds = captureExpandedNodeIds();
		String selectedId = captureSelectedNodeId();
		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Requests");
		Map<String, List<NodeState>> byParent = new HashMap<>();
		for (NodeState node : stateService.getNodes()) {
			byParent.computeIfAbsent(node.parentId, key -> new ArrayList<>()).add(node);
		}
		for (List<NodeState> nodes : byParent.values()) {
			nodes.sort(Comparator.comparingInt(a -> a.order));
		}
		buildTreeChildren(rootNode, null, byParent);
		treeModel.setRoot(rootNode);
		treeModel.reload();
		tree.expandRow(0);
		restoreExpandedNodeIds(expandedIds);
		String targetId = focusNodeId != null ? focusNodeId : selectedId;
		if (targetId != null) {
			selectNode(targetId);
		}
		refreshChainRequestsCombo();
	}

	private void selectNode(String nodeId) {
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) treeModel.getRoot();
		DefaultMutableTreeNode node = findTreeNodeById(rootNode, nodeId);
		if (node == null) {
			return;
		}
		TreePath path = new TreePath(node.getPath());
		tree.expandPath(path);
		tree.setSelectionPath(path);
		tree.scrollPathToVisible(path);
	}

	private DefaultMutableTreeNode findTreeNodeById(
		DefaultMutableTreeNode rootNode,
		String nodeId
	) {
		if (rootNode.getUserObject() instanceof NodeState state && Objects.equals(state.id, nodeId)) {
			return rootNode;
		}
		Enumeration<?> children = rootNode.children();
		while (children.hasMoreElements()) {
			Object child = children.nextElement();
			if (child instanceof DefaultMutableTreeNode childNode) {
				DefaultMutableTreeNode found = findTreeNodeById(childNode, nodeId);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private void buildTreeChildren(
		DefaultMutableTreeNode parent,
		String parentId,
		Map<String, List<NodeState>> byParent
	) {
		List<NodeState> nodes = byParent.getOrDefault(parentId, List.of());
		for (NodeState node : nodes) {
			DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
			parent.add(treeNode);
			if (node.type == NodeType.FOLDER) {
				buildTreeChildren(treeNode, node.id, byParent);
			}
		}
	}

	private void createFolder() {
		String name = JOptionPane.showInputDialog(root, "Folder name:");
		if (name == null || name.isBlank()) {
			return;
		}
		String parentId = selectedFolderId();
		stateService.createFolder(name, parentId);
		reloadTree();
	}

	private void createRequest() {
		String name = JOptionPane.showInputDialog(root, "Request name:");
		if (name == null || name.isBlank()) {
			return;
		}
		RequestType type = (RequestType) JOptionPane.showInputDialog(
			root,
			"Request type:",
			"New Request",
			JOptionPane.PLAIN_MESSAGE,
			null,
			RequestType.values(),
			RequestType.HTTP
		);
		if (type == null) {
			return;
		}
		String parentId = selectedFolderId();
		NodeState created = stateService.createRequest(name, type, parentId);
		reloadTree(created.id);
	}

	private Set<String> captureExpandedNodeIds() {
		Set<String> expandedIds = new HashSet<>();
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) treeModel.getRoot();
		if (rootNode == null) {
			return expandedIds;
		}
		TreePath rootPath = new TreePath(rootNode.getPath());
		Enumeration<TreePath> expanded = tree.getExpandedDescendants(rootPath);
		if (expanded == null) {
			return expandedIds;
		}
		while (expanded.hasMoreElements()) {
			TreePath path = expanded.nextElement();
			Object last = path.getLastPathComponent();
			if (last instanceof DefaultMutableTreeNode treeNode) {
				Object userObject = treeNode.getUserObject();
				if (userObject instanceof NodeState node) {
					expandedIds.add(node.id);
				}
			}
		}
		return expandedIds;
	}

	private String captureSelectedNodeId() {
		Object selected = tree.getLastSelectedPathComponent();
		if (!(selected instanceof DefaultMutableTreeNode treeNode)) {
			return null;
		}
		Object userObject = treeNode.getUserObject();
		if (!(userObject instanceof NodeState node)) {
			return null;
		}
		return node.id;
	}

	private void restoreExpandedNodeIds(Set<String> expandedIds) {
		if (expandedIds == null || expandedIds.isEmpty()) {
			return;
		}
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) treeModel.getRoot();
		for (String nodeId : expandedIds) {
			DefaultMutableTreeNode treeNode = findTreeNodeById(rootNode, nodeId);
			if (treeNode == null) {
				continue;
			}
			TreePath path = new TreePath(treeNode.getPath());
			tree.expandPath(path);
		}
	}

	private void deleteSelected() {
		if (currentNode == null) {
			return;
		}
		int confirm = JOptionPane.showConfirmDialog(root,
													"Delete \"" + currentNode.name + "\"?",
													"Confirm",
													JOptionPane.OK_CANCEL_OPTION
		);
		if (confirm != JOptionPane.OK_OPTION) {
			return;
		}
		stateService.deleteNode(currentNode.id);
		currentNode = null;
		editorCards.show(editorPanel, "empty");
		reloadTree();
	}

	private void importCollections() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Import Intelli Webrunner collections or .http file");
		FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("Webrunner JSON collections", "json");
		FileNameExtensionFilter httpFilter = new FileNameExtensionFilter("IntelliJ HTTP files", "http");
		chooser.addChoosableFileFilter(jsonFilter);
		chooser.addChoosableFileFilter(httpFilter);
		chooser.setFileFilter(jsonFilter);
		int result = chooser.showOpenDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = chooser.getSelectedFile();
		if (selectedFile != null && hasHttpExtension(selectedFile)) {
			importHttpFile(selectedFile);
			return;
		}
		try {
			WebrunnerState imported = mapper.readValue(selectedFile, WebrunnerState.class);
			Object[] options = new Object[] {"Merge", "Replace", "Cancel"};
			int choice = JOptionPane.showOptionDialog(
				root,
				"Import mode:",
				"Import Collections",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[0]
			);
			if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
				return;
			}
			if (choice == 0) {
				stateService.mergeState(imported);
			} else {
				stateService.replaceState(imported);
			}
			currentNode = null;
			editorCards.show(editorPanel, "empty");
			reloadTree();
		} catch (Exception error) {
			JOptionPane.showMessageDialog(
				root,
				"Failed to import: " + error.getMessage(),
				"Import error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void exportCollections() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export Intelli Webrunner collections or .http file");
		FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("Webrunner JSON collections", "json");
		FileNameExtensionFilter httpFilter = new FileNameExtensionFilter("IntelliJ HTTP files", "http");
		chooser.addChoosableFileFilter(jsonFilter);
		chooser.addChoosableFileFilter(httpFilter);
		if (currentNode != null && currentNode.requestType == RequestType.HTTP) {
			chooser.setFileFilter(httpFilter);
			chooser.setSelectedFile(new File(safeFileName(currentNode.name) + ".http"));
		} else {
			chooser.setFileFilter(jsonFilter);
			chooser.setSelectedFile(new File("intelli-webrunner.json"));
		}
		int result = chooser.showSaveDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			File selectedFile = chooser.getSelectedFile();
			boolean exportHttp = (chooser.getFileFilter() == httpFilter) || hasHttpExtension(selectedFile);
			if (exportHttp) {
				exportHttpRequest(ensureExtension(selectedFile, "http"));
				return;
			}
			mapper.writerWithDefaultPrettyPrinter()
				.writeValue(ensureExtension(selectedFile, "json"), stateService.exportState());
		} catch (Exception error) {
			JOptionPane.showMessageDialog(
				root,
				"Failed to export: " + error.getMessage(),
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void importCollectionsJson() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Import Intelli Webrunner collections");
		FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("Webrunner JSON collections", "json");
		chooser.addChoosableFileFilter(jsonFilter);
		chooser.setFileFilter(jsonFilter);
		int result = chooser.showOpenDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = chooser.getSelectedFile();
		try {
			WebrunnerState imported = mapper.readValue(selectedFile, WebrunnerState.class);
			Object[] options = new Object[] {"Merge", "Replace", "Cancel"};
			int choice = JOptionPane.showOptionDialog(
				root,
				"Import mode:",
				"Import Collections",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[0]
			);
			if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
				return;
			}
			if (choice == 0) {
				stateService.mergeState(imported);
			} else {
				stateService.replaceState(imported);
			}
			currentNode = null;
			editorCards.show(editorPanel, "empty");
			reloadTree();
		} catch (Exception error) {
			JOptionPane.showMessageDialog(
				root,
				"Failed to import: " + error.getMessage(),
				"Import error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void exportCollectionsJson() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export Intelli Webrunner collections");
		FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("Webrunner JSON collections", "json");
		chooser.addChoosableFileFilter(jsonFilter);
		chooser.setFileFilter(jsonFilter);
		chooser.setSelectedFile(new File("intelli-webrunner.json"));
		int result = chooser.showSaveDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			File selectedFile = chooser.getSelectedFile();
			mapper.writerWithDefaultPrettyPrinter()
				.writeValue(ensureExtension(selectedFile, "json"), stateService.exportState());
		} catch (Exception error) {
			JOptionPane.showMessageDialog(
				root,
				"Failed to export: " + error.getMessage(),
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void importHttpFromChooser() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Import IntelliJ .http file");
		FileNameExtensionFilter httpFilter = new FileNameExtensionFilter("IntelliJ HTTP files", "http");
		chooser.addChoosableFileFilter(httpFilter);
		chooser.setFileFilter(httpFilter);
		int result = chooser.showOpenDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = chooser.getSelectedFile();
		if (selectedFile == null) {
			return;
		}
		importHttpFile(selectedFile);
	}

	private void exportHttpFromChooser() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export IntelliJ .http file");
		FileNameExtensionFilter httpFilter = new FileNameExtensionFilter("IntelliJ HTTP files", "http");
		chooser.addChoosableFileFilter(httpFilter);
		chooser.setFileFilter(httpFilter);
		if (currentNode != null && currentNode.requestType == RequestType.HTTP) {
			chooser.setSelectedFile(new File(safeFileName(currentNode.name) + ".http"));
		} else {
			chooser.setSelectedFile(new File("request.http"));
		}
		int result = chooser.showSaveDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = ensureExtension(chooser.getSelectedFile(), "http");
		exportHttpRequest(selectedFile);
	}

	private void importHttpFromTree() {
		TreeFolderSelection selection = getTreeFolderSelection();
		if (selection == null) {
			JOptionPane.showMessageDialog(
				root,
				"Select root or a folder to import into.",
				"Import error",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Import IntelliJ .http file");
		FileNameExtensionFilter httpFilter = new FileNameExtensionFilter("IntelliJ HTTP files", "http");
		chooser.addChoosableFileFilter(httpFilter);
		chooser.setFileFilter(httpFilter);
		int result = chooser.showOpenDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = chooser.getSelectedFile();
		if (selectedFile == null) {
			return;
		}
		importHttpFile(selectedFile, selection.folderId);
	}

	private void exportHttpFromTree() {
		TreeFolderSelection selection = getTreeFolderSelection();
		if (selection == null) {
			JOptionPane.showMessageDialog(
				root,
				"Select root or a folder to export.",
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}
		List<NodeState> requests = collectHttpRequestsInSubtree(selection.folderId);
		if (requests.isEmpty()) {
			JOptionPane.showMessageDialog(
				root,
				"No HTTP requests found to export.",
				"Export",
				JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export IntelliJ .http file");
		FileNameExtensionFilter httpFilter = new FileNameExtensionFilter("IntelliJ HTTP files", "http");
		chooser.addChoosableFileFilter(httpFilter);
		chooser.setFileFilter(httpFilter);
		String baseName =
			selection.displayName == null || selection.displayName.isBlank() ? "requests" : selection.displayName;
		chooser.setSelectedFile(new File(safeFileName(baseName) + ".http"));
		int result = chooser.showSaveDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = ensureExtension(chooser.getSelectedFile(), "http");
		exportHttpRequests(selectedFile, requests);
	}

	private void importOpenApiFromTree() {
		TreeFolderSelection selection = getTreeFolderSelection();
		if (selection == null) {
			JOptionPane.showMessageDialog(
				root,
				"Select root or a folder to import into.",
				"Import error",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Import OpenAPI (JSON)");
		FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("OpenAPI JSON", "json");
		chooser.addChoosableFileFilter(jsonFilter);
		chooser.setFileFilter(jsonFilter);
		int result = chooser.showOpenDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = chooser.getSelectedFile();
		if (selectedFile == null) {
			return;
		}
		importOpenApiFile(selectedFile, selection.folderId);
	}

	private void exportOpenApiFromTree() {
		TreeFolderSelection selection = getTreeFolderSelection();
		if (selection == null) {
			JOptionPane.showMessageDialog(
				root,
				"Select root or a folder to export.",
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}
		List<NodeState> requests = collectHttpRequestsInSubtree(selection.folderId);
		if (requests.isEmpty()) {
			JOptionPane.showMessageDialog(
				root,
				"No HTTP requests found to export.",
				"Export",
				JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export OpenAPI (JSON)");
		FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("OpenAPI JSON", "json");
		chooser.addChoosableFileFilter(jsonFilter);
		chooser.setFileFilter(jsonFilter);
		String baseName =
			selection.displayName == null || selection.displayName.isBlank() ? "openapi" : selection.displayName;
		chooser.setSelectedFile(new File(safeFileName(baseName) + "-openapi.json"));
		int result = chooser.showSaveDialog(root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = ensureExtension(chooser.getSelectedFile(), "json");
		exportOpenApiFile(selectedFile, requests, selection.displayName);
	}

	private void importHttpFile(File file) {
		importHttpFile(file, selectedFolderId());
	}

	private void importHttpFile(
		File file,
		String parentId
	) {
		try {
			List<HttpFileRequest> requests = parseHttpFile(file);
			if (requests.isEmpty()) {
				JOptionPane.showMessageDialog(
					root,
					"No HTTP requests found in file.",
					"Import",
					JOptionPane.INFORMATION_MESSAGE
				);
				return;
			}
			for (HttpFileRequest request : requests) {
				if (request == null || request.method == null || request.url == null) {
					continue;
				}
				String name = (request.name == null || request.name.isBlank())
					? request.method + " " + request.url
					: request.name;
				NodeState node = stateService.createRequest(name, RequestType.HTTP, parentId);
				RequestDetailsState details = stateService.getRequestDetails(node.id);
				if (details == null) {
					details = new RequestDetailsState();
					details.requestId = node.id;
				}
				details.type = RequestType.HTTP;
				details.method = request.method;
				details.url = request.url;
				details.payloadType = "RAW";
				stateService.saveRequestDetails(details);

				RequestStatusState status = stateService.getRequestStatus(node.id);
				if (status == null) {
					status = new RequestStatusState();
					status.requestId = node.id;
				}
				status.requestBody = request.body == null ? "" : request.body;
				status.requestHeaders = request.headers == null ? new ArrayList<>() : new ArrayList<>(request.headers);
				status.requestParams = parseQueryParams(details.url);
				status.responseBody = "";
				status.responseHeaders = "";
				status.logs = "";
				status.beforeScript = "";
				status.afterScript = "";
				stateService.saveRequestStatus(status);
			}
			currentNode = null;
			editorCards.show(editorPanel, "empty");
			reloadTree();
		} catch (Exception error) {
			JOptionPane.showMessageDialog(
				root,
				"Failed to import .http: " + error.getMessage(),
				"Import error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void exportHttpRequest(File file) {
		if (currentNode == null || currentNode.requestType != RequestType.HTTP) {
			JOptionPane.showMessageDialog(
				root,
				"Select an HTTP request to export.",
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}
		saveCurrentEditors();
		RequestDetailsState details = stateService.getRequestDetails(currentNode.id);
		RequestStatusState status = stateService.getRequestStatus(currentNode.id);
		if (details == null || details.url == null || details.url.isBlank()) {
			JOptionPane.showMessageDialog(root, "Missing request URL.", "Export error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		String method = details.method == null ? "GET" : details.method;
		String url = applyQueryParams(details.url, status != null ? status.requestParams : List.of());
		String body = status != null ? safe(status.requestBody) : "";
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();

		StringBuilder builder = new StringBuilder();
		String name = safe(currentNode.name);
		if (!name.isBlank()) {
			builder.append("### ").append(name).append("\n");
		}
		builder.append(method).append(" ").append(url).append("\n");
		if (headers != null) {
			for (HeaderEntryState header : headers) {
				if (header == null || !header.enabled) {
					continue;
				}
				String headerName = header.name == null ? "" : header.name.trim();
				if (headerName.isEmpty()) {
					continue;
				}
				String headerValue = header.value == null ? "" : header.value;
				builder.append(headerName).append(": ").append(headerValue).append("\n");
			}
		}
		if (body != null && !body.isBlank()) {
			builder.append("\n");
			builder.append(body);
			if (!body.endsWith("\n")) {
				builder.append("\n");
			}
		}
		try {
			Files.writeString(file.toPath(), builder.toString(), StandardCharsets.UTF_8);
		} catch (Exception error) {
			JOptionPane.showMessageDialog(
				root,
				"Failed to export .http: " + error.getMessage(),
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void exportHttpRequests(
		File file,
		List<NodeState> requests
	) {
		if (requests == null || requests.isEmpty()) {
			return;
		}
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (NodeState node : requests) {
			if (node == null || node.requestType != RequestType.HTTP) {
				continue;
			}
			if (!first) {
				builder.append("\n");
			}
			builder.append(buildHttpBlock(node));
			first = false;
		}
		try {
			Files.writeString(file.toPath(), builder.toString(), StandardCharsets.UTF_8);
		} catch (Exception error) {
			JOptionPane.showMessageDialog(
				root,
				"Failed to export .http: " + error.getMessage(),
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private String buildHttpBlock(NodeState node) {
		RequestDetailsState details = stateService.getRequestDetails(node.id);
		RequestStatusState status = stateService.getRequestStatus(node.id);
		if (details == null || details.url == null || details.url.isBlank()) {
			return "";
		}
		String method = details.method == null ? "GET" : details.method;
		String url = applyQueryParams(details.url, status != null ? status.requestParams : List.of());
		String body = status != null ? safe(status.requestBody) : "";
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();

		StringBuilder builder = new StringBuilder();
		String name = safe(node.name);
		if (!name.isBlank()) {
			builder.append("### ").append(name).append("\n");
		}
		builder.append(method).append(" ").append(url).append("\n");
		if (headers != null) {
			for (HeaderEntryState header : headers) {
				if (header == null || !header.enabled) {
					continue;
				}
				String headerName = header.name == null ? "" : header.name.trim();
				if (headerName.isEmpty()) {
					continue;
				}
				String headerValue = header.value == null ? "" : header.value;
				builder.append(headerName).append(": ").append(headerValue).append("\n");
			}
		}
		if (body != null && !body.isBlank()) {
			builder.append("\n");
			builder.append(body);
			if (!body.endsWith("\n")) {
				builder.append("\n");
			}
		}
		return builder.toString();
	}

	private List<HttpFileRequest> parseHttpFile(File file) throws Exception {
		List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		List<HttpFileRequest> requests = new ArrayList<>();
		List<String> block = new ArrayList<>();
		String pendingName = null;
		boolean hadDelimiter = false;
		for (String line : lines) {
			String trimmed = line == null ? "" : line.trim();
			if (trimmed.startsWith("###")) {
				hadDelimiter = true;
				if (!block.isEmpty()) {
					HttpFileRequest parsed = parseHttpBlock(pendingName, block);
					if (parsed != null) {
						requests.add(parsed);
					}
					block.clear();
				}
				String name = trimmed.substring(3).trim();
				pendingName = name.isEmpty() ? null : name;
				continue;
			}
			block.add(line);
		}
		if (!block.isEmpty() || !hadDelimiter) {
			HttpFileRequest parsed = parseHttpBlock(pendingName, block);
			if (parsed != null) {
				requests.add(parsed);
			}
		}
		return requests;
	}

	private HttpFileRequest parseHttpBlock(
		String name,
		List<String> lines
	) {
		int index = 0;
		while (index < lines.size()) {
			String line = lines.get(index);
			if (line == null || line.trim().isEmpty() || isHttpComment(line)) {
				index++;
				continue;
			}
			break;
		}
		if (index >= lines.size()) {
			return null;
		}
		String requestLine = lines.get(index).trim();
		String[] parts = requestLine.split("\\s+");
		if (parts.length < 2) {
			return null;
		}
		String method = parts[0].trim();
		String url = parts[1].trim();
		index++;
		List<HeaderEntryState> headers = new ArrayList<>();
		while (index < lines.size()) {
			String line = lines.get(index);
			if (line == null || line.trim().isEmpty()) {
				index++;
				break;
			}
			if (isHttpComment(line)) {
				index++;
				continue;
			}
			int colon = line.indexOf(':');
			if (colon > 0) {
				String headerName = line.substring(0, colon).trim();
				String headerValue = line.substring(colon + 1).trim();
				if (!headerName.isEmpty()) {
					HeaderEntryState header = new HeaderEntryState();
					header.id = UUID.randomUUID().toString();
					header.name = headerName;
					header.value = headerValue;
					header.enabled = true;
					headers.add(header);
				}
			}
			index++;
		}
		String body = "";
		if (index < lines.size()) {
			body = String.join("\n", lines.subList(index, lines.size()));
		}
		HttpFileRequest request = new HttpFileRequest();
		request.name = name;
		request.method = method;
		request.url = url;
		request.headers = headers;
		request.body = body;
		return request;
	}

	private boolean isHttpComment(String line) {
		String trimmed = line == null ? "" : line.trim();
		return trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("@");
	}

	private boolean hasHttpExtension(File file) {
		if (file == null) {
			return false;
		}
		return file.getName().toLowerCase(Locale.ROOT).endsWith(".http");
	}

	private File ensureExtension(
		File file,
		String extension
	) {
		if (file == null) {
			return null;
		}
		String name = file.getName();
		String suffix = "." + extension;
		if (name.toLowerCase(Locale.ROOT).endsWith(suffix)) {
			return file;
		}
		File parent = file.getParentFile();
		String fixed = name + suffix;
		return parent == null ? new File(fixed) : new File(parent, fixed);
	}

	private String safeFileName(String value) {
		String input = value == null ? "request" : value.trim();
		if (input.isEmpty()) {
			input = "request";
		}
		String sanitized = input.replaceAll("[\\\\/:*?\"<>|]", "_");
		return sanitized.isEmpty() ? "request" : sanitized;
	}

	private TreeFolderSelection getTreeFolderSelection() {
		Object selected = tree.getLastSelectedPathComponent();
		if (!(selected instanceof DefaultMutableTreeNode treeNode)) {
			return null;
		}
		Object userObject = treeNode.getUserObject();
		if (userObject instanceof NodeState node) {
			if (node.type == NodeType.FOLDER) {
				return new TreeFolderSelection(node.id, node.name);
			}
			return null;
		}
		return new TreeFolderSelection(null, "Requests");
	}

	private List<NodeState> collectHttpRequestsInSubtree(String folderId) {
		WebrunnerState state = stateService.exportState();
		Map<String, NodeState> nodeById = new HashMap<>();
		Map<String, List<NodeState>> children = new HashMap<>();
		for (NodeState node : state.nodes) {
			nodeById.put(node.id, node);
			children.computeIfAbsent(node.parentId, key -> new ArrayList<>()).add(node);
		}
		for (List<NodeState> list : children.values()) {
			list.sort(Comparator.comparingInt(a -> a.order));
		}
		List<NodeState> result = new ArrayList<>();
		Deque<NodeState> stack = new ArrayDeque<>();
		List<NodeState> roots = children.getOrDefault(folderId, List.of());
		for (int i = roots.size() - 1; i >= 0; i--) {
			stack.push(roots.get(i));
		}
		while (!stack.isEmpty()) {
			NodeState node = stack.pop();
			if (node.type == NodeType.REQUEST && node.requestType == RequestType.HTTP) {
				result.add(node);
			}
			List<NodeState> kids = children.getOrDefault(node.id, List.of());
			for (int i = kids.size() - 1; i >= 0; i--) {
				stack.push(kids.get(i));
			}
		}
		return result;
	}

	private void exportOpenApiFile(
		File file,
		List<NodeState> requests,
		String title
	) {
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("openapi", "3.0.3");
		Map<String, Object> info = new LinkedHashMap<>();
		info.put("title", title == null || title.isBlank() ? "Webrunner Export" : title);
		info.put("version", "1.0.0");
		doc.put("info", info);

		Map<String, Object> paths = new LinkedHashMap<>();
		Map<String, NodeState> nodeById = new HashMap<>();
		for (NodeState node : stateService.exportState().nodes) {
			nodeById.put(node.id, node);
		}

		for (NodeState node : requests) {
			if (node == null) {
				continue;
			}
			RequestDetailsState details = stateService.getRequestDetails(node.id);
			RequestStatusState status = stateService.getRequestStatus(node.id);
			if (details == null || details.url == null || details.url.isBlank()) {
				continue;
			}
			String method = details.method == null ? "get" : details.method.toLowerCase(Locale.ROOT);
			ParsedUrl parsed = parseUrl(details.url);
			String path = parsed.path;
			Map<String, Object> pathItem =
				(Map<String, Object>) paths.computeIfAbsent(path, key -> new LinkedHashMap<>());

			Map<String, Object> operation = new LinkedHashMap<>();
			operation.put("summary", safe(node.name));
			operation.put("operationId", buildOperationId(node));
			operation.put("responses", Map.of("200", Map.of("description", "OK")));
			if (parsed.serverUrl != null) {
				operation.put("servers", List.of(Map.of("url", parsed.serverUrl)));
			}

			List<String> tags = buildFolderTags(node, nodeById);
			if (!tags.isEmpty()) {
				operation.put("tags", tags);
			}

			List<Map<String, Object>> parameters = buildOpenApiParameters(status, details);
			if (!parameters.isEmpty()) {
				operation.put("parameters", parameters);
			}

			Object requestBody = buildOpenApiRequestBody(status);
			if (requestBody != null) {
				operation.put("requestBody", requestBody);
			}

			Map<String, Object> vendor = buildVendorExtension(node, details, status);
			operation.put("x-webrunner", vendor);

			pathItem.put(method, operation);
		}

		doc.put("paths", paths);
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(file, doc);
		} catch (Exception error) {
			JOptionPane.showMessageDialog(
				root,
				"Failed to export OpenAPI: " + error.getMessage(),
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void importOpenApiFile(
		File file,
		String parentId
	) {
		try {
			Map<String, Object> doc = mapper.readValue(file, Map.class);
			Object openapi = doc.get("openapi");
			if (openapi == null) {
				JOptionPane.showMessageDialog(
					root,
					"Invalid OpenAPI file (missing 'openapi').",
					"Import error",
					JOptionPane.ERROR_MESSAGE
				);
				return;
			}
			Object pathsObj = doc.get("paths");
			if (!(pathsObj instanceof Map<?, ?> paths)) {
				JOptionPane.showMessageDialog(
					root,
					"OpenAPI file has no paths.",
					"Import",
					JOptionPane.INFORMATION_MESSAGE
				);
				return;
			}
			int created = 0;
			for (Map.Entry<?, ?> entry : paths.entrySet()) {
				String path = String.valueOf(entry.getKey());
				Object pathItemObj = entry.getValue();
				if (!(pathItemObj instanceof Map<?, ?> pathItem)) {
					continue;
				}
				for (Map.Entry<?, ?> opEntry : pathItem.entrySet()) {
					String method = String.valueOf(opEntry.getKey()).toUpperCase(Locale.ROOT);
					if (!isHttpMethod(method)) {
						continue;
					}
					Object operationObj = opEntry.getValue();
					if (!(operationObj instanceof Map<?, ?> operation)) {
						continue;
					}
					String url = resolveOperationUrl(doc, pathItem, operation, path);
					RequestData requestData = readVendorRequestData(operation, pathItem, method, url);
					NodeState node = stateService.createRequest(requestData.name, RequestType.HTTP, parentId);
					RequestDetailsState details = stateService.getRequestDetails(node.id);
					if (details == null) {
						details = new RequestDetailsState();
						details.requestId = node.id;
					}
					details.type = RequestType.HTTP;
					details.method = requestData.method;
					details.url = requestData.url;
					details.payloadType = "RAW";
					stateService.saveRequestDetails(details);

					RequestStatusState status = stateService.getRequestStatus(node.id);
					if (status == null) {
						status = new RequestStatusState();
						status.requestId = node.id;
					}
					status.requestBody = requestData.body == null ? "" : requestData.body;
					status.requestHeaders =
						requestData.headers == null ? new ArrayList<>() : new ArrayList<>(requestData.headers);
					status.requestParams =
						requestData.params == null ? new ArrayList<>() : new ArrayList<>(requestData.params);
					status.responseBody = "";
					status.responseHeaders = "";
					status.logs = "";
					status.beforeScript = requestData.beforeScript == null ? "" : requestData.beforeScript;
					status.afterScript = requestData.afterScript == null ? "" : requestData.afterScript;
					stateService.saveRequestStatus(status);
					created++;
				}
			}
			if (created == 0) {
				JOptionPane.showMessageDialog(
					root,
					"No HTTP operations found in OpenAPI file.",
					"Import",
					JOptionPane.INFORMATION_MESSAGE
				);
				return;
			}
			currentNode = null;
			editorCards.show(editorPanel, "empty");
			reloadTree();
		} catch (Exception error) {
			JOptionPane.showMessageDialog(
				root,
				"Failed to import OpenAPI: " + error.getMessage(),
				"Import error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private Map<String, Object> buildVendorExtension(
		NodeState node,
		RequestDetailsState details,
		RequestStatusState status
	) {
		Map<String, Object> vendor = new LinkedHashMap<>();
		vendor.put("id", node.id);
		vendor.put("name", safe(node.name));
		vendor.put("method", details.method == null ? "GET" : details.method);
		vendor.put("url", details.url == null ? "" : details.url);
		vendor.put("headers", headerEntriesToMaps(status != null ? status.requestHeaders : List.of()));
		vendor.put("params", headerEntriesToMaps(status != null ? status.requestParams : List.of()));
		vendor.put("body", status != null ? safe(status.requestBody) : "");
		vendor.put("beforeScript", status != null ? safe(status.beforeScript) : "");
		vendor.put("afterScript", status != null ? safe(status.afterScript) : "");
		return vendor;
	}

	private List<Map<String, Object>> headerEntriesToMaps(List<HeaderEntryState> entries) {
		List<Map<String, Object>> list = new ArrayList<>();
		if (entries == null) {
			return list;
		}
		for (HeaderEntryState entry : entries) {
			if (entry == null) {
				continue;
			}
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("name", entry.name == null ? "" : entry.name);
			map.put("value", entry.value == null ? "" : entry.value);
			map.put("enabled", entry.enabled);
			list.add(map);
		}
		return list;
	}

	private List<Map<String, Object>> buildOpenApiParameters(
		RequestStatusState status,
		RequestDetailsState details
	) {
		List<Map<String, Object>> params = new ArrayList<>();
		List<HeaderEntryState> query =
			status != null ? mergeParamsWithUrl(status.requestParams, details.url) : List.of();
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();
		for (HeaderEntryState entry : query) {
			if (entry == null || !entry.enabled) {
				continue;
			}
			String name = entry.name == null ? "" : entry.name.trim();
			if (name.isEmpty()) {
				continue;
			}
			Map<String, Object> param = new LinkedHashMap<>();
			param.put("name", name);
			param.put("in", "query");
			param.put("schema", Map.of("type", "string"));
			if (entry.value != null && !entry.value.isEmpty()) {
				param.put("example", entry.value);
			}
			params.add(param);
		}
		for (HeaderEntryState entry : headers) {
			if (entry == null || !entry.enabled) {
				continue;
			}
			String name = entry.name == null ? "" : entry.name.trim();
			if (name.isEmpty()) {
				continue;
			}
			Map<String, Object> param = new LinkedHashMap<>();
			param.put("name", name);
			param.put("in", "header");
			param.put("schema", Map.of("type", "string"));
			if (entry.value != null && !entry.value.isEmpty()) {
				param.put("example", entry.value);
			}
			params.add(param);
		}
		return params;
	}

	private Object buildOpenApiRequestBody(RequestStatusState status) {
		if (status == null) {
			return null;
		}
		String body = safe(status.requestBody);
		if (body.isBlank()) {
			return null;
		}
		Map<String, Object> content = new LinkedHashMap<>();
		Map<String, Object> media = new LinkedHashMap<>();
		media.put("example", body);
		String contentType = findHeaderValue(status.requestHeaders, "Content-Type");
		content.put(contentType == null || contentType.isBlank() ? "text/plain" : contentType, media);
		Map<String, Object> requestBody = new LinkedHashMap<>();
		requestBody.put("content", content);
		return requestBody;
	}

	private String findHeaderValue(
		List<HeaderEntryState> headers,
		String name
	) {
		if (headers == null || name == null) {
			return null;
		}
		for (HeaderEntryState header : headers) {
			if (header == null || !header.enabled || header.name == null) {
				continue;
			}
			if (name.equalsIgnoreCase(header.name.trim())) {
				return header.value == null ? "" : header.value.trim();
			}
		}
		return null;
	}

	private ParsedUrl parseUrl(String url) {
		String fallbackPath = "/";
		if (url == null || url.isBlank()) {
			return new ParsedUrl(fallbackPath, null);
		}
		try {
			java.net.URI uri = java.net.URI.create(url);
			String path = uri.getRawPath();
			if (path == null || path.isBlank()) {
				path = "/";
			}
			String server = null;
			if (uri.getScheme() != null && uri.getHost() != null) {
				StringBuilder builder = new StringBuilder();
				builder.append(uri.getScheme()).append("://").append(uri.getHost());
				if (uri.getPort() > 0) {
					builder.append(":").append(uri.getPort());
				}
				server = builder.toString();
			}
			return new ParsedUrl(path, server);
		} catch (Exception e) {
			return new ParsedUrl(fallbackPath, null);
		}
	}

	private String buildOperationId(NodeState node) {
		String base = safe(node.name).replaceAll("[^A-Za-z0-9_]", "_");
		if (base.isBlank()) {
			base = "operation";
		}
		return base + "_" + node.id.replaceAll("[^A-Za-z0-9_]", "");
	}

	private List<String> buildFolderTags(
		NodeState node,
		Map<String, NodeState> nodeById
	) {
		List<String> tags = new ArrayList<>();
		String parentId = node.parentId;
		while (parentId != null) {
			NodeState parent = nodeById.get(parentId);
			if (parent == null) {
				break;
			}
			if (parent.type == NodeType.FOLDER && parent.name != null && !parent.name.isBlank()) {
				tags.add(parent.name);
			}
			parentId = parent.parentId;
		}
		Collections.reverse(tags);
		return tags;
	}

	private boolean isHttpMethod(String method) {
		return method.equals("GET") || method.equals("POST") || method.equals("PUT")
			|| method.equals("PATCH") || method.equals("DELETE")
			|| method.equals("HEAD") || method.equals("OPTIONS");
	}

	private String resolveOperationUrl(
		Map<String, Object> doc,
		Map<?, ?> pathItem,
		Map<?, ?> operation,
		String path
	) {
		String baseUrl = firstServerUrl(operation);
		if (baseUrl == null) {
			baseUrl = firstServerUrl(pathItem);
		}
		if (baseUrl == null) {
			baseUrl = firstServerUrl(doc);
		}
		if (baseUrl == null || baseUrl.isBlank()) {
			return path.startsWith("/") ? path : "/" + path;
		}
		String normalizedPath = path.startsWith("/") ? path : "/" + path;
		if (baseUrl.endsWith("/")) {
			return baseUrl.substring(0, baseUrl.length() - 1) + normalizedPath;
		}
		return baseUrl + normalizedPath;
	}

	private String firstServerUrl(Map<?, ?> container) {
		if (container == null) {
			return null;
		}
		Object serversObj = container.get("servers");
		if (!(serversObj instanceof List<?> servers)) {
			return null;
		}
		if (servers.isEmpty()) {
			return null;
		}
		Object first = servers.get(0);
		if (!(first instanceof Map<?, ?> server)) {
			return null;
		}
		Object url = server.get("url");
		return url == null ? null : String.valueOf(url);
	}

	private RequestData readVendorRequestData(
		Map<?, ?> operation,
		Map<?, ?> pathItem,
		String method,
		String url
	) {
		RequestData data = new RequestData();
		data.method = method;
		data.url = url;
		data.name = method + " " + url;

		Object vendorObj = operation.get("x-webrunner");
		if (vendorObj instanceof Map<?, ?> vendor) {
			Object name = vendor.get("name");
			if (name != null && !String.valueOf(name).isBlank()) {
				data.name = String.valueOf(name);
			}
			Object vendorMethod = vendor.get("method");
			if (vendorMethod != null) {
				data.method = String.valueOf(vendorMethod).toUpperCase(Locale.ROOT);
			}
			Object vendorUrl = vendor.get("url");
			if (vendorUrl != null && !String.valueOf(vendorUrl).isBlank()) {
				data.url = String.valueOf(vendorUrl);
			}
			data.headers = parseHeaderEntries(vendor.get("headers"));
			data.params = parseHeaderEntries(vendor.get("params"));
			Object body = vendor.get("body");
			if (body != null) {
				data.body = String.valueOf(body);
			}
			Object before = vendor.get("beforeScript");
			if (before != null) {
				data.beforeScript = String.valueOf(before);
			}
			Object after = vendor.get("afterScript");
			if (after != null) {
				data.afterScript = String.valueOf(after);
			}
			return data;
		}

		data.headers = parseOpenApiParameters(pathItem, operation, "header");
		data.params = parseOpenApiParameters(pathItem, operation, "query");
		data.body = extractRequestBody(operation);
		Object summary = operation.get("summary");
		if (summary != null && !String.valueOf(summary).isBlank()) {
			data.name = String.valueOf(summary);
		}
		Object opId = operation.get("operationId");
		if (opId != null && !String.valueOf(opId).isBlank() && (summary == null || String.valueOf(summary).isBlank())) {
			data.name = String.valueOf(opId);
		}
		return data;
	}

	private List<HeaderEntryState> parseHeaderEntries(Object value) {
		List<HeaderEntryState> list = new ArrayList<>();
		if (!(value instanceof List<?> entries)) {
			return list;
		}
		for (Object entryObj : entries) {
			if (!(entryObj instanceof Map<?, ?> entry)) {
				continue;
			}
			Object name = entry.get("name");
			if (name == null || String.valueOf(name).isBlank()) {
				continue;
			}
			HeaderEntryState header = new HeaderEntryState();
			header.id = UUID.randomUUID().toString();
			header.name = String.valueOf(name);
			Object valueObj = entry.get("value");
			header.value = valueObj == null ? "" : String.valueOf(valueObj);
			Object enabledObj = entry.get("enabled");
			header.enabled = enabledObj == null || Boolean.parseBoolean(String.valueOf(enabledObj));
			list.add(header);
		}
		return list;
	}

	private List<HeaderEntryState> parseOpenApiParameters(
		Map<?, ?> pathItem,
		Map<?, ?> operation,
		String location
	) {
		List<HeaderEntryState> list = new ArrayList<>();
		List<Object> combined = new ArrayList<>();
		Object pathParamsObj = pathItem == null ? null : pathItem.get("parameters");
		if (pathParamsObj instanceof List<?> pathParams) {
			combined.addAll(pathParams);
		}
		Object opParamsObj = operation.get("parameters");
		if (opParamsObj instanceof List<?> opParams) {
			combined.addAll(opParams);
		}
		if (combined.isEmpty()) {
			return list;
		}
		for (Object paramObj : combined) {
			if (!(paramObj instanceof Map<?, ?> param)) {
				continue;
			}
			Object in = param.get("in");
			if (in == null || !location.equalsIgnoreCase(String.valueOf(in))) {
				continue;
			}
			Object nameObj = param.get("name");
			if (nameObj == null || String.valueOf(nameObj).isBlank()) {
				continue;
			}
			HeaderEntryState entry = new HeaderEntryState();
			entry.id = UUID.randomUUID().toString();
			entry.name = String.valueOf(nameObj);
			Object value = param.get("example");
			if (value == null) {
				value = extractFromSchema(param);
			}
			entry.value = value == null ? "" : stringifyExample(value);
			entry.enabled = true;
			list.add(entry);
		}
		return list;
	}

	private Object extractFromSchema(Map<?, ?> param) {
		Object schemaObj = param.get("schema");
		if (!(schemaObj instanceof Map<?, ?> schema)) {
			return param.get("default");
		}
		Object example = schema.get("example");
		if (example != null) {
			return example;
		}
		Object def = schema.get("default");
		if (def != null) {
			return def;
		}
		return param.get("default");
	}

	private String extractRequestBody(Map<?, ?> operation) {
		Object bodyObj = operation.get("requestBody");
		if (!(bodyObj instanceof Map<?, ?> body)) {
			return "";
		}
		Object contentObj = body.get("content");
		if (!(contentObj instanceof Map<?, ?> content)) {
			return "";
		}
		for (Object mediaObj : content.values()) {
			if (!(mediaObj instanceof Map<?, ?> media)) {
				continue;
			}
			Object example = media.get("example");
			if (example != null) {
				return stringifyExample(example);
			}
			Object examples = media.get("examples");
			if (examples instanceof Map<?, ?> examplesMap && !examplesMap.isEmpty()) {
				Object first = examplesMap.values().iterator().next();
				if (first instanceof Map<?, ?> exampleMap) {
					Object value = exampleMap.get("value");
					if (value != null) {
						return stringifyExample(value);
					}
				}
			}
		}
		return "";
	}

	private String stringifyExample(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof String text) {
			return text;
		}
		try {
			return mapper.writeValueAsString(value);
		} catch (Exception e) {
			return String.valueOf(value);
		}
	}

	private static class ParsedUrl {

		final String path;
		final String serverUrl;

		ParsedUrl(
			String path,
			String serverUrl
		) {
			this.path = path;
			this.serverUrl = serverUrl;
		}
	}

	private static class RequestData {

		String name;
		String method;
		String url;
		List<HeaderEntryState> headers = new ArrayList<>();
		List<HeaderEntryState> params = new ArrayList<>();
		String body = "";
		String beforeScript = "";
		String afterScript = "";
	}

	private static class HttpFileRequest {

		String name;
		String method;
		String url;
		List<HeaderEntryState> headers;
		String body;
	}

	private static class TreeFolderSelection {

		final String folderId;
		final String displayName;

		TreeFolderSelection(
			String folderId,
			String displayName
		) {
			this.folderId = folderId;
			this.displayName = displayName;
		}
	}

	private String selectedFolderId() {
		Object selected = tree.getLastSelectedPathComponent();
		if (!(selected instanceof DefaultMutableTreeNode)) {
			return null;
		}
		DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) selected;
		Object userObject = treeNode.getUserObject();
		if (userObject instanceof NodeState node && node.type == NodeType.FOLDER) {
			return node.id;
		}
		if (userObject instanceof NodeState node && node.type == NodeType.REQUEST) {
			return node.parentId;
		}
		return null;
	}

	private void loadHttp(String requestId) {
		isLoading = true;
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		RequestStatusState status = stateService.getRequestStatus(requestId);
		updateHeaderNameEditor(RequestType.HTTP);
		httpMethodCombo.setSelectedItem(details != null && details.method != null ? details.method : "GET");
		httpPayloadCombo.setSelectedItem(resolvePayloadLabel(details != null ? details.payloadType : null));
		httpUrlField.setText(details != null && details.url != null ? details.url : "");
		requestBodyArea.setText(status != null ? safe(status.requestBody) : "");
		formDataTableModel.setEntries(status != null ? status.formData : List.of());
		binaryFileField.setText(status != null ? safe(status.binaryFilePath) : "");
		beforeScriptArea.setText(status != null ? safe(status.beforeScript) : "");
		afterScriptArea.setText(status != null ? safe(status.afterScript) : "");
		headersTableModel.setHeaders(status != null ? status.requestHeaders : List.of(), true);
		List<HeaderEntryState> mergedParams =
			mergeParamsWithUrl(status != null ? status.requestParams : List.of(), details != null ? details.url : null);
		paramsTableModel.setHeaders(mergedParams, true);
		responseBodyArea.setText(status != null ? safe(status.responseBody) : "");
		responseHeadersArea.setText(status != null ? safe(status.responseHeaders) : "");
		responseLogsArea.setText(status != null ? safe(status.logs) : "");
		responseStatusLabel.setText("");
		switchPayloadType();
		isLoading = false;
	}

	private void loadGrpc(String requestId) {
		isLoading = true;
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		RequestStatusState status = stateService.getRequestStatus(requestId);
		updateHeaderNameEditor(RequestType.GRPC);
		grpcTargetField.setText(details != null ? safe(details.target) : "");
		requestBodyArea.setText(status != null ? safe(status.requestBody) : "");
		bodyCards.show(bodyPanel, "raw");
		beforeScriptArea.setText(status != null ? safe(status.beforeScript) : "");
		afterScriptArea.setText(status != null ? safe(status.afterScript) : "");
		headersTableModel.setHeaders(status != null ? status.requestHeaders : List.of(), false);
		paramsTableModel.setHeaders(status != null ? status.requestParams : List.of(), true);
		responseBodyArea.setText(status != null ? safe(status.responseBody) : "");
		responseHeadersArea.setText(status != null ? safe(status.responseHeaders) : "");
		responseLogsArea.setText(status != null ? safe(status.logs) : "");
		responseStatusLabel.setText("");
		isLoading = false;
		if (details != null && details.target != null && !details.target.isBlank()) {
			if (details.service != null) {
				grpcServiceSelection.put(requestId, details.service);
			}
			reloadGrpcServices();
		}
	}

	private void loadChain(String requestId) {
		isLoading = true;
		ChainState chain = stateService.getChainState(requestId);
		chainListModel.clear();
		if (chain != null) {
			for (String id : chain.requestIds) {
				chainListModel.addElement(id);
			}
			chainLogsArea.setText(safe(chain.logs));
			chainCurrentStateArea.setText(safe(chain.currentState));
		} else {
			chainLogsArea.setText("");
			chainCurrentStateArea.setText("");
		}
		chainNextButton.setEnabled(chainSession != null);
		isLoading = false;
		refreshChainRequestsCombo();
	}

	private void saveCurrentEditors() {
		if (isLoading || isStoppingTableEditing || isSyncingParamsFromUrl || currentNode == null ||
			currentNode.type != NodeType.REQUEST) {
			return;
		}
		if (currentNode.requestType == RequestType.HTTP) {
			saveHttp(currentNode.id);
		} else if (currentNode.requestType == RequestType.GRPC) {
			saveGrpc(currentNode.id);
		} else if (currentNode.requestType == RequestType.CHAIN) {
			saveChain(currentNode.id);
		}
	}

	private void stopTableEditing() {
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
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		if (details == null) {
			details = new RequestDetailsState();
			details.requestId = requestId;
		}
		details.type = RequestType.HTTP;
		details.method = String.valueOf(httpMethodCombo.getSelectedItem());
		details.payloadType = resolvePayloadValue(httpPayloadCombo.getSelectedItem());
		details.url = httpUrlField.getText();
		stateService.saveRequestDetails(details);

		RequestStatusState status = buildStatus(requestId);
		stateService.saveRequestStatus(status);
	}

	private void saveGrpc(String requestId) {
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		if (details == null) {
			details = new RequestDetailsState();
			details.requestId = requestId;
		}
		details.type = RequestType.GRPC;
		details.target = grpcTargetField.getText();
		details.service =
			grpcServiceCombo.getSelectedItem() == null ? "" : String.valueOf(grpcServiceCombo.getSelectedItem());
		details.grpcMethod =
			grpcMethodCombo.getSelectedItem() == null ? "" : String.valueOf(grpcMethodCombo.getSelectedItem());
		stateService.saveRequestDetails(details);

		RequestStatusState status = buildStatus(requestId);
		stateService.saveRequestStatus(status);
	}

	private void saveChain(String requestId) {
		ChainState chain = stateService.getChainState(requestId);
		if (chain == null) {
			chain = new ChainState();
			chain.requestId = requestId;
		}
		chain.requestIds = Collections.list(chainListModel.elements());
		chain.logs = chainLogsArea.getText();
		chain.currentState = chainCurrentStateArea.getText();
		stateService.saveChainState(chain);
	}

	private RequestStatusState buildStatus(String requestId) {
		RequestStatusState status = new RequestStatusState();
		status.requestId = requestId;
		status.requestBody = requestBodyArea.getText();
		status.requestHeaders = headersTableModel.getHeaders();
		status.requestParams = paramsTableModel.getHeaders();
		status.formData = formDataTableModel.getEntries();
		status.binaryFilePath = binaryFileField.getText();
		status.responseBody = responseBodyArea.getText();
		status.responseHeaders = responseHeadersArea.getText();
		status.logs = responseLogsArea.getText();
		status.beforeScript = beforeScriptArea.getText();
		status.afterScript = afterScriptArea.getText();
		return status;
	}

	private void executeHttp() {
		if (currentNode == null || currentNode.requestType != RequestType.HTTP) {
			return;
		}
		saveCurrentEditors();
		RequestDetailsState details = stateService.getRequestDetails(currentNode.id);
		if (details == null || details.url == null || details.url.isBlank()) {
			showLog("Missing URL.");
			return;
		}
		String method = details.method == null ? "GET" : details.method;
		RequestStatusState status = stateService.getRequestStatus(currentNode.id);
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();
		List<HeaderEntryState> params = status != null ? status.requestParams : List.of();
		String body = status != null ? status.requestBody : "";
		List<FormEntryState> formData = status != null ? status.formData : List.of();
		String binaryFilePath = status != null ? status.binaryFilePath : "";
		String payloadType = details.payloadType == null ? "RAW" : details.payloadType;
		String before = status != null ? status.beforeScript : "";
		String after = status != null ? status.afterScript : "";

		runInBackground(() -> {
			ExecutionResult result =
				executeWithScripts(
					method,
					details.url,
					headers,
					params,
					body,
					before,
					after,
					false,
					null,
					payloadType,
					formData,
					binaryFilePath
				);
			updateResponseUI(result, false);
		});
	}

	private void executeHttpDownload() {
		if (currentNode == null || currentNode.requestType != RequestType.HTTP) {
			return;
		}
		saveCurrentEditors();
		RequestDetailsState details = stateService.getRequestDetails(currentNode.id);
		if (details == null || details.url == null || details.url.isBlank()) {
			showLog("Missing URL.");
			return;
		}
		String method = details.method == null ? "GET" : details.method;
		RequestStatusState status = stateService.getRequestStatus(currentNode.id);
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();
		List<HeaderEntryState> params = status != null ? status.requestParams : List.of();
		String body = status != null ? status.requestBody : "";
		List<FormEntryState> formData = status != null ? status.formData : List.of();
		String binaryFilePath = status != null ? status.binaryFilePath : "";
		String payloadType = details.payloadType == null ? "RAW" : details.payloadType;
		String before = status != null ? status.beforeScript : "";
		String after = status != null ? status.afterScript : "";

		runInBackground(() -> {
			DownloadResult result =
				executeWithScriptsDownload(
					method,
					details.url,
					headers,
					params,
					body,
					before,
					after,
					payloadType,
					formData,
					binaryFilePath
				);
			updateResponseUI(result.result, false);
			if (result.bodyBytes != null) {
				SwingUtilities.invokeLater(() -> promptSaveDownload(result));
			}
		});
	}

	private void executeGrpc() {
		if (currentNode == null || currentNode.requestType != RequestType.GRPC) {
			return;
		}
		saveCurrentEditors();
		RequestDetailsState details = stateService.getRequestDetails(currentNode.id);
		if (details == null || details.target == null || details.target.isBlank()) {
			showLog("Missing gRPC target.");
			return;
		}
		if (details.service == null || details.service.isBlank() || details.grpcMethod == null ||
			details.grpcMethod.isBlank()) {
			showLog("Missing gRPC service or method.");
			return;
		}
		RequestStatusState status = stateService.getRequestStatus(currentNode.id);
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();
		List<HeaderEntryState> params = status != null ? status.requestParams : List.of();
		String body = status != null ? status.requestBody : "";
		String before = status != null ? status.beforeScript : "";
		String after = status != null ? status.afterScript : "";

		runInBackground(() -> {
			ExecutionResult result = executeGrpcWithScripts(details, headers, params, body, before, after, null);
			updateResponseUI(result, true);
		});
	}

	private void updateResponseUI(
		ExecutionResult result,
		boolean isGrpc
	) {
		SwingUtilities.invokeLater(() -> {
			responseBodyArea.setText(result.responseBody);
			responseHeadersArea.setText(result.responseHeaders);
			responseLogsArea.setText(result.logs);
			if (isGrpc) {
				responseStatusLabel.setForeground(result.statusCode >= 400 ? JBColor.RED : JBColor.GREEN);
				responseStatusLabel.setText("Status: " + result.statusCode + " " + result.statusMessage);
			} else {
				responseStatusLabel.setForeground(result.statusCode >= 400 ? JBColor.RED : JBColor.GREEN);
				responseStatusLabel.setText("Status: " + result.statusCode);
			}
			saveCurrentEditors();
		});
	}

	private ExecutionResult executeWithScripts(
		String method,
		String url,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		boolean forChain,
		VarsStore sharedVars,
		String payloadType,
		List<FormEntryState> formData,
		String binaryFilePath
	) {
		VarsStore vars = sharedVars == null ? new VarsStore() : sharedVars;
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = message -> logs.add(message);
		ScriptHelpers helpers = new ScriptHelpers(logger);
		ScriptRequest rawRequest = new ScriptRequest(body, cloneHeaders(headers), cloneHeaders(params));
		rawRequest.setFormData(cloneFormData(formData));
		rawRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);
		ScriptRequest scriptRequest = new ScriptRequest(body, cloneHeaders(headers), cloneHeaders(params));
		scriptRequest.setFormData(cloneFormData(formData));
		scriptRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, null)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			return ExecutionResult.failure(logs);
		}

		Map<String, Object> varsSnapshot = vars.entries();
		String templatedBody = templateEngine.applyToBody(scriptRequest.getBody(), varsSnapshot);
		List<HeaderEntryState> templatedHeaders =
			templateEngine.applyToHeaders(scriptRequest.getHeaders(), varsSnapshot);
		List<HeaderEntryState> templatedParams = templateEngine.applyToParams(scriptRequest.getParams(), varsSnapshot);
		List<FormEntryState> templatedFormData = templateEngine.applyToFormData(
			scriptRequest.getFormData(),
			varsSnapshot
		);
		String templatedBinaryPath = templateEngine.applyToText(
			scriptRequest.getBinaryFilePath(),
			varsSnapshot
		);
		String templatedUrlBase = templateEngine.applyToText(url, varsSnapshot);
		String templatedUrl = applyQueryParams(templatedUrlBase, templatedParams);

		try {
			HttpExecutionResponse response =
				httpExecutor.execute(
					method,
					templatedUrl,
					templatedHeaders,
					templatedBody,
					templatedFormData,
					templatedBinaryPath,
					resolvePayloadType(payloadType)
				);
			try {
				ScriptRequest afterRequest = new ScriptRequest(
					templatedBody,
					templatedHeaders,
					templatedParams
				);
				afterRequest.setFormData(cloneFormData(templatedFormData));
				afterRequest.setBinaryFilePath(templatedBinaryPath);
				scriptRuntime.runScript(
					after,
					new ScriptContext(
						vars,
						logger,
						helpers,
						afterRequest,
						rawRequest,
						response
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			String responseHeaders = toJson(response.headers);
			return new ExecutionResult(
				response.statusCode,
				"",
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				String.join("\n", logs)
			);
		} catch (Exception error) {
			logs.add("Request failed: " + error.getMessage());
			return ExecutionResult.failure(logs);
		}
	}

	private DownloadResult executeWithScriptsDownload(
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
		VarsStore vars = new VarsStore();
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = message -> logs.add(message);
		ScriptHelpers helpers = new ScriptHelpers(logger);
		ScriptRequest rawRequest = new ScriptRequest(body, cloneHeaders(headers), cloneHeaders(params));
		rawRequest.setFormData(cloneFormData(formData));
		rawRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);
		ScriptRequest scriptRequest = new ScriptRequest(body, cloneHeaders(headers), cloneHeaders(params));
		scriptRequest.setFormData(cloneFormData(formData));
		scriptRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, null)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			return DownloadResult.failure(logs);
		}

		Map<String, Object> varsSnapshot = vars.entries();
		String templatedBody = templateEngine.applyToBody(scriptRequest.getBody(), varsSnapshot);
		List<HeaderEntryState> templatedHeaders =
			templateEngine.applyToHeaders(scriptRequest.getHeaders(), varsSnapshot);
		List<HeaderEntryState> templatedParams = templateEngine.applyToParams(scriptRequest.getParams(), varsSnapshot);
		List<FormEntryState> templatedFormData = templateEngine.applyToFormData(
			scriptRequest.getFormData(),
			varsSnapshot
		);
		String templatedBinaryPath = templateEngine.applyToText(
			scriptRequest.getBinaryFilePath(),
			varsSnapshot
		);
		String templatedUrlBase = templateEngine.applyToText(url, varsSnapshot);
		String templatedUrl = applyQueryParams(templatedUrlBase, templatedParams);

		try {
			HttpExecutionResponse response =
				httpExecutor.executeBinary(
					method,
					templatedUrl,
					templatedHeaders,
					templatedBody,
					templatedFormData,
					templatedBinaryPath,
					resolvePayloadType(payloadType)
				);
			try {
				ScriptRequest afterRequest = new ScriptRequest(
					templatedBody,
					templatedHeaders,
					templatedParams
				);
				afterRequest.setFormData(cloneFormData(templatedFormData));
				afterRequest.setBinaryFilePath(templatedBinaryPath);
				scriptRuntime.runScript(
					after,
					new ScriptContext(
						vars,
						logger,
						helpers,
						afterRequest,
						rawRequest,
						response
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			String responseHeaders = toJson(response.headers);
			ExecutionResult result = new ExecutionResult(
				response.statusCode,
				"",
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				String.join("\n", logs)
			);
			return new DownloadResult(result, response.bodyBytes, response.headers);
		} catch (Exception error) {
			logs.add("Request failed: " + error.getMessage());
			return DownloadResult.failure(logs);
		}
	}

	private ExecutionResult executeGrpcWithScripts(
		RequestDetailsState details,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		VarsStore sharedVars
	) {
		VarsStore vars = sharedVars == null ? new VarsStore() : sharedVars;
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = message -> logs.add(message);
		ScriptHelpers helpers = new ScriptHelpers(logger);
		ScriptRequest rawRequest = new ScriptRequest(body, cloneHeaders(headers), cloneHeaders(params));
		ScriptRequest scriptRequest = new ScriptRequest(body, cloneHeaders(headers), cloneHeaders(params));

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, null)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			return ExecutionResult.failure(logs);
		}

		Map<String, Object> varsSnapshot = vars.entries();
		String templatedBody = templateEngine.applyToBody(scriptRequest.getBody(), varsSnapshot);
		List<HeaderEntryState> templatedHeaders =
			templateEngine.applyToHeaders(scriptRequest.getHeaders(), varsSnapshot);
		List<HeaderEntryState> templatedParams = templateEngine.applyToParams(scriptRequest.getParams(), varsSnapshot);

		try {
			GrpcExecutionResponse response = grpcExecutor.execute(details.target,
																  details.service,
																  details.grpcMethod,
																  templatedBody,
																  templatedHeaders
			);
			try {
				scriptRuntime.runScript(
					after,
					new ScriptContext(
						vars,
						logger,
						helpers,
						new ScriptRequest(
							templatedBody,
							templatedHeaders,
							templatedParams
						),
						rawRequest,
						response
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			String responseHeaders = toJson(response.headers);
			return new ExecutionResult(
				response.statusCode,
				response.statusMessage,
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				String.join("\n", logs)
			);
		} catch (Exception error) {
			logs.add("gRPC request failed: " + error.getMessage());
			return ExecutionResult.failure(logs);
		}
	}

	private void reloadGrpcServices() {
		if (currentNode == null || currentNode.requestType != RequestType.GRPC) {
			return;
		}
		String requestId = currentNode.id;
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
				SwingUtilities.invokeLater(() -> {
					if (currentNode == null || !Objects.equals(currentNode.id, requestId)) {
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
						responseLogsArea.setText("No gRPC services found.");
					}
				});
			} catch (Exception error) {
				SwingUtilities.invokeLater(() -> responseLogsArea.setText("gRPC reload failed: " + error.getMessage()));
			}
		});
	}

	private void updateGrpcMethods(String desiredMethod) {
		if (currentNode == null) {
			return;
		}
		List<GrpcServiceInfo> services = grpcServicesCache.getOrDefault(currentNode.id, List.of());
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

	private void refreshChainRequestsCombo() {
		chainRequestCombo.removeAllItems();
		for (NodeState node : stateService.getNodes()) {
			if (node.type == NodeType.REQUEST && node.requestType != RequestType.CHAIN) {
				if (currentNode != null && Objects.equals(currentNode.id, node.id)) {
					continue;
				}
				chainRequestCombo.addItem(node.id);
			}
		}
	}

	private void addChainRequest() {
		if (currentNode == null || currentNode.requestType != RequestType.CHAIN) {
			return;
		}
		Object selected = chainRequestCombo.getSelectedItem();
		if (selected == null) {
			return;
		}
		chainListModel.addElement(String.valueOf(selected));
		saveChain(currentNode.id);
	}

	private void removeChainRequest() {
		int index = chainList.getSelectedIndex();
		if (index < 0) {
			return;
		}
		chainListModel.remove(index);
		if (currentNode != null) {
			saveChain(currentNode.id);
		}
	}

	private void runChain(boolean debug) {
		if (currentNode == null || currentNode.requestType != RequestType.CHAIN) {
			return;
		}
		saveCurrentEditors();
		chainRunButton.setEnabled(false);
		chainDebugButton.setEnabled(false);
		chainNextButton.setEnabled(debug);
		chainSession = new ChainSession();
		if (debug) {
			runChainNext();
			return;
		}
		runInBackground(() -> {
			while (chainSession.nextIndex < chainListModel.size()) {
				executeChainStep(chainSession, chainListModel.getElementAt(chainSession.nextIndex));
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
		String requestId = chainListModel.getElementAt(chainSession.nextIndex);
		runInBackground(() -> {
			executeChainStep(chainSession, requestId);
			chainSession.nextIndex++;
			SwingUtilities.invokeLater(() -> chainNextButton.setEnabled(
				chainSession.nextIndex < chainListModel.size()));
			if (chainSession.nextIndex >= chainListModel.size()) {
				finishChainRun();
			}
		});
	}

	private void executeChainStep(
		ChainSession session,
		String requestId
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
			result = executeWithScripts(method,
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
			result = executeGrpcWithScripts(details,
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

		Map<String, Object> currentState = new LinkedHashMap<>();
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("id", requestId);
		meta.put("name", node.name);
		meta.put("type", details.type.name());
		if (details.type == RequestType.HTTP) {
			meta.put("http", Map.of("method", details.method, "url", details.url));
		} else if (details.type == RequestType.GRPC) {
			meta.put(
				"grpc",
				Map.of("target", details.target, "service", details.service, "method", details.grpcMethod)
			);
		}
		currentState.put("request", Map.of(
			"meta", meta,
			"body", status.requestBody,
			"headers", status.requestHeaders
		));
		currentState.put("response", Map.of(
			"statusCode", result.statusCode,
			"statusMessage", result.statusMessage,
			"body", result.responseBody,
			"headers", result.responseHeaders
		));
		currentState.put("vars", session.vars.entries());
		session.currentStateJson = toJson(currentState);

		updateChainUi(session, node);
	}

	private void updateChainUi(
		ChainSession session,
		NodeState node
	) {
		SwingUtilities.invokeLater(() -> {
			chainLogsArea.setText(String.join("\n", session.logs));
			chainCurrentStateArea.setText(session.currentStateJson);
			if (node != null) {
				chainList.setSelectedValue(node.id, true);
			}
			saveChain(currentNode.id);
		});
	}

	private void finishChainRun() {
		SwingUtilities.invokeLater(() -> {
			chainRunButton.setEnabled(true);
			chainDebugButton.setEnabled(true);
			chainNextButton.setEnabled(false);
			chainSession = null;
			saveChain(currentNode.id);
		});
	}

	private void attachAutoSaveListeners() {
		httpUrlField.getDocument().addDocumentListener(new AutoSaveListener());
		httpUrlField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				scheduleParamsSyncFromUrl();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				scheduleParamsSyncFromUrl();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				scheduleParamsSyncFromUrl();
			}
		});
		grpcTargetField.getDocument().addDocumentListener(new AutoSaveListener());
		requestBodyArea.addDocumentListener(new EditorAutoSaveListener());
		beforeScriptArea.addDocumentListener(new EditorAutoSaveListener());
		afterScriptArea.addDocumentListener(new EditorAutoSaveListener());
		headersTableModel.addTableModelListener((TableModelEvent e) -> saveCurrentEditors());
		paramsTableModel.addTableModelListener((TableModelEvent e) -> saveCurrentEditors());
		formDataTableModel.addTableModelListener((TableModelEvent e) -> saveCurrentEditors());
		binaryFileField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				saveCurrentEditors();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				saveCurrentEditors();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				saveCurrentEditors();
			}
		});
		httpMethodCombo.addActionListener(e -> saveCurrentEditors());
		httpPayloadCombo.addActionListener(e -> {
			switchPayloadType();
			if (!isLoading) {
				saveCurrentEditors();
			}
		});
		grpcServiceCombo.addActionListener(e -> {
			if (!isLoading && !isGrpcReloading) {
				if (currentNode != null) {
					Object selected = grpcServiceCombo.getSelectedItem();
					grpcServiceSelection.put(currentNode.id, selected == null ? "" : String.valueOf(selected));
				}
				updateGrpcMethods(null);
				saveCurrentEditors();
			}
		});
		grpcMethodCombo.addActionListener(e -> {
			if (!isLoading && !isGrpcReloading) {
				saveCurrentEditors();
			}
		});
	}

	private void attachHotkeys() {
		InputMap inputMap = root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		ActionMap actionMap = root.getActionMap();
		inputMap.put(KeyStroke.getKeyStroke("control ENTER"), "webrunner.send");
		inputMap.put(KeyStroke.getKeyStroke("DELETE"), "webrunner.delete");
		inputMap.put(KeyStroke.getKeyStroke("control shift L"), "webrunner.format");
		inputMap.put(KeyStroke.getKeyStroke("control alt L"), "webrunner.format");
		inputMap.put(KeyStroke.getKeyStroke("alt RIGHT"), "webrunner.tab.next");
		inputMap.put(KeyStroke.getKeyStroke("alt LEFT"), "webrunner.tab.prev");
		inputMap.put(KeyStroke.getKeyStroke("shift alt RIGHT"), "webrunner.focus.editor");
		inputMap.put(KeyStroke.getKeyStroke("shift alt LEFT"), "webrunner.focus.tree");
		actionMap.put("webrunner.send", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				if (currentNode == null || currentNode.type != NodeType.REQUEST) {
					return;
				}
				if (currentNode.requestType == RequestType.HTTP) {
					executeHttp();
				} else if (currentNode.requestType == RequestType.GRPC) {
					executeGrpc();
				} else if (currentNode.requestType == RequestType.CHAIN) {
					if (chainSession != null) {
						runChainNext();
					} else {
						runChain(false);
					}
				}
			}
		});
		actionMap.put("webrunner.delete", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				deleteSelected();
			}
		});
		actionMap.put("webrunner.format", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				formatCurrentEditors();
			}
		});
		actionMap.put("webrunner.tab.next", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				switchTab(1);
			}
		});
		actionMap.put("webrunner.tab.prev", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				switchTab(-1);
			}
		});
		actionMap.put("webrunner.focus.editor", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				focusEditor();
			}
		});
		actionMap.put("webrunner.focus.tree", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				tree.requestFocusInWindow();
			}
		});
	}

	private void switchTab(int direction) {
		if (currentNode == null) {
			return;
		}
		JTabbedPane targetTabs = null;
		if (currentNode.requestType == RequestType.CHAIN) {
			targetTabs = chainResponseTabs;
		} else {
			Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
			if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, responseTabs)) {
				targetTabs = responseTabs;
			} else {
				targetTabs = requestTabs;
			}
		}
		if (targetTabs == null || targetTabs.getTabCount() == 0) {
			return;
		}
		int next = targetTabs.getSelectedIndex() + direction;
		if (next < 0) {
			next = targetTabs.getTabCount() - 1;
		} else if (next >= targetTabs.getTabCount()) {
			next = 0;
		}
		targetTabs.setSelectedIndex(next);
		targetTabs.requestFocusInWindow();
	}

	private void focusEditor() {
		if (currentNode == null) {
			return;
		}
		if (currentNode.requestType == RequestType.CHAIN) {
			chainLogsArea.requestFocusInWindow();
			return;
		}
		requestBodyArea.requestFocusInWindow();
	}

	private void runInBackground(Runnable runnable) {
		ApplicationManager.getApplication().executeOnPooledThread(runnable);
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

	private void formatCurrentEditors() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST) {
			return;
		}
		WriteCommandAction.runWriteCommandAction(project, () -> {
			if (currentNode.requestType == RequestType.CHAIN) {
				formatJsonField(chainCurrentStateArea);
				return;
			}
			formatJsonField(requestBodyArea);
			formatJsonField(responseBodyArea);
			formatJsonField(responseHeadersArea);
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

	private void generateBodyFromClass() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST ||
			currentNode.requestType == RequestType.CHAIN) {
			return;
		}
		JCheckBox includeInherited = new JCheckBox("Include inherited fields", true);
		JCheckBox useAnnotations = new JCheckBox("Use Jackson annotations", true);
		JCheckBox useNulls = new JCheckBox("Use null values", false);
		JPanel options = new JPanel(new GridLayout(0, 1));
		options.add(includeInherited);
		options.add(useAnnotations);
		options.add(useNulls);
		int confirm = JOptionPane.showConfirmDialog(root, options, "Class body options", JOptionPane.OK_CANCEL_OPTION);
		if (confirm != JOptionPane.OK_OPTION) {
			return;
		}
		TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
			.createAllProjectScopeChooser("Select Class");
		chooser.showDialog();
		PsiClass psiClass = chooser.getSelected();
		if (psiClass == null) {
			return;
		}
		Map<String, Object> body = buildBodyForClass(
			psiClass,
			new HashSet<>(),
			0,
			includeInherited.isSelected(),
			useAnnotations.isSelected(),
			useNulls.isSelected()
		);
		String json = toJson(body);
		requestBodyArea.setText(json);
		requestBodyArea.requestFocusInWindow();
	}

	private Map<String, Object> buildBodyForClass(
		PsiClass psiClass,
		Set<String> visiting,
		int depth,
		boolean includeInherited,
		boolean useAnnotations,
		boolean useNulls
	) {
		if (psiClass == null) {
			return Map.of();
		}
		String key = psiClass.getQualifiedName() == null ? psiClass.getName() : psiClass.getQualifiedName();
		if (key != null) {
			if (visiting.contains(key)) {
				return Map.of();
			}
			visiting.add(key);
		}
		if (depth > 4) {
			return Map.of();
		}
		Map<String, Object> result = new LinkedHashMap<>();
		Set<String> ignored = useAnnotations ? ignoredJsonProperties(psiClass) : Set.of();

		if (psiClass.isRecord()) {
			for (PsiRecordComponent component : psiClass.getRecordComponents()) {
				String name = jsonName(component.getName(),
									   component.getAnnotation("com.fasterxml.jackson.annotation.JsonProperty"),
									   useAnnotations
				);
				if (ignored.contains(name)) {
					continue;
				}
				result.put(
					name,
					valueForType(component.getType(),
								 visiting,
								 depth + 1,
								 includeInherited,
								 useAnnotations,
								 useNulls
					)
				);
			}
		} else {
			PsiField[] fields = includeInherited ? psiClass.getAllFields() : psiClass.getFields();
			for (PsiField field : fields) {
				if (field.hasModifierProperty(PsiModifier.STATIC)) {
					continue;
				}
				if (field.hasModifierProperty(PsiModifier.TRANSIENT)) {
					continue;
				}
				if (useAnnotations && field.getAnnotation("com.fasterxml.jackson.annotation.JsonIgnore") != null) {
					continue;
				}
				String name = jsonName(field.getName(),
									   field.getAnnotation("com.fasterxml.jackson.annotation.JsonProperty"),
									   useAnnotations
				);
				if (ignored.contains(name)) {
					continue;
				}
				result.put(
					name,
					valueForType(field.getType(),
								 visiting,
								 depth + 1,
								 includeInherited,
								 useAnnotations,
								 useNulls
					)
				);
			}
		}

		if (key != null) {
			visiting.remove(key);
		}
		return result;
	}

	private Object valueForType(
		PsiType type,
		Set<String> visiting,
		int depth,
		boolean includeInherited,
		boolean useAnnotations,
		boolean useNulls
	) {
		if (type == null) {
			return null;
		}
		if (useNulls) {
			return null;
		}
		if (type.equals(PsiTypes.booleanType())) {
			return false;
		}
		if (type.equals(
			PsiTypes.byteType())
			|| type.equals(PsiTypes.shortType())
			|| type.equals(PsiTypes.intType())
			|| type.equals(PsiTypes.longType())
		) {
			return 0;
		}
		if (
			type.equals(PsiTypes.floatType())
				|| type.equals(PsiTypes.doubleType())
		) {
			return 0.0;
		}
		if (type.equals(PsiTypes.charType())) {
			return "a";
		}
		if (type instanceof PsiArrayType) {
			PsiType component = ((PsiArrayType) type).getComponentType();
			return List.of(valueForType(component, visiting, depth + 1, includeInherited, useAnnotations, useNulls));
		}
		if (type instanceof PsiClassType classType) {
			PsiClass resolved = classType.resolve();
			if (resolved == null || resolved instanceof PsiTypeParameter) {
				return null;
			}
			String qName = resolved.getQualifiedName();
			if (qName != null) {
				switch (qName) {
					case "java.lang.String", "java.lang.CharSequence" -> {
						return "";
					}
					case "java.lang.Boolean" -> {
						return false;
					}
					case "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte" -> {
						return 0;
					}
					case "java.lang.Float", "java.lang.Double" -> {
						return 0.0;
					}
					case "java.math.BigDecimal" -> {
						return "0.00";
					}
					case "java.math.BigInteger" -> {
						return "0";
					}
					case "java.util.UUID" -> {
						return UUID.randomUUID().toString();
					}
					case "java.time.LocalDate" -> {
						return "2024-01-01";
					}
					case "java.time.LocalDateTime" -> {
						return "2024-01-01T00:00:00";
					}
					case "java.time.OffsetDateTime" -> {
						return "2024-01-01T00:00:00Z";
					}
					case "java.time.Instant" -> {
						return "2024-01-01T00:00:00Z";
					}
				}
			}
			if (resolved.isEnum()) {
				for (PsiField field : resolved.getFields()) {
					if (field instanceof com.intellij.psi.PsiEnumConstant) {
						return field.getName();
					}
				}
				return "";
			}
			if (InheritanceUtil.isInheritor(resolved, "java.util.Map")) {
				PsiType[] params = classType.getParameters();
				Object keySample = params.length > 0 ?
					valueForType(params[0], visiting, depth + 1, includeInherited, useAnnotations, useNulls) : "key";
				Object valueSample = params.length > 1 ?
					valueForType(params[1], visiting, depth + 1, includeInherited, useAnnotations, useNulls) : "";
				if (keySample == null) {
					keySample = "key";
				}
				return Map.of(String.valueOf(keySample), valueSample);
			}
			if (InheritanceUtil.isInheritor(resolved, "java.util.Collection")) {
				PsiType[] params = classType.getParameters();
				Object item = params.length > 0 ?
					valueForType(params[0], visiting, depth + 1, includeInherited, useAnnotations, useNulls) : null;
				return item == null ? List.of() : List.of(item);
			}
			if (InheritanceUtil.isInheritor(resolved, "java.util.Optional")) {
				PsiType[] params = classType.getParameters();
				return params.length > 0 ?
					valueForType(params[0], visiting, depth + 1, includeInherited, useAnnotations, useNulls) : null;
			}
			return buildBodyForClass(resolved, visiting, depth + 1, includeInherited, useAnnotations, useNulls);
		}
		return null;
	}

	private void generateBodyFromProto() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST ||
			currentNode.requestType == RequestType.CHAIN) {
			return;
		}
		JCheckBox useNulls = new JCheckBox("Use null values", false);
		JPanel options = new JPanel(new GridLayout(0, 1));
		options.add(useNulls);
		int confirm = JOptionPane.showConfirmDialog(root, options, "Proto body options", JOptionPane.OK_CANCEL_OPTION);
		if (confirm != JOptionPane.OK_OPTION) {
			return;
		}
		ProtoMessageSelection selection = chooseProtoMessage();
		if (selection == null) {
			return;
		}
		Map<String, Object> body = buildBodyForProtoMessage(selection, new HashSet<>(), 0, useNulls.isSelected());
		String json = toJson(body);
		requestBodyArea.setText(json);
		requestBodyArea.requestFocusInWindow();
	}

	private Map<String, Object> buildBodyForProtoMessage(
		ProtoMessageSelection selection,
		Set<String> visiting,
		int depth,
		boolean useNulls
	) {
		if (selection == null) {
			return Map.of();
		}
		String key = selection.qualifiedName;
		if (key != null) {
			if (visiting.contains(key)) {
				return Map.of();
			}
			visiting.add(key);
		}
		if (depth > 4) {
			return Map.of();
		}
		Map<String, Object> result = new LinkedHashMap<>();
		for (ProtoField field : selection.message.fields) {
			Object value = valueForProtoField(field,
											  selection.registry,
											  selection.message.fullName,
											  visiting,
											  depth + 1,
											  useNulls
			);
			result.put(field.name, value);
		}
		if (key != null) {
			visiting.remove(key);
		}
		return result;
	}

	private Object valueForProtoField(
		ProtoField field,
		ProtoRegistry registry,
		String currentMessageName,
		Set<String> visiting,
		int depth,
		boolean useNulls
	) {
		if (useNulls) {
			return null;
		}
		if (field.isMap) {
			Object key = valueForProtoScalar(field.mapKeyType);
			Object value =
				valueForProtoType(field.mapValueType, registry, currentMessageName, visiting, depth, useNulls);
			if (key == null) {
				key = "key";
			}
			return Map.of(String.valueOf(key), value);
		}
		Object value = valueForProtoType(field.type, registry, currentMessageName, visiting, depth, useNulls);
		if (field.repeated) {
			if (value == null) {
				return List.of();
			}
			return List.of(value);
		}
		return value;
	}

	private Object valueForProtoType(
		String type,
		ProtoRegistry registry,
		String currentMessageName,
		Set<String> visiting,
		int depth,
		boolean useNulls
	) {
		if (useNulls) {
			return null;
		}
		Object scalar = valueForProtoScalar(type);
		if (scalar != null) {
			return scalar;
		}
		if (type.startsWith(".google.protobuf.") || type.startsWith("google.protobuf.")) {
			String shortName = type.startsWith(".") ? type.substring(".google.protobuf.".length()) :
				type.substring("google.protobuf.".length());
			return valueForWellKnown(shortName);
		}
		String resolved = registry.resolveType(type, currentMessageName);
		if (resolved != null && registry.enums.containsKey(resolved)) {
			ProtoEnum protoEnum = registry.enums.get(resolved);
			if (protoEnum != null && !protoEnum.values.isEmpty()) {
				return protoEnum.values.get(0);
			}
			return "";
		}
		ProtoMessage message = resolved == null ? null : registry.messages.get(resolved);
		if (message == null) {
			return "";
		}
		return buildBodyForProtoMessage(
			new ProtoMessageSelection(message.displayName, registry, message),
			visiting,
			depth + 1,
			useNulls
		);
	}

	private Object valueForProtoScalar(String type) {
		if (type == null) {
			return null;
		}
		return switch (type) {
			case "string" -> "";
			case "bool" -> false;
			case "double", "float" -> 0.0;
			case "int32", "int64", "sint32", "sint64", "uint32", "uint64", "fixed32", "fixed64", "sfixed32",
				 "sfixed64" -> 0;
			case "bytes" -> "";
			default -> null;
		};
	}

	private Object valueForWellKnown(String shortName) {
		return switch (shortName) {
			case "Timestamp" -> "2024-01-01T00:00:00Z";
			case "StringValue" -> "";
			case "BoolValue" -> false;
			case "Int32Value", "UInt32Value", "Int64Value", "UInt64Value" -> 0;
			case "DoubleValue", "FloatValue" -> 0.0;
			default -> "";
		};
	}

	private ProtoMessageSelection chooseProtoMessage() {
		VirtualFile file = chooseProtoFile();
		if (file == null) {
			return null;
		}
		ProtoRegistry registry = loadProtoRegistry(file);
		List<ProtoMessage> fileMessages = registry.messagesByFile.getOrDefault(file.getPath(), List.of());
		if (fileMessages.isEmpty()) {
			JOptionPane.showMessageDialog(
				root,
				"No messages found in selected .proto file.",
				"Proto body",
				JOptionPane.INFORMATION_MESSAGE
			);
			return null;
		}
		DefaultListModel<ProtoMessageSelection> model = new DefaultListModel<>();
		for (ProtoMessage message : fileMessages) {
			model.addElement(new ProtoMessageSelection(message.displayName, registry, message));
		}
		JBList<ProtoMessageSelection> list = new JBList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus
			) {
				Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof ProtoMessageSelection selection) {
					((JLabel) component).setText(selection.display);
				}
				return component;
			}
		});
		new ListSpeedSearch<>(list, selection -> selection == null ? "" : selection.display);
		JScrollPane scrollPane = new JBScrollPane(list);
		scrollPane.setPreferredSize(new Dimension(520, 360));
		int confirm =
			JOptionPane.showConfirmDialog(root, scrollPane, "Select Proto Message", JOptionPane.OK_CANCEL_OPTION);
		if (confirm != JOptionPane.OK_OPTION) {
			return null;
		}
		return list.getSelectedValue();
	}

	private VirtualFile chooseProtoFile() {
		com.intellij.openapi.fileChooser.FileChooserDescriptor descriptor =
			com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor("proto");
		descriptor.setTitle("Select Proto File");
		return com.intellij.openapi.fileChooser.FileChooser.chooseFile(descriptor, project, null);
	}

	private ProtoRegistry loadProtoRegistry(VirtualFile rootFile) {
		ProtoRegistry registry = new ProtoRegistry();
		Collection<VirtualFile> files =
			FilenameIndex.getAllFilesByExt(project, "proto", GlobalSearchScope.projectScope(project));
		ProtoImportIndex index = new ProtoImportIndex(project, files);
		registry.loadFrom(rootFile, index);
		return registry;
	}

	private static final class ProtoRegistry {

		private final Map<String, ProtoMessage> messages = new LinkedHashMap<>();
		private final Map<String, ProtoEnum> enums = new LinkedHashMap<>();
		private final Map<String, List<ProtoMessage>> messagesByFile = new LinkedHashMap<>();
		private final Set<String> loadedFiles = new HashSet<>();

		void loadFrom(
			VirtualFile file,
			ProtoImportIndex index
		) {
			if (file == null) {
				return;
			}
			String path = file.getPath();
			if (loadedFiles.contains(path)) {
				return;
			}
			loadedFiles.add(path);
			try {
				String text = VfsUtilCore.loadText(file);
				ProtoParser parser = new ProtoParser(text, file.getName(), path);
				ProtoFile protoFile = parser.parse();
				for (ProtoMessage message : protoFile.messages) {
					messages.putIfAbsent(message.fullName, message);
					messagesByFile.computeIfAbsent(path, key -> new ArrayList<>()).add(message);
				}
				for (ProtoEnum protoEnum : protoFile.enums) {
					enums.putIfAbsent(protoEnum.fullName, protoEnum);
				}
				for (String importPath : protoFile.imports) {
					VirtualFile imported = index.resolve(importPath);
					if (imported != null) {
						loadFrom(imported, index);
					}
				}
			} catch (Exception ignored) {
			}
		}

		String resolveType(
			String type,
			String currentMessageFullName
		) {
			if (type == null || type.isBlank()) {
				return null;
			}
			String normalized = type.startsWith(".") ? type.substring(1) : type;
			if (messages.containsKey(normalized) || enums.containsKey(normalized)) {
				return normalized;
			}
			if (currentMessageFullName != null && !currentMessageFullName.isBlank()) {
				String scope = currentMessageFullName;
				while (scope.contains(".")) {
					String candidate = scope + "." + normalized;
					if (messages.containsKey(candidate) || enums.containsKey(candidate)) {
						return candidate;
					}
					scope = scope.substring(0, scope.lastIndexOf('.'));
				}
				int lastDot = currentMessageFullName.lastIndexOf('.');
				if (lastDot > 0) {
					String pkg = currentMessageFullName.substring(0, lastDot);
					String candidate = pkg + "." + normalized;
					if (messages.containsKey(candidate) || enums.containsKey(candidate)) {
						return candidate;
					}
				}
			}
			return normalized;
		}
	}

	private static final class ProtoImportIndex {

		private final Map<String, List<VirtualFile>> byPath = new HashMap<>();
		private final List<VirtualFile> allFiles = new ArrayList<>();
		private final String basePath;

		ProtoImportIndex(
			Project project,
			Collection<VirtualFile> files
		) {
			this.basePath = project.getBasePath() == null ? null : project.getBasePath().replace("\\", "/");
			for (VirtualFile file : files) {
				allFiles.add(file);
				add(file.getName(), file);
				String path = file.getPath().replace("\\", "/");
				if (basePath != null && path.startsWith(basePath + "/")) {
					String relative = path.substring(basePath.length() + 1);
					add(relative, file);
				}
			}
		}

		VirtualFile resolve(String importPath) {
			if (importPath == null || importPath.isBlank()) {
				return null;
			}
			String key = importPath.replace("\\", "/");
			List<VirtualFile> direct = byPath.get(key);
			if (direct != null && !direct.isEmpty()) {
				return direct.get(0);
			}
			for (VirtualFile file : allFiles) {
				String path = file.getPath().replace("\\", "/");
				if (path.endsWith(key)) {
					return file;
				}
			}
			return null;
		}

		private void add(
			String key,
			VirtualFile file
		) {
			byPath.computeIfAbsent(key, ignore -> new ArrayList<>()).add(file);
		}
	}

	private static final class ProtoParser {

		private final ProtoTokenizer tokenizer;
		private final ProtoFile file;

		ProtoParser(
			String text,
			String fileName,
			String path
		) {
			this.tokenizer = new ProtoTokenizer(text == null ? "" : text);
			this.file = new ProtoFile(fileName, path);
		}

		ProtoFile parse() {
			while (tokenizer.hasNext()) {
				if (tokenizer.match("package")) {
					file.packageName = readQualifiedName();
					tokenizer.consume(";");
					continue;
				}
				if (tokenizer.match("import")) {
					if (tokenizer.peekText("public") || tokenizer.peekText("weak")) {
						tokenizer.next();
					}
					String path = tokenizer.readString();
					if (path != null) {
						file.imports.add(path);
					}
					tokenizer.consume(";");
					continue;
				}
				if (tokenizer.match("message")) {
					parseMessage(null);
					continue;
				}
				if (tokenizer.match("enum")) {
					parseEnum(null);
					continue;
				}
				skipStatementOrBlock();
			}
			return file;
		}

		private void parseMessage(String parentFullName) {
			String name = tokenizer.readIdentifier();
			if (name == null) {
				return;
			}
			String fullName = buildFullName(file.packageName, parentFullName, name);
			ProtoMessage message = new ProtoMessage();
			message.name = name;
			message.packageName = file.packageName;
			message.fullName = fullName;
			message.displayName = file.fileName + ":" + fullName;
			file.messages.add(message);
			if (!tokenizer.consume("{")) {
				return;
			}
			while (tokenizer.hasNext() && !tokenizer.peekText("}")) {
				if (tokenizer.match("message")) {
					parseMessage(fullName);
					continue;
				}
				if (tokenizer.match("enum")) {
					parseEnum(fullName);
					continue;
				}
				if (tokenizer.match("oneof")) {
					parseOneof(message);
					continue;
				}
				if (tokenizer.peekText("option") || tokenizer.peekText("reserved") ||
					tokenizer.peekText("extensions")) {
					tokenizer.next();
					skipStatementOrBlock();
					continue;
				}
				ProtoField field = parseField(true, fullName);
				if (field != null) {
					message.fields.add(field);
				} else {
					skipStatementOrBlock();
				}
			}
			tokenizer.consume("}");
		}

		private void parseEnum(String parentFullName) {
			String name = tokenizer.readIdentifier();
			if (name == null) {
				return;
			}
			String fullName = buildFullName(file.packageName, parentFullName, name);
			ProtoEnum protoEnum = new ProtoEnum();
			protoEnum.name = name;
			protoEnum.fullName = fullName;
			if (!tokenizer.consume("{")) {
				return;
			}
			while (tokenizer.hasNext() && !tokenizer.peekText("}")) {
				if (tokenizer.peekText("option") || tokenizer.peekText("reserved")) {
					tokenizer.next();
					skipStatementOrBlock();
					continue;
				}
				String value = tokenizer.readIdentifier();
				if (value == null) {
					tokenizer.next();
					continue;
				}
				protoEnum.values.add(value);
				skipStatementOrBlock();
			}
			tokenizer.consume("}");
			file.enums.add(protoEnum);
		}

		private void parseOneof(ProtoMessage message) {
			tokenizer.readIdentifier();
			if (!tokenizer.consume("{")) {
				return;
			}
			while (tokenizer.hasNext() && !tokenizer.peekText("}")) {
				ProtoField field = parseField(false, message.fullName);
				if (field != null) {
					message.fields.add(field);
				} else {
					skipStatementOrBlock();
				}
			}
			tokenizer.consume("}");
		}

		private ProtoField parseField(
			boolean allowLabel,
			String currentMessageFullName
		) {
			boolean repeated = false;
			if (allowLabel &&
				(tokenizer.peekText("repeated") || tokenizer.peekText("optional") || tokenizer.peekText("required"))) {
				repeated = tokenizer.match("repeated");
				if (!repeated) {
					tokenizer.next();
				}
			}
			String type;
			ProtoField field = new ProtoField();
			if (tokenizer.match("map")) {
				if (!tokenizer.consume("<")) {
					return null;
				}
				String keyType = readTypeName();
				if (!tokenizer.consume(",")) {
					return null;
				}
				String valueType = readTypeName();
				if (!tokenizer.consume(">")) {
					return null;
				}
				type = "map";
				field.isMap = true;
				field.mapKeyType = keyType;
				field.mapValueType = valueType;
			} else {
				type = readTypeName();
			}
			if (type == null) {
				return null;
			}
			String name = tokenizer.readIdentifier();
			if (name == null) {
				return null;
			}
			field.name = name;
			field.repeated = repeated;
			field.type = type;
			skipFieldRemainder();
			return field;
		}

		private String readTypeName() {
			if (tokenizer.match(".")) {
				String first = tokenizer.readIdentifier();
				if (first == null) {
					return null;
				}
				return "." + readQualifiedNameTail(first);
			}
			String first = tokenizer.readIdentifier();
			if (first == null) {
				return null;
			}
			return readQualifiedNameTail(first);
		}

		private String readQualifiedName() {
			String first = tokenizer.readIdentifier();
			if (first == null) {
				return null;
			}
			return readQualifiedNameTail(first);
		}

		private String readQualifiedNameTail(String first) {
			StringBuilder builder = new StringBuilder(first);
			while (tokenizer.match(".")) {
				String next = tokenizer.readIdentifier();
				if (next == null) {
					break;
				}
				builder.append('.').append(next);
			}
			return builder.toString();
		}

		private void skipFieldRemainder() {
			int bracketDepth = 0;
			while (tokenizer.hasNext()) {
				String text = tokenizer.nextText();
				if ("[".equals(text)) {
					bracketDepth++;
				} else if ("]".equals(text)) {
					bracketDepth = Math.max(0, bracketDepth - 1);
				} else if (";".equals(text) && bracketDepth == 0) {
					return;
				} else if ("{".equals(text)) {
					tokenizer.skipBlock();
				}
			}
		}

		private void skipStatementOrBlock() {
			if (tokenizer.peekText("{")) {
				tokenizer.skipBlock();
				tokenizer.consume(";");
				return;
			}
			while (tokenizer.hasNext()) {
				String text = tokenizer.nextText();
				if ("{".equals(text)) {
					tokenizer.skipBlock();
					continue;
				}
				if (";".equals(text)) {
					return;
				}
			}
		}

		private static String buildFullName(
			String packageName,
			String parentFullName,
			String name
		) {
			if (parentFullName != null && !parentFullName.isBlank()) {
				return parentFullName + "." + name;
			}
			if (packageName != null && !packageName.isBlank()) {
				return packageName + "." + name;
			}
			return name;
		}
	}

	private static final class ProtoTokenizer {

		private final List<Token> tokens = new ArrayList<>();
		private int index = 0;

		ProtoTokenizer(String text) {
			tokenize(text == null ? "" : text);
		}

		boolean hasNext() {
			return index < tokens.size();
		}

		boolean match(String value) {
			if (peekText(value)) {
				index++;
				return true;
			}
			return false;
		}

		boolean peekText(String value) {
			if (!hasNext()) {
				return false;
			}
			return value.equals(tokens.get(index).text);
		}

		Token next() {
			if (!hasNext()) {
				return null;
			}
			return tokens.get(index++);
		}

		String nextText() {
			Token token = next();
			return token == null ? null : token.text;
		}

		boolean consume(String value) {
			return match(value);
		}

		String readIdentifier() {
			if (!hasNext()) {
				return null;
			}
			Token token = tokens.get(index);
			if (token.type == TokenType.IDENT) {
				index++;
				return token.text;
			}
			return null;
		}

		String readString() {
			if (!hasNext()) {
				return null;
			}
			Token token = tokens.get(index);
			if (token.type == TokenType.STRING) {
				index++;
				return token.text;
			}
			return null;
		}

		void skipBlock() {
			if (!match("{")) {
				return;
			}
			int depth = 1;
			while (hasNext()) {
				String text = nextText();
				if ("{".equals(text)) {
					depth++;
				} else if ("}".equals(text)) {
					depth--;
					if (depth == 0) {
						return;
					}
				}
			}
		}

		private void tokenize(String text) {
			int i = 0;
			while (i < text.length()) {
				char ch = text.charAt(i);
				if (Character.isWhitespace(ch)) {
					i++;
					continue;
				}
				if (ch == '/' && i + 1 < text.length()) {
					char next = text.charAt(i + 1);
					if (next == '/') {
						i = skipLineComment(text, i + 2);
						continue;
					}
					if (next == '*') {
						i = skipBlockComment(text, i + 2);
						continue;
					}
				}
				if (ch == '"' || ch == '\'') {
					Token token = readStringToken(text, i, ch);
					tokens.add(token);
					i = token.end;
					continue;
				}
				if (isIdentifierStart(ch)) {
					Token token = readIdentifierToken(text, i);
					tokens.add(token);
					i = token.end;
					continue;
				}
				if (isDigit(ch) || ch == '-') {
					Token token = readNumberToken(text, i);
					tokens.add(token);
					i = token.end;
					continue;
				}
				if (isSymbol(ch)) {
					tokens.add(new Token(TokenType.SYMBOL, String.valueOf(ch), i + 1));
					i++;
					continue;
				}
				i++;
			}
		}

		private static int skipLineComment(
			String text,
			int start
		) {
			int i = start;
			while (i < text.length() && text.charAt(i) != '\n') {
				i++;
			}
			return i;
		}

		private static int skipBlockComment(
			String text,
			int start
		) {
			int i = start;
			while (i + 1 < text.length()) {
				if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
					return i + 2;
				}
				i++;
			}
			return text.length();
		}

		private static Token readStringToken(
			String text,
			int start,
			char quote
		) {
			int i = start + 1;
			StringBuilder builder = new StringBuilder();
			while (i < text.length()) {
				char ch = text.charAt(i);
				if (ch == '\\' && i + 1 < text.length()) {
					builder.append(text.charAt(i + 1));
					i += 2;
					continue;
				}
				if (ch == quote) {
					return new Token(TokenType.STRING, builder.toString(), i + 1);
				}
				builder.append(ch);
				i++;
			}
			return new Token(TokenType.STRING, builder.toString(), i);
		}

		private static Token readIdentifierToken(
			String text,
			int start
		) {
			int i = start;
			while (i < text.length() && isIdentifierPart(text.charAt(i))) {
				i++;
			}
			return new Token(TokenType.IDENT, text.substring(start, i), i);
		}

		private static Token readNumberToken(
			String text,
			int start
		) {
			int i = start;
			if (text.charAt(i) == '-') {
				i++;
			}
			while (i < text.length() && isDigit(text.charAt(i))) {
				i++;
			}
			return new Token(TokenType.IDENT, text.substring(start, i), i);
		}

		private static boolean isIdentifierStart(char ch) {
			return Character.isLetter(ch) || ch == '_';
		}

		private static boolean isIdentifierPart(char ch) {
			return Character.isLetterOrDigit(ch) || ch == '_';
		}

		private static boolean isDigit(char ch) {
			return ch >= '0' && ch <= '9';
		}

		private static boolean isSymbol(char ch) {
			return "{}[]()<>=;,:.".indexOf(ch) >= 0;
		}

		private enum TokenType {
			IDENT,
			STRING,
			SYMBOL
		}

		private static final class Token {

			final TokenType type;
			final String text;
			final int end;

			Token(
				TokenType type,
				String text,
				int end
			) {
				this.type = type;
				this.text = text;
				this.end = end;
			}
		}
	}

	private static final class ProtoFile {

		final String fileName;
		final String path;
		String packageName = "";
		final List<String> imports = new ArrayList<>();
		final List<ProtoMessage> messages = new ArrayList<>();
		final List<ProtoEnum> enums = new ArrayList<>();

		ProtoFile(
			String fileName,
			String path
		) {
			this.fileName = fileName == null ? "" : fileName;
			this.path = path == null ? "" : path;
		}
	}

	private static final class ProtoMessage {

		String name;
		String fullName;
		String packageName;
		String displayName;
		List<ProtoField> fields = new ArrayList<>();
	}

	private static final class ProtoEnum {

		String name;
		String fullName;
		List<String> values = new ArrayList<>();
	}

	private static final class ProtoField {

		String name;
		String type;
		boolean repeated;
		boolean isMap;
		String mapKeyType;
		String mapValueType;
	}

	private static final class ProtoMessageSelection {

		final String display;
		final ProtoRegistry registry;
		final ProtoMessage message;
		final String qualifiedName;

		ProtoMessageSelection(
			String display,
			ProtoRegistry registry,
			ProtoMessage message
		) {
			this.display = display;
			this.registry = registry;
			this.message = message;
			this.qualifiedName = message == null ? null : message.fullName;
		}

		@Override
		public String toString() {
			return display;
		}
	}

	private String jsonName(
		String fallback,
		com.intellij.psi.PsiAnnotation annotation,
		boolean useAnnotations
	) {
		if (!useAnnotations || annotation == null) {
			return fallback;
		}
		com.intellij.psi.PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
		if (value != null) {
			String text = value.getText();
			if (text != null && text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
				return text.substring(1, text.length() - 1);
			}
		}
		return fallback;
	}

	private Set<String> ignoredJsonProperties(PsiClass psiClass) {
		com.intellij.psi.PsiAnnotation annotation =
			psiClass.getAnnotation("com.fasterxml.jackson.annotation.JsonIgnoreProperties");
		if (annotation == null) {
			return Set.of();
		}
		Set<String> result = new HashSet<>();
		com.intellij.psi.PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
		if (value instanceof com.intellij.psi.PsiArrayInitializerMemberValue array) {
			for (com.intellij.psi.PsiAnnotationMemberValue entry : array.getInitializers()) {
				String text = entry.getText();
				if (text != null && text.startsWith("\"") && text.endsWith("\"")) {
					result.add(text.substring(1, text.length() - 1));
				}
			}
		} else if (value != null) {
			String text = value.getText();
			if (text != null && text.startsWith("\"") && text.endsWith("\"")) {
				result.add(text.substring(1, text.length() - 1));
			}
		}
		return result;
	}

	private void showLog(String message) {
		responseLogsArea.setText(message);
	}

	private void appendResponseLog(String message) {
		String existing = responseLogsArea.getText();
		if (existing == null || existing.isBlank()) {
			responseLogsArea.setText(message);
			return;
		}
		responseLogsArea.setText(existing + "\n" + message);
	}

	private void promptSaveDownload(DownloadResult result) {
		if (result == null || result.bodyBytes == null) {
			return;
		}
		String suggestedName = suggestDownloadFilename(result.headers);
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save Response");
		if (suggestedName != null && !suggestedName.isBlank()) {
			chooser.setSelectedFile(new File(suggestedName));
		}
		int dialogResult = chooser.showSaveDialog(root);
		if (dialogResult != JFileChooser.APPROVE_OPTION) {
			appendResponseLog("Download canceled.");
			return;
		}
		File file = chooser.getSelectedFile();
		if (file == null) {
			appendResponseLog("Download canceled.");
			return;
		}
		try {
			Files.write(file.toPath(), result.bodyBytes);
			appendResponseLog("Saved response to: " + file.getAbsolutePath());
		} catch (Exception error) {
			appendResponseLog("Failed to save response: " + error.getMessage());
		}
	}

	private String suggestDownloadFilename(Map<String, List<String>> headers) {
		String contentDisposition = firstHeaderValue(headers, "content-disposition");
		String filename = extractFilenameFromDisposition(contentDisposition);
		if (filename != null && !filename.isBlank()) {
			return filename;
		}
		return "download.bin";
	}

	private String extractFilenameFromDisposition(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String lower = value.toLowerCase(Locale.ROOT);
		int index = lower.indexOf("filename*=");
		if (index >= 0) {
			String part = value.substring(index + "filename*=".length()).trim();
			part = trimDispositionPart(part);
			int charsetIndex = part.indexOf("''");
			if (charsetIndex >= 0) {
				String encoded = part.substring(charsetIndex + 2);
				try {
					return URLDecoder.decode(stripQuotes(encoded), StandardCharsets.UTF_8);
				} catch (Exception ignored) {
					return stripQuotes(encoded);
				}
			}
			return stripQuotes(part);
		}
		index = lower.indexOf("filename=");
		if (index >= 0) {
			String part = value.substring(index + "filename=".length()).trim();
			part = trimDispositionPart(part);
			return stripQuotes(part);
		}
		return null;
	}

	private String trimDispositionPart(String value) {
		if (value == null) {
			return null;
		}
		int semicolon = value.indexOf(';');
		if (semicolon >= 0) {
			return value.substring(0, semicolon).trim();
		}
		return value.trim();
	}

	private String stripQuotes(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
			(trimmed.startsWith("'") && trimmed.endsWith("'"))) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}

	private String firstHeaderValue(Map<String, List<String>> headers, String name) {
		if (headers == null || name == null) {
			return null;
		}
		for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
			if (entry.getKey() == null) {
				continue;
			}
			if (!entry.getKey().equalsIgnoreCase(name)) {
				continue;
			}
			List<String> values = entry.getValue();
			if (values == null || values.isEmpty()) {
				return null;
			}
			return values.get(0);
		}
		return null;
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private String applyQueryParams(
		String url,
		List<HeaderEntryState> params
	) {
		if (url == null) {
			return "";
		}
		if (params == null || params.isEmpty()) {
			return url;
		}
		String base = url;
		String fragment = "";
		int hashIndex = url.indexOf('#');
		if (hashIndex >= 0) {
			base = url.substring(0, hashIndex);
			fragment = url.substring(hashIndex);
		}
		StringBuilder builder = new StringBuilder(base);
		boolean hasQuery = base.contains("?");
		boolean needsSeparator = hasQuery && !base.endsWith("?") && !base.endsWith("&");
		Set<String> existingPairs = collectQueryPairs(base);

		for (HeaderEntryState param : params) {
			if (param == null || !param.enabled) {
				continue;
			}
			String name = param.name == null ? "" : param.name.trim();
			if (name.isEmpty()) {
				continue;
			}
			String value = param.value == null ? "" : param.value;
			String dedupeKey = name + "\u0000" + value;
			if (existingPairs.contains(dedupeKey)) {
				continue;
			}
			if (!hasQuery) {
				builder.append('?');
				hasQuery = true;
				needsSeparator = false;
			} else if (needsSeparator) {
				builder.append('&');
			}
			builder.append(encodeParam(name));
			builder.append('=');
			builder.append(encodeParam(value));
			needsSeparator = true;
		}

		return builder.append(fragment).toString();
	}

	private String encodeParam(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private void syncParamsFromUrlField() {
		if (isLoading || isSyncingParamsFromUrl || currentNode == null || currentNode.requestType != RequestType.HTTP) {
			return;
		}
		List<HeaderEntryState> merged = mergeParamsWithUrl(paramsTableModel.getHeaders(), httpUrlField.getText());
		if (merged == null) {
			return;
		}
		isSyncingParamsFromUrl = true;
		try {
			paramsTableModel.setHeaders(merged, true);
		} finally {
			isSyncingParamsFromUrl = false;
		}
	}

	private void scheduleParamsSyncFromUrl() {
		if (isLoading || isSyncingParamsFromUrl) {
			return;
		}
		urlParamSyncTimer.restart();
	}

	private List<HeaderEntryState> mergeParamsWithUrl(
		List<HeaderEntryState> params,
		String url
	) {
		List<HeaderEntryState> fromUrl = parseQueryParams(url);
		if (fromUrl.isEmpty()) {
			return params == null ? List.of() : params;
		}
		List<HeaderEntryState> merged = new ArrayList<>();
		if (params != null) {
			for (HeaderEntryState entry : params) {
				if (entry == null) {
					continue;
				}
				HeaderEntryState clone = new HeaderEntryState();
				clone.id = entry.id;
				clone.name = entry.name;
				clone.value = entry.value;
				clone.enabled = entry.enabled;
				merged.add(clone);
			}
		}
		for (HeaderEntryState entry : fromUrl) {
			HeaderEntryState existing = findParamByName(merged, entry.name);
			if (existing == null) {
				merged.add(entry);
			} else {
				existing.value = entry.value;
				existing.enabled = true;
			}
		}
		return merged;
	}

	private HeaderEntryState findParamByName(
		List<HeaderEntryState> params,
		String name
	) {
		if (name == null) {
			return null;
		}
		for (HeaderEntryState entry : params) {
			if (entry != null && name.equals(entry.name)) {
				return entry;
			}
		}
		return null;
	}

	private List<HeaderEntryState> parseQueryParams(String url) {
		List<HeaderEntryState> result = new ArrayList<>();
		if (url == null || url.isBlank()) {
			return result;
		}
		int queryIndex = url.indexOf('?');
		if (queryIndex < 0) {
			return result;
		}
		int hashIndex = url.indexOf('#', queryIndex);
		String query = hashIndex >= 0 ? url.substring(queryIndex + 1, hashIndex) : url.substring(queryIndex + 1);
		if (query.isBlank()) {
			return result;
		}
		String[] parts = query.split("&");
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			String name;
			String value;
			int eq = part.indexOf('=');
			if (eq >= 0) {
				name = decodeParam(part.substring(0, eq));
				value = decodeParam(part.substring(eq + 1));
			} else {
				name = decodeParam(part);
				value = "";
			}
			if (name == null || name.isBlank()) {
				continue;
			}
			HeaderEntryState entry = new HeaderEntryState();
			entry.id = java.util.UUID.randomUUID().toString();
			entry.name = name;
			entry.value = value;
			entry.enabled = true;
			result.add(entry);
		}
		return result;
	}

	private Set<String> collectQueryPairs(String url) {
		Set<String> pairs = new HashSet<>();
		for (HeaderEntryState entry : parseQueryParams(url)) {
			String name = entry.name == null ? "" : entry.name;
			String value = entry.value == null ? "" : entry.value;
			pairs.add(name + "\u0000" + value);
		}
		return pairs;
	}

	private String decodeParam(String value) {
		try {
			return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			return value;
		}
	}

	private List<HeaderEntryState> cloneHeaders(List<HeaderEntryState> headers) {
		List<HeaderEntryState> copy = new ArrayList<>();
		if (headers == null) {
			return copy;
		}
		for (HeaderEntryState entry : headers) {
			HeaderEntryState clone = new HeaderEntryState();
			clone.id = entry.id;
			clone.name = entry.name;
			clone.value = entry.value;
			clone.enabled = entry.enabled;
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

	private String toJson(Object value) {
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
		} catch (Exception e) {
			return String.valueOf(value);
		}
	}

	private void openRequestWindow() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST ||
			currentNode.requestType == RequestType.CHAIN) {
			return;
		}
		JDialog dialog = new JDialog();
		dialog.setTitle("Request Editor - " + currentNode.name);
		JTabbedPane tabs = new JTabbedPane();

		EditorTextField bodyField = new JsonBodyEditorField(project, requestBodyArea.getDocument());
		EditorTextField beforeField =
			new EditorTextField(beforeScriptArea.getDocument(), project, scriptFileType, false, false);
		beforeField.setOneLineMode(false);
		EditorTextField afterField =
			new EditorTextField(afterScriptArea.getDocument(), project, scriptFileType, false, false);
		afterField.setOneLineMode(false);

		tabs.add("Body", new JBScrollPane(bodyField));
		tabs.add("Before Request", new JBScrollPane(beforeField));
		tabs.add("After Request", new JBScrollPane(afterField));

		dialog.getContentPane().add(tabs);
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(root);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private void openBeforeRequestWindow() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST ||
			currentNode.requestType == RequestType.CHAIN) {
			return;
		}
		JDialog dialog = new JDialog();
		dialog.setTitle("Before Request - " + currentNode.name);
		EditorTextField beforeField =
			new EditorTextField(beforeScriptArea.getDocument(), project, scriptFileType, false, false);
		beforeField.setOneLineMode(false);
		dialog.getContentPane().add(new JBScrollPane(beforeField));
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(root);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private void openAfterRequestWindow() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST ||
			currentNode.requestType == RequestType.CHAIN) {
			return;
		}
		JDialog dialog = new JDialog();
		dialog.setTitle("After Request - " + currentNode.name);
		EditorTextField afterField =
			new EditorTextField(afterScriptArea.getDocument(), project, scriptFileType, false, false);
		afterField.setOneLineMode(false);
		dialog.getContentPane().add(new JBScrollPane(afterField));
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(root);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private void openResponseWindow() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST ||
			currentNode.requestType == RequestType.CHAIN) {
			return;
		}
		JDialog dialog = new JDialog();
		dialog.setTitle("Response Viewer - " + currentNode.name);
		JTabbedPane tabs = new JTabbedPane();

		EditorTextField bodyField =
			new EditorTextField(responseBodyArea.getDocument(), project, JsonFileType.INSTANCE, false, false);
		bodyField.setOneLineMode(false);
		EditorTextField headersField =
			new EditorTextField(responseHeadersArea.getDocument(), project, JsonFileType.INSTANCE, false, false);
		headersField.setOneLineMode(false);
		JBTextArea logsArea = new JBTextArea();
		logsArea.setDocument(responseLogsArea.getDocument());

		tabs.add("Response Body", new JBScrollPane(bodyField));
		tabs.add("Response Headers", new JBScrollPane(headersField));
		tabs.add("Logs", new JBScrollPane(logsArea));

		dialog.getContentPane().add(tabs);
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(root);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private void openChainWindow() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST ||
			currentNode.requestType != RequestType.CHAIN) {
			return;
		}
		JDialog dialog = new JDialog();
		dialog.setTitle("Chain Viewer - " + currentNode.name);
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

	private void openSettingsDialog() {
		JDialog dialog = new JDialog();
		dialog.setTitle("Settings");
		JTabbedPane tabs = new JTabbedPane();

		JPanel headersPanel = new JPanel(new BorderLayout());
		JTable presetsTable = new JTable(headerPresetTableModel);
		headerPresetTableModel.setPresets(headerPresets);
		presetsTable.setFillsViewportHeight(true);
		headersPanel.add(new JBScrollPane(presetsTable), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton addPreset = new JButton("Add");
		JButton removePreset = new JButton("Remove");
		actions.add(addPreset);
		actions.add(removePreset);
		addPreset.addActionListener(e -> headerPresetTableModel.addEmptyRow());
		removePreset.addActionListener(e -> {
			int row = presetsTable.getSelectedRow();
			headerPresetTableModel.removeRow(row);
		});
		headersPanel.add(actions, BorderLayout.SOUTH);

		tabs.add("Headers", headersPanel);

		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveButton = new JButton("Save");
		JButton cancelButton = new JButton("Cancel");
		footer.add(saveButton);
		footer.add(cancelButton);
		saveButton.addActionListener(e -> {
			if (presetsTable.isEditing()) {
				TableCellEditor editor = presetsTable.getCellEditor();
				if (editor != null) {
					editor.stopCellEditing();
				}
			}
			headerPresets = headerPresetTableModel.getPresets();
			stateService.saveHeaderPresets(headerPresets);
			if (currentNode != null && currentNode.requestType == RequestType.GRPC) {
				updateHeaderNameEditor(RequestType.GRPC);
			} else {
				updateHeaderNameEditor(RequestType.HTTP);
			}
			dialog.dispose();
		});
		cancelButton.addActionListener(e -> dialog.dispose());

		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(tabs, BorderLayout.CENTER);
		dialog.getContentPane().add(footer, BorderLayout.SOUTH);
		dialog.setSize(700, 500);
		dialog.setLocationRelativeTo(root);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private void startDebugCall() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST ||
			currentNode.requestType == RequestType.CHAIN) {
			return;
		}
		saveCurrentEditors();
		if (debugCallSession != null) {
			debugCallSession.abandon(true);
		}
		debugCallSession = new DebugCallSession(currentNode.id, currentNode.requestType);
		debugCallSession.open();
	}

	private static class ExecutionResult {

		final int statusCode;
		final String statusMessage;
		final String responseBody;
		final String responseHeaders;
		final String logs;

		ExecutionResult(
			int statusCode,
			String statusMessage,
			String responseBody,
			String responseHeaders,
			String logs
		) {
			this.statusCode = statusCode;
			this.statusMessage = statusMessage;
			this.responseBody = responseBody;
			this.responseHeaders = responseHeaders;
			this.logs = logs;
		}

		static ExecutionResult failure(List<String> logs) {
			return new ExecutionResult(0, "", "", "{}", String.join("\n", logs));
		}
	}

	private static class DownloadResult {

		final ExecutionResult result;
		final byte[] bodyBytes;
		final Map<String, List<String>> headers;

		DownloadResult(ExecutionResult result, byte[] bodyBytes, Map<String, List<String>> headers) {
			this.result = result;
			this.bodyBytes = bodyBytes;
			this.headers = headers;
		}

		static DownloadResult failure(List<String> logs) {
			return new DownloadResult(ExecutionResult.failure(logs), null, Map.of());
		}
	}

	private static final class DebugStageResult {

		final String stageName;
		final long durationNanos;
		final List<String> lines;
		final boolean hasNext;

		DebugStageResult(
			String stageName,
			long durationNanos,
			List<String> lines,
			boolean hasNext
		) {
			this.stageName = stageName;
			this.durationNanos = durationNanos;
			this.lines = lines;
			this.hasNext = hasNext;
		}
	}

	private class DebugCallSession {

		private final String requestId;
		private final RequestType requestType;
		private final RequestDetailsState details;
		private final RequestStatusState status;

		private JDialog dialog;
		private JBTextArea outputArea;
		private JBTextField inlineScriptField;
		private JButton inlineRunButton;
		private JButton nextButton;
		private JButton abandonButton;

		private int stepIndex = 1;
		private volatile boolean abandoned = false;
		private Future<?> pendingTask;

		private VarsStore vars;
		private ScriptHelpers helpers;
		private ScriptLogger logger;
		private List<String> logs;
		private List<String> beforeLogs = List.of();
		private List<String> afterLogs = List.of();
		private ScriptRequest rawRequest;
		private ScriptRequest beforeRequest;
		private ScriptRequest afterRequest;
		private ScriptRequest currentRequest;
		private String templatedBody = "";
		private List<HeaderEntryState> templatedHeaders = List.of();
		private List<HeaderEntryState> templatedParams = List.of();
		private String templatedUrl = "";
		private HttpExecutionResponse httpResponse;
		private GrpcExecutionResponse grpcResponse;
		private boolean beforeFailed = false;
		private boolean requestFailed = false;
		private String validationError;

		private DebugCallSession(String requestId, RequestType requestType) {
			this.requestId = requestId;
			this.requestType = requestType;
			this.details = stateService.getRequestDetails(requestId);
			this.status = stateService.getRequestStatus(requestId);
		}

		private void open() {
			String title = "Debug Call";
			NodeState node = stateService.findNode(requestId);
			if (node != null && node.name != null && !node.name.isBlank()) {
				title += " - " + node.name;
			}
			dialog = new JDialog();
			dialog.setTitle(title);
			outputArea = new JBTextArea();
			outputArea.setEditable(false);
			outputArea.setLineWrap(true);
			outputArea.setWrapStyleWord(true);

			inlineScriptField = new JBTextField();
			inlineScriptField.setColumns(30);
			inlineScriptField.setToolTipText("Inline JS");

			inlineRunButton = new JButton(AllIcons.Actions.Execute);
			inlineRunButton.setToolTipText("Run Script");
			inlineRunButton.setMargin(new Insets(0, 0, 0, 0));
			inlineRunButton.setPreferredSize(new Dimension(28, 28));
			inlineRunButton.addActionListener(e -> runInlineScript());

			nextButton = new JButton("Next");
			abandonButton = new JButton("Abandon");
			nextButton.addActionListener(e -> advance());
			abandonButton.addActionListener(e -> abandon(true));

			JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			actions.add(inlineScriptField);
			actions.add(inlineRunButton);
			actions.add(nextButton);
			actions.add(abandonButton);

			dialog.getContentPane().setLayout(new BorderLayout());
			dialog.getContentPane().add(new JBScrollPane(outputArea), BorderLayout.CENTER);
			dialog.getContentPane().add(actions, BorderLayout.SOUTH);
			dialog.setSize(900, 700);
			dialog.setLocationRelativeTo(root);
			dialog.setModal(false);
			dialog.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					abandon(true);
				}

				@Override
				public void windowClosed(WindowEvent e) {
					abandon(false);
				}
			});

			outputArea.setText("");
			appendStage(buildInitialStage());
			dialog.setVisible(true);
		}

		private void advance() {
			if (abandoned) {
				return;
			}
			nextButton.setEnabled(false);
			pendingTask = ApplicationManager.getApplication().executeOnPooledThread(() -> {
				DebugStageResult result = runStage(stepIndex);
				if (result == null || abandoned) {
					return;
				}
				SwingUtilities.invokeLater(() -> {
					if (abandoned) {
						return;
					}
					appendStage(result);
					stepIndex++;
					nextButton.setEnabled(result.hasNext);
				});
			});
		}

		private DebugStageResult runStage(int step) {
			return switch (step) {
				case 1 -> buildBeforeStage();
				case 2 -> buildResponseStage();
				case 3 -> buildAfterStage();
				case 4 -> buildFinalStage();
				default -> null;
			};
		}

		private void abandon(boolean closeDialog) {
			if (abandoned) {
				return;
			}
			abandoned = true;
			if (pendingTask != null) {
				pendingTask.cancel(true);
			}
			SwingUtilities.invokeLater(() -> {
				if (outputArea != null) {
					outputArea.setText("");
				}
				if (closeDialog && dialog != null && dialog.isDisplayable()) {
					dialog.dispose();
				}
			});
			if (debugCallSession == this) {
				debugCallSession = null;
			}
		}

		private void runInlineScript() {
			if (abandoned) {
				return;
			}
			String script = inlineScriptField.getText();
			if (script == null || script.isBlank()) {
				return;
			}
			inlineRunButton.setEnabled(false);
			pendingTask = ApplicationManager.getApplication().executeOnPooledThread(() -> {
				DebugStageResult result = executeInlineScript(script);
				if (result == null || abandoned) {
					return;
				}
				SwingUtilities.invokeLater(() -> {
					if (abandoned) {
						return;
					}
					appendStage(result);
					inlineRunButton.setEnabled(true);
				});
			});
		}

		private DebugStageResult executeInlineScript(String script) {
			long start = System.nanoTime();
			List<String> lines = new ArrayList<>();
			if (vars == null || logger == null || helpers == null) {
				lines.add("Context not ready. Run Next first.");
				long duration = System.nanoTime() - start;
				return new DebugStageResult("Inline Script", duration, lines, nextButton.isEnabled());
			}
			if (logs == null) {
				logs = new ArrayList<>();
			}
			int logStart = logs.size();
			ScriptRequest contextRequest = afterRequest != null ? afterRequest : currentRequest;
			if (contextRequest == null) {
				contextRequest = new ScriptRequest("", List.of(), List.of());
			}
			Object response = null;
			if (requestType == RequestType.HTTP) {
				response = httpResponse;
			} else if (requestType == RequestType.GRPC) {
				response = grpcResponse;
			}
			try {
				scriptRuntime.runScript(
					script,
					new ScriptContext(vars, logger, helpers, contextRequest, rawRequest, response)
				);
			} catch (Exception error) {
				logs.add("Inline script error: " + error.getMessage());
			}
			List<String> scriptLogs =
				logs.size() == logStart ? List.of() : new ArrayList<>(logs.subList(logStart, logs.size()));
			lines.addAll(formatLogs("Inline script logs", scriptLogs));
			long duration = System.nanoTime() - start;
			return new DebugStageResult("Inline Script", duration, lines, nextButton.isEnabled());
		}

		private DebugStageResult buildInitialStage() {
			long start = System.nanoTime();
			List<String> lines = new ArrayList<>();
			lines.add("Request Id: " + requestId);
			if (requestType == RequestType.HTTP) {
				String method = details == null || details.method == null ? "GET" : details.method;
				String url = details == null || details.url == null ? "" : details.url;
				lines.add("Method: " + method);
				lines.add("URL: " + (url.isBlank() ? "<missing>" : url));
			} else {
				String target = details == null || details.target == null ? "" : details.target;
				String service = details == null || details.service == null ? "" : details.service;
				String method = details == null || details.grpcMethod == null ? "" : details.grpcMethod;
				lines.add("Target: " + (target.isBlank() ? "<missing>" : target));
				lines.add("Service: " + (service.isBlank() ? "<missing>" : service));
				lines.add("Method: " + (method.isBlank() ? "<missing>" : method));
			}
			ScriptRequest snapshot = new ScriptRequest(
				status == null ? "" : status.requestBody,
				status == null ? List.of() : status.requestHeaders,
				status == null ? List.of() : status.requestParams
			);
			if (status != null) {
				snapshot.setFormData(cloneFormData(status.formData));
				snapshot.setBinaryFilePath(status.binaryFilePath);
			}
			lines.addAll(formatRequestSnapshot(snapshot));
			long duration = System.nanoTime() - start;
			return new DebugStageResult("Current Request", duration, lines, true);
		}

		private DebugStageResult buildBeforeStage() {
			long start = System.nanoTime();
			List<String> lines = new ArrayList<>();
			String body = status == null ? "" : safe(status.requestBody);
			List<HeaderEntryState> headers = status == null ? List.of() : status.requestHeaders;
			List<HeaderEntryState> params = status == null ? List.of() : status.requestParams;
			List<FormEntryState> formData = status == null ? List.of() : status.formData;
			String binaryFilePath = status == null ? "" : safe(status.binaryFilePath);
			String before = status == null ? "" : safe(status.beforeScript);

			logs = new ArrayList<>();
			logger = message -> logs.add(message);
			helpers = new ScriptHelpers(logger);
			vars = new VarsStore();
			rawRequest = new ScriptRequest(body, cloneHeaders(headers), cloneHeaders(params));
			rawRequest.setFormData(cloneFormData(formData));
			rawRequest.setBinaryFilePath(binaryFilePath);
			beforeRequest = new ScriptRequest(body, cloneHeaders(headers), cloneHeaders(params));
			beforeRequest.setFormData(cloneFormData(formData));
			beforeRequest.setBinaryFilePath(binaryFilePath);

			validationError = validateDetails();
			if (validationError != null) {
				beforeFailed = true;
				lines.add("Error: " + validationError);
			} else {
				try {
					scriptRuntime.runScript(
						before,
						new ScriptContext(vars, logger, helpers, beforeRequest, rawRequest, null)
					);
				} catch (Exception error) {
					logs.add("Before request error: " + error.getMessage());
					beforeFailed = true;
				}
			}
			beforeLogs = logs.isEmpty() ? List.of() : new ArrayList<>(logs);

			if (!beforeFailed) {
				Map<String, Object> varsSnapshot = vars.entries();
				templatedBody = templateEngine.applyToBody(beforeRequest.getBody(), varsSnapshot);
				templatedHeaders = templateEngine.applyToHeaders(beforeRequest.getHeaders(), varsSnapshot);
				templatedParams = templateEngine.applyToParams(beforeRequest.getParams(), varsSnapshot);
				List<FormEntryState> templatedFormData =
					templateEngine.applyToFormData(beforeRequest.getFormData(), varsSnapshot);
				String templatedBinaryPath = templateEngine.applyToText(
					beforeRequest.getBinaryFilePath(),
					varsSnapshot
				);
				if (requestType == RequestType.HTTP) {
					String url = details == null || details.url == null ? "" : details.url;
					String templatedUrlBase = templateEngine.applyToText(url, varsSnapshot);
					templatedUrl = applyQueryParams(templatedUrlBase, templatedParams);
				}
				currentRequest = new ScriptRequest(templatedBody, templatedHeaders, templatedParams);
				currentRequest.setFormData(cloneFormData(templatedFormData));
				currentRequest.setBinaryFilePath(templatedBinaryPath);
			} else {
				templatedBody = beforeRequest.getBody();
				templatedHeaders = beforeRequest.getHeaders();
				templatedParams = beforeRequest.getParams();
				templatedUrl = details == null || details.url == null ? "" : details.url;
				currentRequest = new ScriptRequest(templatedBody, templatedHeaders, templatedParams);
				currentRequest.setFormData(cloneFormData(beforeRequest.getFormData()));
				currentRequest.setBinaryFilePath(beforeRequest.getBinaryFilePath());
			}

			if (requestType == RequestType.HTTP) {
				String method = details == null || details.method == null ? "GET" : details.method;
				lines.add("Method: " + method);
				lines.add("URL: " + (templatedUrl == null || templatedUrl.isBlank() ? "<missing>" : templatedUrl));
			} else {
				String target = details == null || details.target == null ? "" : details.target;
				String service = details == null || details.service == null ? "" : details.service;
				String method = details == null || details.grpcMethod == null ? "" : details.grpcMethod;
				lines.add("Target: " + (target.isBlank() ? "<missing>" : target));
				lines.add("Service: " + (service.isBlank() ? "<missing>" : service));
				lines.add("Method: " + (method.isBlank() ? "<missing>" : method));
			}

			lines.add("Request:");
			lines.addAll(formatRequestSnapshot(currentRequest));
			lines.addAll(formatLogs("Before request logs", beforeLogs));
			if (beforeFailed) {
				lines.add("Request will not be sent.");
			}

			long duration = System.nanoTime() - start;
			return new DebugStageResult("Sent Request", duration, lines, true);
		}

		private DebugStageResult buildResponseStage() {
			long start = System.nanoTime();
			List<String> lines = new ArrayList<>();
			if (beforeFailed) {
				lines.add("Request skipped because before request failed.");
				long duration = System.nanoTime() - start;
				return new DebugStageResult("Response Received", duration, lines, true);
			}
			try {
				if (requestType == RequestType.HTTP) {
					String method = details == null || details.method == null ? "GET" : details.method;
					String payloadType = details == null ? "RAW" : details.payloadType;
					List<FormEntryState> formData =
						currentRequest == null ? List.of() : currentRequest.getFormData();
					String binaryPath =
						currentRequest == null ? "" : currentRequest.getBinaryFilePath();
					httpResponse = httpExecutor.execute(
						method,
						templatedUrl,
						templatedHeaders,
						templatedBody,
						formData,
						binaryPath,
						resolvePayloadType(payloadType)
					);
				} else {
					grpcResponse = grpcExecutor.execute(
						details.target,
						details.service,
						details.grpcMethod,
						templatedBody,
						templatedHeaders
					);
				}
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				return null;
			} catch (Exception error) {
				requestFailed = true;
				if (requestType == RequestType.HTTP) {
					logs.add("Request failed: " + error.getMessage());
				} else {
					logs.add("gRPC request failed: " + error.getMessage());
				}
			}

			if (requestFailed) {
				lines.add("Request failed. No response received.");
			} else if (requestType == RequestType.HTTP && httpResponse != null) {
				lines.add("Status: " + httpResponse.statusCode);
				lines.addAll(formatResponseSnapshot(httpResponse.body, httpResponse.headers));
			} else if (requestType == RequestType.GRPC && grpcResponse != null) {
				lines.add("Status: " + grpcResponse.statusCode + " " + safe(grpcResponse.statusMessage));
				lines.addAll(formatResponseSnapshot(grpcResponse.body, grpcResponse.headers));
			} else {
				lines.add("No response received.");
			}

			long duration = System.nanoTime() - start;
			return new DebugStageResult("Response Received", duration, lines, true);
		}

		private DebugStageResult buildAfterStage() {
			long start = System.nanoTime();
			List<String> lines = new ArrayList<>();
			if (beforeFailed) {
				lines.add("After request skipped because before request failed.");
			} else if (requestFailed || (requestType == RequestType.HTTP && httpResponse == null) ||
				(requestType == RequestType.GRPC && grpcResponse == null)) {
				lines.add("After request skipped because request failed.");
			} else {
				int logStart = logs.size();
				String after = status == null ? "" : safe(status.afterScript);
				afterRequest = new ScriptRequest(templatedBody, cloneHeaders(templatedHeaders), cloneHeaders(templatedParams));
				if (currentRequest != null) {
					afterRequest.setFormData(cloneFormData(currentRequest.getFormData()));
					afterRequest.setBinaryFilePath(currentRequest.getBinaryFilePath());
				}
				try {
					Object response = requestType == RequestType.HTTP ? httpResponse : grpcResponse;
					scriptRuntime.runScript(
						after,
						new ScriptContext(vars, logger, helpers, afterRequest, rawRequest, response)
					);
				} catch (Exception error) {
					logs.add("After request error: " + error.getMessage());
				}
				afterLogs = logs.size() == logStart ? List.of() : new ArrayList<>(logs.subList(logStart, logs.size()));
				lines.addAll(formatLogs("After request logs", afterLogs));
			}
			long duration = System.nanoTime() - start;
			return new DebugStageResult("After Request Logs", duration, lines, true);
		}

		private DebugStageResult buildFinalStage() {
			long start = System.nanoTime();
			List<String> lines = new ArrayList<>();
			ScriptRequest finalRequest = afterRequest != null ? afterRequest : currentRequest;
			if (finalRequest == null) {
				finalRequest = new ScriptRequest("", List.of(), List.of());
			}
			lines.addAll(formatRequestSnapshot(finalRequest));
			lines.addAll(formatLogs("Logs after request", logs == null ? List.of() : logs));
			long duration = System.nanoTime() - start;
			return new DebugStageResult("Final State", duration, lines, false);
		}

		private String validateDetails() {
			if (details == null) {
				return "Missing request details.";
			}
			if (requestType == RequestType.HTTP) {
				if (details.url == null || details.url.isBlank()) {
					return "Missing URL.";
				}
			} else {
				if (details.target == null || details.target.isBlank()) {
					return "Missing gRPC target.";
				}
				if (details.service == null || details.service.isBlank()) {
					return "Missing gRPC service.";
				}
				if (details.grpcMethod == null || details.grpcMethod.isBlank()) {
					return "Missing gRPC method.";
				}
			}
			return null;
		}

		private void appendStage(DebugStageResult result) {
			String header = result.stageName + " (" + formatDuration(result.durationNanos) + ")";
			if (outputArea.getDocument().getLength() > 0) {
				outputArea.append("\n");
			}
			outputArea.append("========================================\n");
			outputArea.append(header + "\n");
			outputArea.append("========================================\n");
			if (result.lines == null || result.lines.isEmpty()) {
				outputArea.append("<empty>\n");
				return;
			}
			for (String line : result.lines) {
				outputArea.append((line == null ? "" : line) + "\n");
			}
		}

		private String formatDuration(long nanos) {
			long ms = TimeUnit.NANOSECONDS.toMillis(nanos);
			long seconds = ms / 1000;
			long remain = ms % 1000;
			return seconds + "s:" + String.format("%03dms", remain);
		}

		private List<String> formatRequestSnapshot(ScriptRequest request) {
			List<String> lines = new ArrayList<>();
			if (request == null) {
				lines.add("<empty>");
				return lines;
			}
			lines.add("Body:");
			appendTextBlock(lines, request.getBody());
			appendHeaderEntries(lines, "Params", request.getParams());
			appendHeaderEntries(lines, "Headers", request.getHeaders());
			appendFormEntries(lines, request.getFormData());
			appendBinaryPath(lines, request.getBinaryFilePath());
			return lines;
		}

		private List<String> formatResponseSnapshot(
			String body,
			Map<String, List<String>> headers
		) {
			List<String> lines = new ArrayList<>();
			lines.add("Response Body:");
			appendTextBlock(lines, JsonUtils.prettyPrint(body));
			lines.add("Response Headers:");
			appendTextBlock(lines, toJson(headers));
			return lines;
		}

		private List<String> formatLogs(String title, List<String> logLines) {
			List<String> lines = new ArrayList<>();
			lines.add(title + ":");
			if (logLines == null || logLines.isEmpty()) {
				lines.add("<empty>");
				return lines;
			}
			lines.addAll(logLines);
			return lines;
		}

		private void appendHeaderEntries(
			List<String> lines,
			String label,
			List<HeaderEntryState> entries
		) {
			lines.add(label + ":");
			if (entries == null || entries.isEmpty()) {
				lines.add("<empty>");
				return;
			}
			for (HeaderEntryState entry : entries) {
				if (entry == null) {
					continue;
				}
				String name = entry.name == null ? "" : entry.name;
				String value = entry.value == null ? "" : entry.value;
				String enabled = entry.enabled ? "enabled" : "disabled";
				lines.add(name + ": " + (value.isBlank() ? "<empty>" : value) + " (" + enabled + ")");
			}
		}

		private void appendFormEntries(List<String> lines, List<FormEntryState> entries) {
			lines.add("Form Data:");
			if (entries == null || entries.isEmpty()) {
				lines.add("<empty>");
				return;
			}
			for (FormEntryState entry : entries) {
				if (entry == null) {
					continue;
				}
				String name = entry.name == null ? "" : entry.name;
				String value = entry.value == null ? "" : entry.value;
				String enabled = entry.enabled ? "enabled" : "disabled";
				String type = entry.file ? "file" : "text";
				lines.add(name + ": " + (value.isBlank() ? "<empty>" : value) + " (" + type + ", " + enabled + ")");
			}
		}

		private void appendBinaryPath(List<String> lines, String path) {
			lines.add("Binary File:");
			if (path == null || path.isBlank()) {
				lines.add("<empty>");
			} else {
				lines.add(path);
			}
		}

		private void appendTextBlock(List<String> lines, String text) {
			if (text == null || text.isBlank()) {
				lines.add("<empty>");
				return;
			}
			String[] parts = text.split("\\R", -1);
			Collections.addAll(lines, parts);
		}

		private boolean requestsEqual(ScriptRequest a, ScriptRequest b) {
			if (a == b) {
				return true;
			}
			if (a == null || b == null) {
				return false;
			}
			if (!Objects.equals(safe(a.getBody()), safe(b.getBody()))) {
				return false;
			}
			if (!headerListsEqual(a.getHeaders(), b.getHeaders())) {
				return false;
			}
			if (!headerListsEqual(a.getParams(), b.getParams())) {
				return false;
			}
			if (!formListsEqual(a.getFormData(), b.getFormData())) {
				return false;
			}
			return Objects.equals(safe(a.getBinaryFilePath()), safe(b.getBinaryFilePath()));
		}

		private boolean headerListsEqual(List<HeaderEntryState> left, List<HeaderEntryState> right) {
			if (left == null) {
				left = List.of();
			}
			if (right == null) {
				right = List.of();
			}
			if (left.size() != right.size()) {
				return false;
			}
			for (int i = 0; i < left.size(); i++) {
				HeaderEntryState a = left.get(i);
				HeaderEntryState b = right.get(i);
				if (a == b) {
					continue;
				}
				if (a == null || b == null) {
					return false;
				}
				if (!Objects.equals(a.name, b.name)) {
					return false;
				}
				if (!Objects.equals(a.value, b.value)) {
					return false;
				}
				if (a.enabled != b.enabled) {
					return false;
				}
			}
			return true;
		}

		private boolean formListsEqual(List<FormEntryState> left, List<FormEntryState> right) {
			if (left == null) {
				left = List.of();
			}
			if (right == null) {
				right = List.of();
			}
			if (left.size() != right.size()) {
				return false;
			}
			for (int i = 0; i < left.size(); i++) {
				FormEntryState a = left.get(i);
				FormEntryState b = right.get(i);
				if (a == b) {
					continue;
				}
				if (a == null || b == null) {
					return false;
				}
				if (!Objects.equals(a.name, b.name)) {
					return false;
				}
				if (!Objects.equals(a.value, b.value)) {
					return false;
				}
				if (a.enabled != b.enabled) {
					return false;
				}
				if (a.file != b.file) {
					return false;
				}
			}
			return true;
		}
	}

	private static class ChainSession {

		int nextIndex = 0;
		VarsStore vars = new VarsStore();
		List<String> logs = new ArrayList<>();
		String currentStateJson = "";
	}

	private class AutoSaveListener implements javax.swing.event.DocumentListener {

		@Override
		public void insertUpdate(javax.swing.event.DocumentEvent e) {
			saveCurrentEditors();
		}

		@Override
		public void removeUpdate(javax.swing.event.DocumentEvent e) {
			saveCurrentEditors();
		}

		@Override
		public void changedUpdate(javax.swing.event.DocumentEvent e) {
			saveCurrentEditors();
		}
	}

	private class EditorAutoSaveListener implements DocumentListener {

		@Override
		public void documentChanged(DocumentEvent event) {
			saveCurrentEditors();
		}
	}

	private class ChainCellRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(
			JList<?> list,
			Object value,
			int index,
			boolean isSelected,
			boolean cellHasFocus
		) {
			Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof String id) {
				NodeState node = stateService.findNode(id);
				String label = node == null ? id : node.name + " (" + node.requestType + ")";
				((JLabel) component).setText(label);
			}
			return component;
		}
	}

	private class ChainComboRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(
			JList<?> list,
			Object value,
			int index,
			boolean isSelected,
			boolean cellHasFocus
		) {
			Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof String id) {
				NodeState node = stateService.findNode(id);
				String label = node == null ? id : node.name + " (" + node.requestType + ")";
				((JLabel) component).setText(label);
			}
			return component;
		}
	}

	private class NodeTreeCellRenderer extends DefaultTreeCellRenderer {

		@Override
		public Component getTreeCellRendererComponent(
			JTree tree,
			Object value,
			boolean selected,
			boolean expanded,
			boolean leaf,
			int row,
			boolean hasFocus
		) {
			Component component =
				super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
			if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof NodeState state) {
				setText(state.name == null ? "" : state.name);
				if (state.type == NodeType.FOLDER) {
					setIcon(expanded ? getDefaultOpenIcon() : getDefaultClosedIcon());
				} else {
					setIcon(getDefaultLeafIcon());
				}
			}
			return component;
		}
	}

	private class ChainListTransferHandler extends TransferHandler {

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
				chainListModel.remove(fromIndex);
				chainListModel.add(index, data);
				if (currentNode != null) {
					saveChain(currentNode.id);
				}
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}

	private class TreeTransferHandler extends TransferHandler {

		private final DataFlavor flavor = DataFlavor.stringFlavor;

		@Override
		protected Transferable createTransferable(JComponent c) {
			Object selected = tree.getLastSelectedPathComponent();
			if (!(selected instanceof DefaultMutableTreeNode treeNode)) {
				return null;
			}
			Object userObject = treeNode.getUserObject();
			if (!(userObject instanceof NodeState node)) {
				return null;
			}
			return new StringSelection(node.id);
		}

		@Override
		public int getSourceActions(JComponent c) {
			return MOVE;
		}

		@Override
		public boolean canImport(TransferSupport support) {
			if (!support.isDataFlavorSupported(flavor)) {
				return false;
			}
			if (!(support.getDropLocation() instanceof JTree.DropLocation dropLocation)) {
				return false;
			}
			TreePath path = dropLocation.getPath();
			if (path == null) {
				return false;
			}
			DefaultMutableTreeNode targetTreeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
			Object userObject = targetTreeNode.getUserObject();
			if (!(userObject instanceof NodeState targetNode)) {
				return false;
			}
			try {
				String draggedId = (String) support.getTransferable().getTransferData(flavor);
				if (Objects.equals(draggedId, targetNode.id)) {
					return false;
				}
				DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) treeModel.getRoot();
				DefaultMutableTreeNode draggedNode = findTreeNodeById(rootNode, draggedId);
				if (draggedNode != null && draggedNode.isNodeDescendant(targetTreeNode)) {
					return false;
				}
			} catch (Exception ignored) {
				return false;
			}
			return true;
		}

		@Override
		public boolean importData(TransferSupport support) {
			if (!canImport(support)) {
				return false;
			}
			try {
				String draggedId = (String) support.getTransferable().getTransferData(flavor);
				JTree.DropLocation dropLocation = (JTree.DropLocation) support.getDropLocation();
				TreePath path = dropLocation.getPath();
				DefaultMutableTreeNode targetTreeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
				Object userObject = targetTreeNode.getUserObject();
				if (!(userObject instanceof NodeState targetNode)) {
					return false;
				}
				String newParentId;
				int index = dropLocation.getChildIndex();
				if (targetNode.type == NodeType.FOLDER) {
					newParentId = targetNode.id;
					if (index < 0) {
						index = targetTreeNode.getChildCount();
					}
				} else {
					DefaultMutableTreeNode parentTreeNode = (DefaultMutableTreeNode) targetTreeNode.getParent();
					NodeState parentNode = parentTreeNode == null ? null : (NodeState) parentTreeNode.getUserObject();
					newParentId = parentNode == null ? null : parentNode.id;
					if (index < 0) {
						index = parentTreeNode == null ? 0 : parentTreeNode.getIndex(targetTreeNode) + 1;
					}
				}
				stateService.moveNode(draggedId, newParentId, index);
				reloadTree();
				selectNode(draggedId);
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}
}
