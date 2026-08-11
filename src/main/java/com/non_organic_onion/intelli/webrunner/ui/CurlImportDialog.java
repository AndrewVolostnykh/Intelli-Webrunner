package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import java.awt.Component;

public final class CurlImportDialog {

	private CurlImportDialog() {
	}

	public static Input show(Component parent) {
		JBTextField nameInput = new JBTextField();
		JBTextArea input = new JBTextArea(14, 80);
		input.setLineWrap(true);
		input.setWrapStyleWord(true);
		JBScrollPane scrollPane = new JBScrollPane(input);
		JPanel namePanel = new JPanel(new BorderLayout(8, 0));
		namePanel.add(new JLabel("Request name:"), BorderLayout.WEST);
		namePanel.add(nameInput, BorderLayout.CENTER);
		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.add(namePanel, BorderLayout.NORTH);
		content.add(scrollPane, BorderLayout.CENTER);
		int result = TaskbarWindowSupport.showConfirmDialog(
			parent,
			content,
			"Use cURL",
			JOptionPane.OK_CANCEL_OPTION
		);
		if (result != JOptionPane.OK_OPTION) {
			return null;
		}
		return new Input(nameInput.getText(), input.getText());
	}

	public record Input(String name, String command) {
	}
}
