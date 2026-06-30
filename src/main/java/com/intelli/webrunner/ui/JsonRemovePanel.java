package com.intelli.webrunner.ui;

import com.intellij.ui.components.JBTextField;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;

public final class JsonRemovePanel {
	private final JPanel root = new JPanel(new BorderLayout(8, 8));
	private final JBTextField valueField = new JBTextField();
	private final JButton removeButton = new JButton("Remove");

	public JsonRemovePanel(Consumer<String> onRemove) {
		JPanel input = new JPanel(new BorderLayout(8, 0));
		input.add(new JLabel("Text to remove:"), BorderLayout.WEST);
		input.add(valueField, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		actions.add(removeButton);

		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		root.add(input, BorderLayout.CENTER);
		root.add(actions, BorderLayout.SOUTH);

		removeButton.addActionListener(e -> onRemove.accept(valueField.getText()));
		valueField.addActionListener(e -> removeButton.doClick());
	}

	public JComponent getComponent() {
		return root;
	}
}
