package com.intelli.webrunner.ui;

import com.intelli.webrunner.util.JsonTextOperations;
import com.intelli.webrunner.util.TextFormatting;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

public final class TextToolPanel {
	private final JPanel root = new JPanel(new BorderLayout(8, 8));
	private final JTextArea textField = new JTextArea();
	private final JButton minifyButton = new JButton("Minify");
	private final JButton beautifyButton = new JButton("Beautify");
	private final JButton moreButton = new JButton("\u22EE");

	public TextToolPanel() {
		buildUi();
		attachActions();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		textField.setLineWrap(true);
		textField.setWrapStyleWord(false);
		textField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textField.getFont().getSize()));
		root.add(new JScrollPane(textField), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.add(minifyButton);
		actions.add(beautifyButton);
		actions.add(moreButton);
		root.add(actions, BorderLayout.SOUTH);
	}

	private void attachActions() {
		minifyButton.addActionListener(e -> apply(TextFormatting.minify(textField.getText())));
		beautifyButton.addActionListener(e -> apply(TextFormatting.beautify(textField.getText())));
		moreButton.addActionListener(e -> showMoreMenu());
	}

	private void showMoreMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem removeItem = new JMenuItem("Remove");
		JMenuItem replaceItem = new JMenuItem("Replace");
		removeItem.addActionListener(e -> JsonRemoveDialog.show(root, "Remove from Text", this::removeText));
		replaceItem.addActionListener(e -> JsonReplaceDialog.show(root, "Replace in Text", this::replaceText));
		menu.add(removeItem);
		menu.add(replaceItem);
		menu.show(moreButton, 0, moreButton.getHeight());
	}

	private void removeText(String value) {
		apply(JsonTextOperations.remove(textField.getText(), value));
	}

	private void replaceText(String target, String replacement) {
		apply(JsonTextOperations.replace(textField.getText(), target, replacement));
	}

	private void apply(String value) {
		textField.setText(value);
		textField.setCaretPosition(0);
	}
}
