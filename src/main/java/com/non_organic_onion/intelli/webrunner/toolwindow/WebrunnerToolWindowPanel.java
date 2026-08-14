package com.non_organic_onion.intelli.webrunner.toolwindow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.non_organic_onion.intelli.webrunner.execution.HttpExecutor;
import com.non_organic_onion.intelli.webrunner.execution.RequestExecutionService;
import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutor;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaListenerService;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaMessageProducer;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaMetadataService;
import com.non_organic_onion.intelli.webrunner.io.HttpFileCodec;
import com.non_organic_onion.intelli.webrunner.io.HttpFileExportService;
import com.non_organic_onion.intelli.webrunner.io.HttpFileImportService;
import com.non_organic_onion.intelli.webrunner.io.HttpFileRequest;
import com.non_organic_onion.intelli.webrunner.io.OpenApiCodec;
import com.non_organic_onion.intelli.webrunner.io.OpenApiImportExportService;
import com.non_organic_onion.intelli.webrunner.script.ScriptRuntime;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.intelli.webrunner.state.IntellijGlobalContextStore;
import com.non_organic_onion.intelli.webrunner.state.NodeState;
import com.non_organic_onion.intelli.webrunner.state.NodeType;
import com.non_organic_onion.intelli.webrunner.state.RequestDetailsState;
import com.non_organic_onion.intelli.webrunner.state.RequestStatusState;
import com.non_organic_onion.intelli.webrunner.state.RequestType;
import com.non_organic_onion.intelli.webrunner.state.WebrunnerState;
import com.non_organic_onion.intelli.webrunner.ui.Base64ToolDialog;
import com.non_organic_onion.intelli.webrunner.ui.ChainEditorPanel;
import com.non_organic_onion.intelli.webrunner.ui.CompareToolDialog;
import com.non_organic_onion.intelli.webrunner.ui.CurlImportDialog;
import com.non_organic_onion.intelli.webrunner.ui.DateTimeToolDialog;
import com.non_organic_onion.intelli.webrunner.ui.GlobalContextDialog;
import com.non_organic_onion.intelli.webrunner.ui.HashToolDialog;
import com.non_organic_onion.intelli.webrunner.ui.JsonToolDialog;
import com.non_organic_onion.intelli.webrunner.ui.JwtDecoderDialog;
import com.non_organic_onion.intelli.webrunner.ui.RequestEditorPanel;
import com.non_organic_onion.intelli.webrunner.ui.RequestTreePanel;
import com.non_organic_onion.intelli.webrunner.ui.ResponseViewerPanel;
import com.non_organic_onion.intelli.webrunner.ui.SettingsDialog;
import com.non_organic_onion.intelli.webrunner.ui.SplitPaneStyling;
import com.non_organic_onion.intelli.webrunner.ui.TaskbarWindowSupport;
import com.non_organic_onion.intelli.webrunner.ui.TextToolDialog;
import com.non_organic_onion.intelli.webrunner.ui.UrlToolDialog;
import com.non_organic_onion.intelli.webrunner.ui.UuidGeneratorDialog;
import com.non_organic_onion.intelli.webrunner.ui.WebrunnerInfoDialog;
import com.non_organic_onion.intelli.webrunner.util.CurlCommandBuilder;
import com.non_organic_onion.intelli.webrunner.util.CurlCommandParser;
import com.non_organic_onion.intelli.webrunner.util.CurlRequest;
import com.non_organic_onion.intelli.webrunner.util.FileNameUtils;
import com.non_organic_onion.intelli.webrunner.util.TemplateEngine;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class WebrunnerToolWindowPanel implements com.intellij.openapi.Disposable {

	private final Project project;
	private final GlobalWebrunnerStateService stateService;
	private final JPanel root;
	private final RequestTreePanel treePanel;
	private final JButton moreButton;
	private final JButton devToolsButton;
	private final JButton globalContextButton;
	private final JButton deleteButton;
	private final CardLayout editorCards;
	private final JPanel editorPanel;
	private final JPanel emptyPanel;

	private NodeState currentNode;
	private String treePopupFolderId;
	private final KeyEventDispatcher hotkeyDispatcher = this::dispatchPluginHotkey;

	private final ObjectMapper mapper = new ObjectMapper();
	private final TemplateEngine templateEngine = new TemplateEngine();
	private final ScriptRuntime scriptRuntime = new ScriptRuntime();
	private final HttpExecutor httpExecutor = new HttpExecutor();
	private final GrpcExecutor grpcExecutor = new GrpcExecutor();
	private final KafkaMetadataService kafkaMetadataService = new KafkaMetadataService();
	private final KafkaMessageProducer kafkaMessageProducer = new KafkaMessageProducer();
	private final KafkaListenerService kafkaListenerService = new KafkaListenerService();
	private final RequestExecutionService executionService;

	private final ResponseViewerPanel responseViewer;
	private final RequestEditorPanel editor;
	private final ChainEditorPanel chainPanel;

	private final JButton openRequestWindowButton = new JButton("Open Request");
	private final JButton openResponseWindowButton = new JButton("Open Response");

	public WebrunnerToolWindowPanel(Project project) {
		this.project = project;
		this.stateService = GlobalWebrunnerStateService.getInstance();
		this.executionService =
			new RequestExecutionService(
				templateEngine,
				scriptRuntime,
				httpExecutor,
				grpcExecutor,
				kafkaMessageProducer,
				new IntellijGlobalContextStore(stateService)
			);
		this.moreButton = new JButton("...");
		this.devToolsButton = new JButton("", AllIcons.General.ExternalTools);
		this.globalContextButton = new JButton("", AllIcons.Nodes.Variable);
		this.deleteButton = new JButton("-");

		this.editorCards = new CardLayout();
		this.editorPanel = new JPanel(editorCards);
		this.emptyPanel = new JPanel(new BorderLayout());
		this.root = new JPanel(new BorderLayout());
		this.responseViewer = new ResponseViewerPanel(project, this::saveCurrentEditors);
		this.editor = new RequestEditorPanel(
			project, stateService, executionService, responseViewer, scriptRuntime, templateEngine, httpExecutor,
			grpcExecutor, kafkaMetadataService, kafkaListenerService
		);
		this.chainPanel = new ChainEditorPanel(project, stateService, executionService, this::reloadTree);
		this.treePanel = new RequestTreePanel(stateService, chainPanel::refreshRequestsCombo);

		buildUi();
		reloadTree();
	}

	public JComponent getComponent() {
		return root;
	}

	@Override
	public void dispose() {
		KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(hotkeyDispatcher);
		editor.dispose();
		kafkaListenerService.shutdown();
	}

	private void invokeLater(Runnable runnable) {
		ApplicationManager.getApplication().invokeLater(
			runnable,
			com.intellij.openapi.application.ModalityState.any()
		);
	}

	private void buildUi() {
		JPanel leftPanel = new JPanel(new BorderLayout());
		treePanel.getTree().addTreeSelectionListener(this::handleTreeSelection);
		treePanel.getTree().addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				handleTreePopupTrigger(event);
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				handleTreePopupTrigger(event);
			}
		});
		treePanel.getTree().setComponentPopupMenu(buildTreePopupMenu());

		JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		Dimension compactButton = new Dimension(28, 28);
		moreButton.setPreferredSize(compactButton);
		moreButton.setMargin(new Insets(0, 0, 0, 0));
		devToolsButton.setPreferredSize(compactButton);
		devToolsButton.setMargin(new Insets(0, 0, 0, 0));
		devToolsButton.setToolTipText("Dev Tools");
		globalContextButton.setPreferredSize(compactButton);
		globalContextButton.setMargin(new Insets(0, 0, 0, 0));
		globalContextButton.setToolTipText("Global Context");
		globalContextButton.setForeground(JBColor.ORANGE);
		deleteButton.setPreferredSize(compactButton);
		deleteButton.setMargin(new Insets(0, 0, 0, 0));
		leftActions.add(deleteButton);
		leftActions.add(moreButton);
		leftActions.add(devToolsButton);
		leftActions.add(globalContextButton);
		deleteButton.addActionListener(e -> deleteSelected());
		moreButton.addActionListener(e -> showMoreMenu());
		devToolsButton.addActionListener(e -> showDevToolsMenu());
		globalContextButton.addActionListener(e -> GlobalContextDialog.show(root, project, stateService));

		leftPanel.add(leftActions, BorderLayout.NORTH);
		leftPanel.setMinimumSize(new Dimension(220, 0));
		leftPanel.add(treePanel.getComponent(), BorderLayout.CENTER);

		emptyPanel.add(new JBLabel("Select or create a request to begin."), BorderLayout.CENTER);

		editorPanel.add(emptyPanel, "empty");
		editorPanel.add(editor.getComponent(), "request");
		editorPanel.add(chainPanel.getComponent(), "chain");

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, editorPanel);
		splitPane.setResizeWeight(0.25);
		splitPane.setBackground(JBColor.BLACK);
		splitPane.setForeground(JBColor.BLACK);
		SplitPaneStyling.applyThinBlackDivider(splitPane);
		root.add(splitPane, BorderLayout.CENTER);

		attachHotkeys();
		openRequestWindowButton.setEnabled(false);
		openResponseWindowButton.setEnabled(false);
		editorCards.show(editorPanel, "empty");
	}

	private void handleTreePopupTrigger(MouseEvent event) {
		if (!event.isPopupTrigger()) {
			return;
		}
		Point point = event.getPoint();
		treePopupFolderId = treePanel.folderIdAt(point);
		treePanel.selectNodeAt(point);
	}

	private JPopupMenu buildTreePopupMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem newRequest = new JMenuItem("New Request");
		JMenuItem rename = new JMenuItem("Rename");
		JMenuItem clone = new JMenuItem("Clone");
		JMenuItem newCollection = new JMenuItem("New Collection");
		JMenuItem remove = new JMenuItem("Delete");
		JMenuItem getCurlItem = new JMenuItem("Get cURL");
		JMenuItem useCurlItem = new JMenuItem("Use cURL");
		JMenuItem importHttpItem = new JMenuItem("Import .http");
		JMenuItem exportHttpItem = new JMenuItem("Export .http");
		JMenuItem importOpenApiItem = new JMenuItem("Import OpenAPI");
		JMenuItem exportOpenApiItem = new JMenuItem("Export OpenAPI");
		newRequest.addActionListener(e -> createRequest());
		rename.addActionListener(e -> renameSelected());
		clone.addActionListener(e -> cloneSelectedRequest());
		newCollection.addActionListener(e -> createFolder());
		remove.addActionListener(e -> deleteSelected());
		getCurlItem.addActionListener(e -> copySelectedCurl());
		useCurlItem.addActionListener(e -> useCurl(treePopupFolderId));
		importHttpItem.addActionListener(e -> importHttpFromTree());
		exportHttpItem.addActionListener(e -> exportHttpFromTree());
		importOpenApiItem.addActionListener(e -> importOpenApiFromTree());
		exportOpenApiItem.addActionListener(e -> exportOpenApiFromTree());
		menu.add(newRequest);
		menu.add(rename);
		menu.add(clone);
		menu.add(newCollection);
		menu.add(remove);
		menu.addSeparator();
		menu.add(getCurlItem);
		menu.add(useCurlItem);
		menu.addSeparator();
		menu.add(importHttpItem);
		menu.add(exportHttpItem);
		menu.addSeparator();
		menu.add(importOpenApiItem);
		menu.add(exportOpenApiItem);
		menu.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				RequestTreePanel.TreeFolderSelection selection = treePanel.getTreeFolderSelection();
				boolean enable = selection != null;
				boolean requestSelected = currentNode != null && currentNode.type == NodeType.REQUEST;
				boolean httpRequestSelected = requestSelected && currentNode.requestType == RequestType.HTTP;
				clone.setEnabled(requestSelected);
				getCurlItem.setEnabled(httpRequestSelected);
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

	private void useCurl(String parentId) {
		CurlImportDialog.Input input = CurlImportDialog.show(root);
		if (input == null || input.command() == null || input.command().isBlank()) {
			return;
		}
		try {
			CurlRequest imported = CurlCommandParser.parse(input.command());
			String name = input.name() == null || input.name().isBlank()
				? imported.method + " " + imported.url
				: input.name().trim();
			NodeState node = stateService.createRequest(name, RequestType.HTTP, parentId);

			RequestDetailsState details = stateService.getRequestDetails(node.id);
			details.method = imported.method;
			details.url = imported.url;
			details.payloadType = imported.payloadType;
			stateService.saveRequestDetails(details);

			RequestStatusState status = new RequestStatusState();
			status.requestId = node.id;
			status.requestBody = imported.body;
			status.requestHeaders = imported.headers;
			status.requestParams = imported.params;
			status.formData = imported.formData;
			status.binaryFilePath = imported.binaryFilePath;
			status.responseBody = "";
			status.responseHeaders = "";
			status.responseCookies = "";
			status.logs = "";
			status.beforeScript = "";
			status.afterScript = "";
			stateService.saveRequestStatus(status);
			reloadTree(node.id);
		} catch (IllegalArgumentException error) {
			TaskbarWindowSupport.showMessageDialog(
				root,
				error.getMessage(),
				"Invalid cURL",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void copySelectedCurl() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST || currentNode.requestType != RequestType.HTTP) {
			return;
		}
		saveCurrentEditors();
		RequestDetailsState details = stateService.getRequestDetails(currentNode.id);
		if (details == null || details.url == null || details.url.isBlank()) {
			TaskbarWindowSupport.showMessageDialog(root, "Missing request URL.", "Get cURL", JOptionPane.ERROR_MESSAGE);
			return;
		}
		RequestStatusState status = stateService.getRequestStatus(currentNode.id);
		String curl = CurlCommandBuilder.build(
			details.method == null ? "GET" : details.method,
			details.url,
			status != null ? status.requestHeaders : List.of(),
			status != null ? status.requestParams : List.of(),
			status != null ? status.requestBody : "",
			details.payloadType == null ? "RAW" : details.payloadType,
			status != null ? status.formData : List.of(),
			status != null ? status.binaryFilePath : ""
		);
		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(curl), null);
		showLog("cURL copied to clipboard.");
	}

	private void showDevToolsMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem jwtItem = new JMenuItem("JWT");
		JMenuItem base64Item = new JMenuItem("Base64");
		JMenuItem urlItem = new JMenuItem("URL");
		JMenuItem jsonItem = new JMenuItem("JSON");
		JMenuItem textItem = new JMenuItem("Text");
		JMenuItem hashItem = new JMenuItem("Hash");
		JMenuItem compareItem = new JMenuItem("Compare");
		JMenuItem uuidItem = new JMenuItem("Generate UUID");
		JMenuItem dateTimeItem = new JMenuItem("DateTime");
		jwtItem.addActionListener(e -> JwtDecoderDialog.show(root, project));
		base64Item.addActionListener(e -> Base64ToolDialog.show(root, project));
		urlItem.addActionListener(e -> UrlToolDialog.show(root, project));
		jsonItem.addActionListener(e -> JsonToolDialog.show(root, project));
		textItem.addActionListener(e -> TextToolDialog.show(root, project));
		hashItem.addActionListener(e -> HashToolDialog.show(root));
		compareItem.addActionListener(e -> CompareToolDialog.show(root, project));
		uuidItem.addActionListener(e -> UuidGeneratorDialog.show(root));
		dateTimeItem.addActionListener(e -> DateTimeToolDialog.show(root));
		menu.add(jwtItem);
		menu.add(base64Item);
		menu.add(urlItem);
		menu.add(jsonItem);
		menu.add(textItem);
		menu.add(hashItem);
		menu.add(compareItem);
		menu.add(uuidItem);
		menu.add(dateTimeItem);
		menu.show(devToolsButton, 0, devToolsButton.getHeight());
	}

	private void renameSelected() {
		if (currentNode == null) {
			return;
		}
		String name = TaskbarWindowSupport.showInputDialog(root, "New name:", "Rename", currentNode.name);
		if (name == null || name.isBlank()) {
			return;
		}
		stateService.updateNodeName(currentNode.id, name);
		reloadTree();
	}

	private void cloneSelectedRequest() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST) {
			return;
		}
		String defaultName = safe(currentNode.name).isBlank() ? "Request copy" : currentNode.name + " Copy";
		String name = TaskbarWindowSupport.showInputDialog(root, "Clone name:", "Clone Request", defaultName);
		if (name == null || name.isBlank()) {
			return;
		}
		saveCurrentEditors();
		NodeState cloned = stateService.cloneRequest(currentNode.id, name);
		if (cloned != null) {
			reloadTree(cloned.id);
		}
	}

	private void showInfoDialog() {
		WebrunnerInfoDialog.show(root);
	}

	private void handleTreeSelection(TreeSelectionEvent event) {
		editor.stopTableEditing();
		saveCurrentEditors();
		Object selected = treePanel.getTree().getLastSelectedPathComponent();
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
			return;
		}
		currentNode = (NodeState) userObject;
		if (currentNode.type == NodeType.FOLDER) {
			editorCards.show(editorPanel, "empty");
			openRequestWindowButton.setEnabled(false);
			openResponseWindowButton.setEnabled(false);
			return;
		}
		if (currentNode.requestType == RequestType.HTTP || currentNode.requestType == RequestType.GRPC
			|| currentNode.requestType == RequestType.KAFKA || currentNode.requestType == RequestType.KAFKA_LISTEN) {
			editor.load(currentNode);
			editorCards.show(editorPanel, "request");
			openRequestWindowButton.setEnabled(true);
			openResponseWindowButton.setEnabled(true);
		} else if (currentNode.requestType == RequestType.CHAIN) {
			chainPanel.load(currentNode.id);
			editorCards.show(editorPanel, "chain");
			openRequestWindowButton.setEnabled(false);
			openResponseWindowButton.setEnabled(false);
		}
	}

	private void reloadTree() {
		reloadTree(null);
	}

	private void reloadTree(String focusNodeId) {
		treePanel.reload(focusNodeId);
	}

	private void createFolder() {
		String name = TaskbarWindowSupport.showInputDialog(root, "Collection name:", "New Collection", "");
		if (name == null || name.isBlank()) {
			return;
		}
		String parentId = treePanel.selectedFolderId();
		stateService.createFolder(name, parentId);
		reloadTree();
	}

	private void createRequest() {
		JTextField nameField = new JTextField();
		JComboBox<RequestType> typeCombo = new JComboBox<>(RequestType.values());
		typeCombo.setSelectedItem(RequestType.HTTP);
		Object[] fields = {
			"Request name:", nameField,
			"Request type:", typeCombo
		};
		SwingUtilities.invokeLater(nameField::requestFocusInWindow);
		int result = TaskbarWindowSupport.showConfirmDialog(
			root,
			fields,
			"New Request",
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
		String parentId = treePanel.selectedFolderId();
		NodeState created = stateService.createRequest(name.trim(), type, parentId);
		reloadTree(created.id);
	}

	private void deleteSelected() {
		if (currentNode == null) {
			return;
		}
		int confirm = TaskbarWindowSupport.showConfirmDialog(root,
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
		int result = TaskbarWindowSupport.showOpenDialog(chooser, root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = chooser.getSelectedFile();
		if (selectedFile != null && FileNameUtils.hasHttpExtension(selectedFile)) {
			importHttpFile(selectedFile);
			return;
		}
		try {
			WebrunnerState imported = mapper.readValue(selectedFile, WebrunnerState.class);
			Object[] options = new Object[] {"Merge", "Replace", "Cancel"};
			int choice = TaskbarWindowSupport.showOptionDialog(
				root,
				"Import mode:",
				"Import Collections",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
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
			TaskbarWindowSupport.showMessageDialog(
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
			chooser.setSelectedFile(new File(FileNameUtils.safeFileName(currentNode.name) + ".http"));
		} else {
			chooser.setFileFilter(jsonFilter);
			chooser.setSelectedFile(new File("intelli-webrunner.json"));
		}
		int result = TaskbarWindowSupport.showSaveDialog(chooser, root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			File selectedFile = chooser.getSelectedFile();
			boolean exportHttp = (chooser.getFileFilter() == httpFilter) || FileNameUtils.hasHttpExtension(selectedFile);
			if (exportHttp) {
				exportHttpRequest(FileNameUtils.ensureExtension(selectedFile, "http"));
				return;
			}
			mapper.writerWithDefaultPrettyPrinter()
				.writeValue(FileNameUtils.ensureExtension(selectedFile, "json"), stateService.exportState());
		} catch (Exception error) {
			TaskbarWindowSupport.showMessageDialog(
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
		int result = TaskbarWindowSupport.showOpenDialog(chooser, root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = chooser.getSelectedFile();
		try {
			WebrunnerState imported = mapper.readValue(selectedFile, WebrunnerState.class);
			Object[] options = new Object[] {"Merge", "Replace", "Cancel"};
			int choice = TaskbarWindowSupport.showOptionDialog(
				root,
				"Import mode:",
				"Import Collections",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
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
			TaskbarWindowSupport.showMessageDialog(
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
		int result = TaskbarWindowSupport.showSaveDialog(chooser, root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			File selectedFile = chooser.getSelectedFile();
			mapper.writerWithDefaultPrettyPrinter()
				.writeValue(FileNameUtils.ensureExtension(selectedFile, "json"), stateService.exportState());
		} catch (Exception error) {
			TaskbarWindowSupport.showMessageDialog(
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
		int result = TaskbarWindowSupport.showOpenDialog(chooser, root);
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
			chooser.setSelectedFile(new File(FileNameUtils.safeFileName(currentNode.name) + ".http"));
		} else {
			chooser.setSelectedFile(new File("request.http"));
		}
		int result = TaskbarWindowSupport.showSaveDialog(chooser, root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = FileNameUtils.ensureExtension(chooser.getSelectedFile(), "http");
		exportHttpRequest(selectedFile);
	}

	private void importHttpFromTree() {
		RequestTreePanel.TreeFolderSelection selection = treePanel.getTreeFolderSelection();
		if (selection == null) {
			TaskbarWindowSupport.showMessageDialog(
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
		int result = TaskbarWindowSupport.showOpenDialog(chooser, root);
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
		RequestTreePanel.TreeFolderSelection selection = treePanel.getTreeFolderSelection();
		if (selection == null) {
			TaskbarWindowSupport.showMessageDialog(
				root,
				"Select root or a folder to export.",
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}
		List<NodeState> requests = treePanel.collectHttpRequestsInSubtree(selection.folderId);
		if (requests.isEmpty()) {
			TaskbarWindowSupport.showMessageDialog(
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
		chooser.setSelectedFile(new File(FileNameUtils.safeFileName(baseName) + ".http"));
		int result = TaskbarWindowSupport.showSaveDialog(chooser, root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = FileNameUtils.ensureExtension(chooser.getSelectedFile(), "http");
		exportHttpRequests(selectedFile, requests);
	}

	private void importOpenApiFromTree() {
		RequestTreePanel.TreeFolderSelection selection = treePanel.getTreeFolderSelection();
		if (selection == null) {
			TaskbarWindowSupport.showMessageDialog(
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
		int result = TaskbarWindowSupport.showOpenDialog(chooser, root);
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
		RequestTreePanel.TreeFolderSelection selection = treePanel.getTreeFolderSelection();
		if (selection == null) {
			TaskbarWindowSupport.showMessageDialog(
				root,
				"Select root or a folder to export.",
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}
		List<NodeState> requests = treePanel.collectHttpRequestsInSubtree(selection.folderId);
		if (requests.isEmpty()) {
			TaskbarWindowSupport.showMessageDialog(
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
		chooser.setSelectedFile(new File(FileNameUtils.safeFileName(baseName) + "-openapi.json"));
		int result = TaskbarWindowSupport.showSaveDialog(chooser, root);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selectedFile = FileNameUtils.ensureExtension(chooser.getSelectedFile(), "json");
		exportOpenApiFile(selectedFile, requests, selection.displayName);
	}

	private void importHttpFile(File file) {
		importHttpFile(file, treePanel.selectedFolderId());
	}

	private void importHttpFile(
		File file,
		String parentId
	) {
		try {
			List<HttpFileRequest> requests = HttpFileCodec.parse(file);
			if (requests.isEmpty()) {
				TaskbarWindowSupport.showMessageDialog(
					root,
					"No HTTP requests found in file.",
					"Import",
					JOptionPane.INFORMATION_MESSAGE
				);
				return;
			}
			for (HttpFileImportService.ImportItem item : HttpFileImportService.buildImportPlan(requests)) {
				NodeState node = stateService.createRequest(item.name(), RequestType.HTTP, parentId);
				RequestDetailsState details = stateService.getRequestDetails(node.id);
				if (details == null) {
					details = new RequestDetailsState();
					details.requestId = node.id;
				}
				details.type = RequestType.HTTP;
				details.method = item.method();
				details.url = item.url();
				details.payloadType = item.payloadType();
				stateService.saveRequestDetails(details);

				RequestStatusState status = stateService.getRequestStatus(node.id);
				if (status == null) {
					status = new RequestStatusState();
					status.requestId = node.id;
				}
				status.requestBody = item.body();
				status.requestHeaders = item.headers();
				status.requestParams = item.params();
				status.responseBody = "";
				status.responseHeaders = "";
				status.responseCookies = "";
				status.logs = "";
				status.beforeScript = item.beforeScript();
				status.afterScript = item.afterScript();
				stateService.saveRequestStatus(status);
			}
			currentNode = null;
			editorCards.show(editorPanel, "empty");
			reloadTree();
		} catch (Exception error) {
			TaskbarWindowSupport.showMessageDialog(
				root,
				"Failed to import .http: " + error.getMessage(),
				"Import error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void exportHttpRequest(File file) {
		if (currentNode == null || currentNode.requestType != RequestType.HTTP) {
			TaskbarWindowSupport.showMessageDialog(
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
			TaskbarWindowSupport.showMessageDialog(root, "Missing request URL.", "Export error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		String export = HttpFileExportService.buildRequestBlock(currentNode, details, status);
		try {
			Files.writeString(file.toPath(), export, StandardCharsets.UTF_8);
		} catch (Exception error) {
			TaskbarWindowSupport.showMessageDialog(
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
		List<HttpFileExportService.RequestExportItem> exportItems = new ArrayList<>();
		for (NodeState node : requests) {
			if (node == null || node.requestType != RequestType.HTTP) {
				continue;
			}
			exportItems.add(new HttpFileExportService.RequestExportItem(
				node,
				stateService.getRequestDetails(node.id),
				stateService.getRequestStatus(node.id)
			));
		}
		try {
			Files.writeString(file.toPath(), HttpFileExportService.buildRequestsFile(exportItems), StandardCharsets.UTF_8);
		} catch (Exception error) {
			TaskbarWindowSupport.showMessageDialog(
				root,
				"Failed to export .http: " + error.getMessage(),
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void exportOpenApiFile(
		File file,
		List<NodeState> requests,
		String title
	) {
		OpenApiCodec codec = new OpenApiCodec(mapper);
		List<OpenApiImportExportService.ExportItem> exportItems = new ArrayList<>();
		for (NodeState node : requests) {
			if (node == null) {
				continue;
			}
			exportItems.add(new OpenApiImportExportService.ExportItem(
				node,
				stateService.getRequestDetails(node.id),
				stateService.getRequestStatus(node.id)
			));
		}
		Map<String, Object> doc =
			OpenApiImportExportService.buildDocument(codec, title, stateService.exportState().nodes, exportItems);
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(file, doc);
		} catch (Exception error) {
			TaskbarWindowSupport.showMessageDialog(
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
			OpenApiCodec codec = new OpenApiCodec(mapper);
			Map<String, Object> doc = mapper.readValue(file, Map.class);
			Object openapi = doc.get("openapi");
			if (openapi == null) {
				TaskbarWindowSupport.showMessageDialog(
					root,
					"Invalid OpenAPI file (missing 'openapi').",
					"Import error",
					JOptionPane.ERROR_MESSAGE
				);
				return;
			}
			List<OpenApiImportExportService.ImportItem> importItems =
				OpenApiImportExportService.buildImportPlan(codec, doc);
			if (!(doc.get("paths") instanceof Map<?, ?>)) {
				TaskbarWindowSupport.showMessageDialog(
					root,
					"OpenAPI file has no paths.",
					"Import",
					JOptionPane.INFORMATION_MESSAGE
				);
				return;
			}
			for (OpenApiImportExportService.ImportItem item : importItems) {
				NodeState node = stateService.createRequest(item.name(), RequestType.HTTP, parentId);
				RequestDetailsState details = stateService.getRequestDetails(node.id);
				if (details == null) {
					details = new RequestDetailsState();
					details.requestId = node.id;
				}
				details.type = RequestType.HTTP;
				details.method = item.method();
				details.url = item.url();
				details.payloadType = item.payloadType();
				stateService.saveRequestDetails(details);

				RequestStatusState status = stateService.getRequestStatus(node.id);
				if (status == null) {
					status = new RequestStatusState();
					status.requestId = node.id;
				}
				status.requestBody = item.body();
				status.requestHeaders = item.headers();
				status.requestParams = item.params();
				status.responseBody = "";
				status.responseHeaders = "";
				status.responseCookies = "";
				status.logs = "";
				status.beforeScript = item.beforeScript();
				status.afterScript = item.afterScript();
				stateService.saveRequestStatus(status);
			}
			if (importItems.isEmpty()) {
				TaskbarWindowSupport.showMessageDialog(
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
			TaskbarWindowSupport.showMessageDialog(
				root,
				"Failed to import OpenAPI: " + error.getMessage(),
				"Import error",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void saveCurrentEditors() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST) {
			return;
		}
		if (currentNode.requestType == RequestType.HTTP || currentNode.requestType == RequestType.GRPC
			|| currentNode.requestType == RequestType.KAFKA || currentNode.requestType == RequestType.KAFKA_LISTEN) {
			editor.saveActive();
		} else if (currentNode.requestType == RequestType.CHAIN) {
			chainPanel.save();
		}
	}

	private void attachHotkeys() {
		InputMap inputMap = root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		ActionMap actionMap = root.getActionMap();
		inputMap.put(KeyStroke.getKeyStroke("control ENTER"), "webrunner.send");
		inputMap.put(KeyStroke.getKeyStroke("alt 1"), "webrunner.focus.tree");
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
				triggerCurrentRequest();
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
				focusRequestTree();
			}
		});
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(hotkeyDispatcher);
	}

	private boolean dispatchPluginHotkey(KeyEvent event) {
		if (event.getID() != KeyEvent.KEY_PRESSED || !isPluginFocusOwner()) {
			return false;
		}
		if (isCtrlEnter(event)) {
			triggerCurrentRequest();
			event.consume();
			return true;
		}
		if (isAltOne(event)) {
			focusRequestTree();
			event.consume();
			return true;
		}
		return false;
	}

	private boolean isPluginFocusOwner() {
		Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		return focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, root);
	}

	private boolean isCtrlEnter(KeyEvent event) {
		int modifiers = event.getModifiersEx();
		return event.getKeyCode() == KeyEvent.VK_ENTER
			&& (modifiers & InputEvent.CTRL_DOWN_MASK) != 0
			&& (modifiers & (InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK)) == 0;
	}

	private boolean isAltOne(KeyEvent event) {
		int modifiers = event.getModifiersEx();
		return event.getKeyCode() == KeyEvent.VK_1
			&& (modifiers & InputEvent.ALT_DOWN_MASK) != 0
			&& (modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) == 0;
	}

	private void triggerCurrentRequest() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST) {
			return;
		}
		if (currentNode.requestType == RequestType.HTTP) {
			editor.executeHttp();
		} else if (currentNode.requestType == RequestType.GRPC) {
			editor.executeGrpc();
		} else if (currentNode.requestType == RequestType.KAFKA) {
			editor.executeKafka();
		} else if (currentNode.requestType == RequestType.KAFKA_LISTEN) {
			editor.toggleKafkaListeningFromShortcut();
		} else if (currentNode.requestType == RequestType.CHAIN) {
			chainPanel.triggerSend();
		}
	}

	private void focusRequestTree() {
		treePanel.getTree().requestFocusInWindow();
	}

	private void switchTab(int direction) {
		if (currentNode == null) {
			return;
		}
		JTabbedPane targetTabs = null;
		if (currentNode.requestType == RequestType.CHAIN) {
			return;
		} else {
			Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
			if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, responseViewer.getTabs())) {
				targetTabs = responseViewer.getTabs();
			} else {
				targetTabs = editor.getRequestTabs();
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
			return;
		}
		editor.focusBody();
	}

	private void formatCurrentEditors() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST
			|| currentNode.requestType == RequestType.CHAIN) {
			return;
		}
		editor.formatEditors();
	}

	private void showLog(String message) {
		responseViewer.showLog(message);
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private void openResponseWindow() {
		if (currentNode == null || currentNode.type != NodeType.REQUEST ||
			currentNode.requestType == RequestType.CHAIN) {
			return;
		}
		responseViewer.openInWindow("Response Viewer - " + currentNode.name, root);
	}

	private void openSettingsDialog() {
		SettingsDialog.show(
			root,
			stateService.getHeaderPresets(),
			stateService.isStressTestsEnabled(),
			stateService.getDefaultTimeoutMillis(),
			stateService.getCollectionsFilePath(),
			stateService.getSettingsFilePath(),
			settings -> {
				saveCurrentEditors();
				stateService.saveHeaderPresets(settings.headerPresets());
				stateService.saveStressTestsEnabled(settings.stressTestsEnabled());
				stateService.saveDefaultTimeoutMillis(settings.defaultTimeoutMillis());
				stateService.changeCollectionsFilePath(settings.collectionsFilePath());
				editor.updateHeaderPresets(settings.headerPresets());
				editor.setStressTestsEnabled(settings.stressTestsEnabled());
				editor.setDefaultTimeoutMillis(settings.defaultTimeoutMillis());
				currentNode = null;
				editorCards.show(editorPanel, "empty");
				reloadTree();
			}
		);
	}

}
