package com.intelli.webrunner.ui;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

public final class JsonToolPanel {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final JPanel root = new JPanel(new BorderLayout(8, 8));
	private final JTextArea jsonField = new JTextArea();
	private final JButton minifyButton = new JButton("Minify");
	private final JButton beautifyButton = new JButton("Beautify");
	private final JLabel statusLabel = new JLabel(" ");

	public JsonToolPanel() {
		buildUi();
		attachActions();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		jsonField.setLineWrap(true);
		jsonField.setWrapStyleWord(false);
		jsonField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, jsonField.getFont().getSize()));
		root.add(new JScrollPane(jsonField), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.add(statusLabel);
		actions.add(minifyButton);
		actions.add(beautifyButton);
		root.add(actions, BorderLayout.SOUTH);
	}

	private void attachActions() {
		minifyButton.addActionListener(e -> format(false));
		beautifyButton.addActionListener(e -> format(true));
	}

	private void format(boolean beautify) {
		String value = jsonField.getText();
		if (value == null || value.isBlank()) {
			statusLabel.setText(" ");
			return;
		}
		try {
			Object json = MAPPER.readValue(value, Object.class);
			String formatted = beautify
				? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json)
				: MAPPER.writeValueAsString(json);
			jsonField.setText(formatted);
			jsonField.setCaretPosition(0);
			statusLabel.setText(" ");
		} catch (Exception error) {
			statusLabel.setText("Invalid JSON: " + error.getMessage());
		}
	}
}
