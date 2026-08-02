package com.intelli.webrunner.ui;

import com.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.intelli.webrunner.state.NodeState;
import com.intelli.webrunner.state.NodeType;
import com.intelli.webrunner.state.RequestType;
import com.intelli.webrunner.state.WebrunnerState;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The request/folder tree: rendering, expansion/selection preservation across reloads, structural
 * queries, and drag-and-drop reordering. The host owns selection handling and the action menus;
 * this panel exposes the tree widget and structural operations.
 */
public final class RequestTreePanel {

	private final GlobalWebrunnerStateService stateService;
	private final Runnable onAfterReload;
	private final DefaultTreeModel treeModel;
	private final JTree tree;
	private final JBScrollPane scroll;

	public RequestTreePanel(GlobalWebrunnerStateService stateService, Runnable onAfterReload) {
		this.stateService = stateService;
		this.onAfterReload = onAfterReload;
		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Requests");
		this.treeModel = new DefaultTreeModel(rootNode);
		this.tree = new JTree(treeModel);
		tree.setRootVisible(true);
		tree.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tree.setDragEnabled(true);
		tree.setDropMode(DropMode.ON_OR_INSERT);
		tree.setTransferHandler(new TreeTransferHandler());
		tree.setCellRenderer(new NodeTreeCellRenderer(stateService));
		this.scroll = new JBScrollPane(tree);
		this.scroll.setMinimumSize(new Dimension(200, 0));
	}

	public JTree getTree() {
		return tree;
	}

	public JComponent getComponent() {
		return scroll;
	}

	public NodeState getSelectedNode() {
		Object selected = tree.getLastSelectedPathComponent();
		if (selected instanceof DefaultMutableTreeNode treeNode
			&& treeNode.getUserObject() instanceof NodeState node) {
			return node;
		}
		return null;
	}

	public void reload() {
		reload(null);
	}

	public void reload(String focusNodeId) {
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
		if (onAfterReload != null) {
			onAfterReload.run();
		}
	}

	public void selectNode(String nodeId) {
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

	public String selectedFolderId() {
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

	public String folderIdAt(Point point) {
		if (point == null) {
			return selectedFolderId();
		}
		TreePath path = tree.getPathForLocation(point.x, point.y);
		if (path == null) {
			return null;
		}
		Object component = path.getLastPathComponent();
		if (!(component instanceof DefaultMutableTreeNode treeNode)) {
			return null;
		}
		Object userObject = treeNode.getUserObject();
		if (userObject instanceof NodeState node && node.type == NodeType.FOLDER) {
			return node.id;
		}
		if (userObject instanceof NodeState node && node.type == NodeType.REQUEST) {
			return node.parentId;
		}
		return null;
	}

	public void selectNodeAt(Point point) {
		if (point == null) {
			return;
		}
		TreePath path = tree.getPathForLocation(point.x, point.y);
		if (path == null) {
			tree.clearSelection();
			return;
		}
		tree.setSelectionPath(path);
	}

	public TreeFolderSelection getTreeFolderSelection() {
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

	public List<NodeState> collectHttpRequestsInSubtree(String folderId) {
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

	/** A drop target for moving folders/requests within the tree. */
	public static final class TreeFolderSelection {

		public final String folderId;
		public final String displayName;

		TreeFolderSelection(
			String folderId,
			String displayName
		) {
			this.folderId = folderId;
			this.displayName = displayName;
		}
	}

	private final class TreeTransferHandler extends TransferHandler {

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
				reload();
				selectNode(draggedId);
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}
}
