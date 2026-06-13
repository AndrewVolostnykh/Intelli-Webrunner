package com.intelli.webrunner.ui;

import com.intelli.webrunner.state.NodeState;
import com.intelli.webrunner.state.NodeType;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;

/**
 * Renders request-tree nodes with folder/leaf icons based on {@link NodeState#type}.
 */
public final class NodeTreeCellRenderer extends DefaultTreeCellRenderer {

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
