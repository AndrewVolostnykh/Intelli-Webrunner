package com.non_organic_onion.intelli.webrunner.toolwindow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.non_organic_onion.intelli.webrunner.execution.HttpExecutor;
import com.non_organic_onion.intelli.webrunner.execution.RequestExecutionService;
import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutor;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaListenerService;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaMessageProducer;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaMetadataService;
import com.non_organic_onion.intelli.webrunner.io.HttpFileCodec;
import com.non_organic_onion.intelli.webrunner.io.HttpFileRequest;
import com.non_organic_onion.intelli.webrunner.io.OpenApiCodec;
import com.non_organic_onion.intelli.webrunner.script.ScriptRuntime;
import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
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
import com.non_organic_onion.intelli.webrunner.ui.TextToolDialog;
import com.non_organic_onion.intelli.webrunner.ui.UrlToolDialog;
import com.non_organic_onion.intelli.webrunner.ui.UuidGeneratorDialog;
import com.non_organic_onion.intelli.webrunner.ui.WebrunnerInfoDialog;
import com.non_organic_onion.intelli.webrunner.util.ContentDispositionUtils;
import com.non_organic_onion.intelli.webrunner.util.CurlCommandParser;
import com.non_organic_onion.intelli.webrunner.util.CurlRequest;
import com.non_organic_onion.intelli.webrunner.util.FileNameUtils;
import com.non_organic_onion.intelli.webrunner.util.JsonUtils;
import com.non_organic_onion.intelli.webrunner.util.StateCopyUtils;
import com.non_organic_onion.intelli.webrunner.util.TemplateEngine;
import com.non_organic_onion.intelli.webrunner.util.UrlParamUtils;
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
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
				stateService
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
				clone.setEnabled(requestSelected);
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
			JOptionPane.showMessageDialog(
				root,
				error.getMessage(),
				"Invalid cURL",
				JOptionPane.ERROR_MESSAGE
			);
		}
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
		textItem.addActionListener(e -> TextToolDialog.show(root));
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
		String name = JOptionPane.showInputDialog(root, "New name:", currentNode.name);
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
		String name = JOptionPane.showInputDialog(root, "Clone name:", defaultName);
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
		String name = JOptionPane.showInputDialog(root, "Collection name:");
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
		int result = JOptionPane.showConfirmDialog(
			root,
			fields,
			"New Request",
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
		String parentId = treePanel.selectedFolderId();
		NodeState created = stateService.createRequest(name.trim(), type, parentId);
		reloadTree(created.id);
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
		RequestTreePanel.TreeFolderSelection selection = treePanel.getTreeFolderSelection();
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
		RequestTreePanel.TreeFolderSelection selection = treePanel.getTreeFolderSelection();
		if (selection == null) {
			JOptionPane.showMessageDialog(
				root,
				"Select root or a folder to export.",
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}
		List<NodeState> requests = treePanel.collectHttpRequestsInSubtree(selection.folderId);
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
		RequestTreePanel.TreeFolderSelection selection = treePanel.getTreeFolderSelection();
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
		RequestTreePanel.TreeFolderSelection selection = treePanel.getTreeFolderSelection();
		if (selection == null) {
			JOptionPane.showMessageDialog(
				root,
				"Select root or a folder to export.",
				"Export error",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}
		List<NodeState> requests = treePanel.collectHttpRequestsInSubtree(selection.folderId);
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
		importHttpFile(file, treePanel.selectedFolderId());
	}

	private void importHttpFile(
		File file,
		String parentId
	) {
		try {
			List<HttpFileRequest> requests = HttpFileCodec.parse(file);
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
				status.responseCookies = "";
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
		return HttpFileCodec.buildBlock(node.name, method, url, body, headers);
	}

	private boolean isHttpComment(String line) {
		return FileNameUtils.isHttpComment(line);
	}

	private boolean hasHttpExtension(File file) {
		return FileNameUtils.hasHttpExtension(file);
	}

	private File ensureExtension(
		File file,
		String extension
	) {
		return FileNameUtils.ensureExtension(file, extension);
	}

	private String safeFileName(String value) {
		return FileNameUtils.safeFileName(value);
	}

	private void exportOpenApiFile(
		File file,
		List<NodeState> requests,
		String title
	) {
		OpenApiCodec codec = new OpenApiCodec(mapper);
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
			OpenApiCodec.ParsedUrl parsed = codec.parseUrl(details.url);
			String path = parsed.path;
			Map<String, Object> pathItem =
				(Map<String, Object>) paths.computeIfAbsent(path, key -> new LinkedHashMap<>());

			Map<String, Object> operation = new LinkedHashMap<>();
			operation.put("summary", safe(node.name));
			operation.put("operationId", codec.buildOperationId(node));
			operation.put("responses", Map.of("200", Map.of("description", "OK")));
			if (parsed.serverUrl != null) {
				operation.put("servers", List.of(Map.of("url", parsed.serverUrl)));
			}

			List<String> tags = codec.buildFolderTags(node, nodeById);
			if (!tags.isEmpty()) {
				operation.put("tags", tags);
			}

			List<Map<String, Object>> parameters = codec.buildOpenApiParameters(status, details);
			if (!parameters.isEmpty()) {
				operation.put("parameters", parameters);
			}

			Object requestBody = codec.buildOpenApiRequestBody(status);
			if (requestBody != null) {
				operation.put("requestBody", requestBody);
			}

			Map<String, Object> vendor = codec.buildVendorExtension(node, details, status);
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
			OpenApiCodec codec = new OpenApiCodec(mapper);
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
					if (!codec.isHttpMethod(method)) {
						continue;
					}
					Object operationObj = opEntry.getValue();
					if (!(operationObj instanceof Map<?, ?> operation)) {
						continue;
					}
					String url = codec.resolveOperationUrl(doc, pathItem, operation, path);
					OpenApiCodec.RequestData requestData = codec.readVendorRequestData(operation, pathItem, method, url);
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
					status.responseCookies = "";
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
				treePanel.getTree().requestFocusInWindow();
			}
		});
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

	private String suggestDownloadFilename(Map<String, List<String>> headers) {
		return ContentDispositionUtils.suggestDownloadFilename(headers);
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

	private String replaceQueryParams(
		String url,
		List<HeaderEntryState> params
	) {
		return UrlParamUtils.replaceQueryParams(url, params);
	}

	private String encodeParam(String value) {
		return UrlParamUtils.encodeParam(value);
	}

	private List<HeaderEntryState> mergeParamsWithUrl(
		List<HeaderEntryState> params,
		String url
	) {
		return UrlParamUtils.mergeParamsWithUrl(params, url);
	}

	private List<HeaderEntryState> parseQueryParams(String url) {
		return UrlParamUtils.parseQueryParams(url);
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
		return StateCopyUtils.cloneHeaders(headers);
	}

	private List<FormEntryState> cloneFormData(List<FormEntryState> entries) {
		return StateCopyUtils.cloneFormData(entries);
	}

	private String toJson(Object value) {
		return JsonUtils.toJson(value);
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
			stateService.getCollectionsFilePath(),
			stateService.getSettingsFilePath(),
			settings -> {
				saveCurrentEditors();
				stateService.saveHeaderPresets(settings.headerPresets());
				stateService.saveStressTestsEnabled(settings.stressTestsEnabled());
				stateService.changeCollectionsFilePath(settings.collectionsFilePath());
				editor.updateHeaderPresets(settings.headerPresets());
				editor.setStressTestsEnabled(settings.stressTestsEnabled());
				currentNode = null;
				editorCards.show(editorPanel, "empty");
				reloadTree();
			}
		);
	}

}
