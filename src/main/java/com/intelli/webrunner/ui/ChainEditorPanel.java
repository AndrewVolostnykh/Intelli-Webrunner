package com.intelli.webrunner.ui;

import com.intelli.webrunner.execution.ExecutionResult;
import com.intelli.webrunner.execution.RequestExecutionService;
import com.intelli.webrunner.script.VarsStore;
import com.intelli.webrunner.state.ChainState;
import com.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.intelli.webrunner.state.NodeState;
import com.intelli.webrunner.state.NodeType;
import com.intelli.webrunner.state.RequestDetailsState;
import com.intelli.webrunner.state.RequestStatusState;
import com.intelli.webrunner.state.RequestType;
import com.intelli.webrunner.util.JsonUtils;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;

import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
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
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	private final JButton chainRunButton = new JButton("Run");
	private final JButton chainDebugButton = new JButton("Debug");
	private final JButton chainNextButton = new JButton("Next");
	private final JBTextArea chainLogsArea = new JBTextArea();
	private final EditorTextField chainCurrentStateArea;
	private final JTabbedPane chainResponseTabs = new JTabbedPane();
	private final JButton openChainWindowButton = new JButton("Open Chain");
	private final JPanel root = new JPanel(new BorderLayout());

	private ChainSession chainSession;
	private String activeRequestId;
	private boolean isLoading = false;

	public ChainEditorPanel(
		Project project,
		GlobalWebrunnerStateService stateService,
		RequestExecutionService executionService
	) {
		this.project = project;
		this.stateService = stateService;
		this.executionService = executionService;
		this.chainCurrentStateArea = new EditorTextField("", project, JsonFileType.INSTANCE);
		this.chainCurrentStateArea.setOneLineMode(false);
		buildUi();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
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
		root.add(topBar, BorderLayout.NORTH);

		JPanel chainEditor = new JPanel(new BorderLayout());
		chainList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		chainList.setCellRenderer(new ChainNodeRenderer(stateService));
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

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chainEditor, chainResponseTabs);
		splitPane.setResizeWeight(0.6);
		root.add(splitPane, BorderLayout.CENTER);
	}

	public void load(String requestId) {
		activeRequestId = requestId;
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
		refreshRequestsCombo();
	}

	public void save() {
		if (activeRequestId == null) {
			return;
		}
		ChainState chain = stateService.getChainState(activeRequestId);
		if (chain == null) {
			chain = new ChainState();
			chain.requestId = activeRequestId;
		}
		chain.requestIds = Collections.list(chainListModel.elements());
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
		save();
	}

	private void removeChainRequest() {
		int index = chainList.getSelectedIndex();
		if (index < 0) {
			return;
		}
		chainListModel.remove(index);
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
			invokeLater(() -> chainNextButton.setEnabled(
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
		session.currentStateJson = JsonUtils.toJson(currentState);

		updateChainUi(session, node);
	}

	private void updateChainUi(
		ChainSession session,
		NodeState node
	) {
		invokeLater(() -> {
			chainLogsArea.setText(String.join("\n", session.logs));
			chainCurrentStateArea.setText(session.currentStateJson);
			if (node != null) {
				chainList.setSelectedValue(node.id, true);
			}
			save();
		});
	}

	private void finishChainRun() {
		invokeLater(() -> {
			chainRunButton.setEnabled(true);
			chainDebugButton.setEnabled(true);
			chainNextButton.setEnabled(false);
			chainSession = null;
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

	private void runInBackground(Runnable runnable) {
		ApplicationManager.getApplication().executeOnPooledThread(runnable);
	}

	private void invokeLater(Runnable runnable) {
		ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static final class ChainSession {

		int nextIndex = 0;
		VarsStore vars = new VarsStore();
		List<String> logs = new ArrayList<>();
		String currentStateJson = "";
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
				chainListModel.remove(fromIndex);
				chainListModel.add(index, data);
				save();
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}
}
