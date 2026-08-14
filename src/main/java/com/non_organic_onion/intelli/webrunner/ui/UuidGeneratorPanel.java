package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.intelli.webrunner.util.UuidService;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public final class UuidGeneratorPanel {
	private final JPanel root = new JPanel(new BorderLayout(8, 8));
	private final JTextField uuidField = new JTextField();
	private final JButton generateButton = new JButton("Generate");
	private final JButton copyButton = new JButton("Copy");
	private final JLabel statusLabel = new JLabel(" ");

	public UuidGeneratorPanel() {
		buildUi();
		attachActions();
		generateUuid();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
		content.add(new JLabel("UUID"), BorderLayout.NORTH);
		content.add(uuidField, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.add(statusLabel);
		actions.add(generateButton);
		actions.add(copyButton);
		content.add(actions, BorderLayout.SOUTH);

		root.add(content, BorderLayout.CENTER);
	}

	private void attachActions() {
		generateButton.addActionListener(e -> generateUuid());
		copyButton.addActionListener(e -> copyUuid());
	}

	private void generateUuid() {
		uuidField.setText(UuidService.randomUuid());
		uuidField.selectAll();
		statusLabel.setText(" ");
	}

	private void copyUuid() {
		String value = uuidField.getText();
		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(value), null);
		statusLabel.setText("Copied");
	}
}
