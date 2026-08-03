package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.ui.components.JBTextField;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.BiConsumer;

public final class JsonReplacePanel {
	private final JPanel root = new JPanel(new GridBagLayout());
	private final JBTextField targetField = new JBTextField();
	private final JBTextField replacementField = new JBTextField();
	private final JButton replaceButton = new JButton("Replace");

	public JsonReplacePanel(BiConsumer<String, String> onReplace) {
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		addField(0, "Text to replace:", targetField);
		addField(1, "Replace with:", replacementField);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		actions.add(replaceButton);
		GridBagConstraints actionsConstraints = new GridBagConstraints();
		actionsConstraints.gridx = 0;
		actionsConstraints.gridy = 2;
		actionsConstraints.gridwidth = 2;
		actionsConstraints.weightx = 1;
		actionsConstraints.fill = GridBagConstraints.HORIZONTAL;
		actionsConstraints.insets = new Insets(8, 0, 0, 0);
		root.add(actions, actionsConstraints);

		replaceButton.addActionListener(e -> onReplace.accept(targetField.getText(), replacementField.getText()));
		replacementField.addActionListener(e -> replaceButton.doClick());
	}

	public JComponent getComponent() {
		return root;
	}

	private void addField(int row, String label, JBTextField field) {
		GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.gridy = row;
		labelConstraints.anchor = GridBagConstraints.WEST;
		labelConstraints.insets = new Insets(row == 0 ? 0 : 8, 0, 0, 8);
		root.add(new JLabel(label), labelConstraints);

		GridBagConstraints fieldConstraints = new GridBagConstraints();
		fieldConstraints.gridx = 1;
		fieldConstraints.gridy = row;
		fieldConstraints.weightx = 1;
		fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
		fieldConstraints.insets = new Insets(row == 0 ? 0 : 8, 0, 0, 0);
		root.add(field, fieldConstraints);
	}
}
