package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.JComponent;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;
import java.util.function.Function;

/**
 * Lightweight styled log viewer used inside plugin UI instead of IntelliJ execution consoles.
 */
public final class LogViewerPanel {

	private final JTextPane textPane = new JTextPane();
	private final JBScrollPane component = new JBScrollPane(textPane);

	public LogViewerPanel() {
		textPane.setEditable(false);
		textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textPane.getFont().getSize()));
		textPane.setBackground(UIManager.getColor("TextPane.background"));
		textPane.setForeground(JBColor.foreground());
	}

	public JComponent getComponent() {
		return component;
	}

	public void setLogs(
		String logs,
		Function<String, Color> colorForLine,
		Function<String, String> textForLine
	) {
		StyledDocument document = textPane.getStyledDocument();
		try {
			document.remove(0, document.getLength());
			if (logs == null || logs.isEmpty()) {
				return;
			}
			String[] lines = logs.split("\\R", -1);
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];
				appendLine(document, textForLine.apply(line), colorForLine.apply(line));
				if (i < lines.length - 1) {
					appendLine(document, "\n", colorForLine.apply(line));
				}
			}
			textPane.setCaretPosition(0);
		} catch (BadLocationException ignored) {
		}
	}

	private void appendLine(
		StyledDocument document,
		String text,
		Color color
	) throws BadLocationException {
		SimpleAttributeSet attributes = new SimpleAttributeSet();
		StyleConstants.setForeground(attributes, color == null ? JBColor.foreground() : color);
		document.insertString(document.getLength(), text == null ? "" : text, attributes);
	}
}
