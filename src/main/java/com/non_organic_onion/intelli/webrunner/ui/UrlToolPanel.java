package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.intelli.webrunner.util.UrlTextService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.BorderFactory;
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

public final class UrlToolPanel {
	private final JPanel root = new JPanel(new BorderLayout());
	private final JLabel encodeLabel = new JLabel("Encode");
	private final JLabel decodeLabel = new JLabel("Decode");
	private final JTextArea encodeField;
	private final JTextArea decodeField;

	private boolean updating;

	public UrlToolPanel(Project project) {
		this.encodeField = createTextField(project);
		this.decodeField = createTextField(project);
		buildUi();
		attachActions();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
		JPanel content = new JPanel(new GridBagLayout());
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		content.add(encodeLabel, labelConstraints(0));
		content.add(decodeLabel, labelConstraints(1));
		content.add(new JBScrollPane(encodeField), fieldConstraints(0));
		content.add(new JBScrollPane(decodeField), fieldConstraints(1));

		root.add(content, BorderLayout.CENTER);
	}

	private void attachActions() {
		encodeField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				encodeInput();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				encodeInput();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				encodeInput();
			}
		});
		decodeField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				decodeInput();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				decodeInput();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				decodeInput();
			}
		});
	}

	private void encodeInput() {
		if (updating) {
			return;
		}
		updating = true;
		try {
			decodeField.setText(UrlTextService.encode(encodeField.getText()));
		} finally {
			updating = false;
		}
	}

	private void decodeInput() {
		if (updating) {
			return;
		}
		updating = true;
		try {
			encodeField.setText(UrlTextService.decode(decodeField.getText()));
		} finally {
			updating = false;
		}
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
