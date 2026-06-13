package com.intelli.webrunner.ui;

import com.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.intelli.webrunner.state.NodeState;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import java.awt.Component;

/**
 * Renders chain entries (request ids) as "name (type)", resolved via the state service.
 * Shared by both the chain list and the request combo.
 */
public final class ChainNodeRenderer extends DefaultListCellRenderer {

	private final GlobalWebrunnerStateService stateService;

	public ChainNodeRenderer(GlobalWebrunnerStateService stateService) {
		this.stateService = stateService;
	}

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
