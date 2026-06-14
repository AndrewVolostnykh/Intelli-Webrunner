package com.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Base64ToolPanel {
	private final JPanel root = new JPanel(new BorderLayout());
	private final JLabel leftLabel = new JLabel("Base64");
	private final JLabel rightLabel = new JLabel("Content");
	private final JButton swapButton = new JButton("⇄");
	private final JTextArea inputField;
	private final JTextArea outputField;

	private boolean decodeMode = true;

	public Base64ToolPanel(Project project) {
		this.inputField = createTextField(project);
		this.outputField = createTextField(project);
		this.outputField.setEditable(false);
		buildUi();
		attachActions();
		updateOutput();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
		JPanel content = new JPanel(new GridBagLayout());
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		GridBagConstraints leftLabelConstraints = labelConstraints(0);
		content.add(leftLabel, leftLabelConstraints);

		GridBagConstraints swapConstraints = new GridBagConstraints();
		swapConstraints.gridx = 1;
		swapConstraints.gridy = 0;
		swapConstraints.insets = new Insets(0, 8, 8, 8);
		content.add(swapButton, swapConstraints);

		GridBagConstraints rightLabelConstraints = labelConstraints(2);
		content.add(rightLabel, rightLabelConstraints);

		GridBagConstraints leftFieldConstraints = fieldConstraints(0);
		content.add(new JBScrollPane(inputField), leftFieldConstraints);

		GridBagConstraints rightFieldConstraints = fieldConstraints(2);
		content.add(new JBScrollPane(outputField), rightFieldConstraints);

		root.add(content, BorderLayout.CENTER);
	}

	private void attachActions() {
		inputField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				updateOutput();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				updateOutput();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				updateOutput();
			}
		});
		swapButton.addActionListener(e -> swapMode());
	}

	private void swapMode() {
		decodeMode = !decodeMode;
		leftLabel.setText(decodeMode ? "Base64" : "Content");
		rightLabel.setText(decodeMode ? "Content" : "Base64");
		updateOutput();
	}

	private void updateOutput() {
		String input = inputField.getText();
		if (input == null || input.isEmpty()) {
			outputField.setText("");
			return;
		}
		if (decodeMode) {
			outputField.setText(decode(input));
		} else {
			outputField.setText(encode(input));
		}
	}

	private static String decode(String value) {
		try {
			String normalized = value.replaceAll("\\s+", "");
			byte[] decoded = Base64.getDecoder().decode(normalized);
			return new String(decoded, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return "Invalid Base64: " + e.getMessage();
		}
	}

	private static String encode(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static JTextArea createTextField(Project project) {
		JTextArea field = new JTextArea();
		field.setLineWrap(true);
		field.setWrapStyleWord(false);
		field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, field.getFont().getSize()));
		return field;
	}

	private static GridBagConstraints labelConstraints(int gridx) {
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = gridx;
		constraints.gridy = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 8, 0);
		return constraints;
	}

	private static GridBagConstraints fieldConstraints(int gridx) {
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = gridx;
		constraints.gridy = 1;
		constraints.weightx = 1;
		constraints.weighty = 1;
		constraints.fill = GridBagConstraints.BOTH;
		return constraints;
	}

}
