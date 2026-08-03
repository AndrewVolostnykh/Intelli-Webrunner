package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.intelli.webrunner.util.HashingService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class HashToolPanel {
	private final JPanel root = new JPanel(new BorderLayout(8, 8));
	private final JTextArea inputField = createTextArea();
	private final JTextArea outputField = createTextArea();
	private final JComboBox<String> algorithmBox =
		new JComboBox<>(HashingService.ALGORITHMS.toArray(new String[0]));
	private final JTextField secretField = new JTextField(24);
	private final JButton hashButton = new JButton("Hash");
	private final JLabel statusLabel = new JLabel(" ");

	public HashToolPanel() {
		buildUi();
		attachActions();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JPanel content = new JPanel(new GridBagLayout());
		content.add(new JLabel("Input"), labelConstraints(0));
		content.add(new JLabel("Hash"), labelConstraints(1));
		content.add(new JScrollPane(inputField), fieldConstraints(0));
		content.add(new JScrollPane(outputField), fieldConstraints(1));
		root.add(content, BorderLayout.CENTER);

		JPanel controls = new JPanel(new GridBagLayout());
		controls.add(new JLabel("Algorithm"), controlsConstraints(0, 0, 1, false));
		controls.add(algorithmBox, controlsConstraints(1, 0, 1, true));
		controls.add(new JLabel("HMAC Secret"), controlsConstraints(2, 0, 1, false));
		controls.add(secretField, controlsConstraints(3, 0, 1, true));
		controls.add(hashButton, controlsConstraints(4, 0, 1, false));
		controls.add(statusLabel, controlsConstraints(0, 1, 5, true));
		root.add(controls, BorderLayout.SOUTH);
	}

	private void attachActions() {
		hashButton.addActionListener(e -> updateHash());
	}

	private void updateHash() {
		try {
			String algorithm = (String) algorithmBox.getSelectedItem();
			String hash = HashingService.hash(inputField.getText(), algorithm, secretField.getText());
			outputField.setText(hash);
			outputField.setCaretPosition(0);
			statusLabel.setText(" ");
		} catch (Exception error) {
			statusLabel.setText(error.getMessage());
		}
	}

	private static JTextArea createTextArea() {
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
		constraints.insets = new Insets(0, 0, 8, gridx == 0 ? 8 : 0);
		return constraints;
	}

	private static GridBagConstraints fieldConstraints(int gridx) {
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = gridx;
		constraints.gridy = 1;
		constraints.weightx = 1;
		constraints.weighty = 1;
		constraints.fill = GridBagConstraints.BOTH;
		constraints.insets = new Insets(0, 0, 0, gridx == 0 ? 8 : 0);
		return constraints;
	}

	private static GridBagConstraints controlsConstraints(int gridx, int gridy, int gridwidth, boolean resize) {
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = gridx;
		constraints.gridy = gridy;
		constraints.gridwidth = gridwidth;
		constraints.weightx = resize ? 1 : 0;
		constraints.fill = resize ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
		constraints.anchor = GridBagConstraints.WEST;
		constraints.insets = new Insets(gridy == 0 ? 0 : 8, gridx == 0 ? 0 : 8, 0, 0);
		return constraints;
	}
}
