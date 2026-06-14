package com.intelli.webrunner.ui;

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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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
			decodeField.setText(encodeUrl(encodeField.getText()));
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
			encodeField.setText(decodeUrl(decodeField.getText()));
		} finally {
			updating = false;
		}
	}

	private static String encodeUrl(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (int offset = 0; offset < value.length(); ) {
			int codePoint = value.codePointAt(offset);
			if (isAllowedUrlCharacter(codePoint)) {
				builder.appendCodePoint(codePoint);
			} else {
				byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
				for (byte singleByte : bytes) {
					builder.append('%');
					String hex = Integer.toHexString(singleByte & 0xff).toUpperCase(java.util.Locale.ROOT);
					if (hex.length() == 1) {
						builder.append('0');
					}
					builder.append(hex);
				}
			}
			offset += Character.charCount(codePoint);
		}
		return builder.toString();
	}

	private static String decodeUrl(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		for (int index = 0; index < value.length(); ) {
			char character = value.charAt(index);
			if (character == '%' && index + 2 < value.length()) {
				int decoded = parseHex(value.charAt(index + 1), value.charAt(index + 2));
				if (decoded >= 0) {
					bytes.write(decoded);
					index += 3;
					continue;
				}
			}
			flushBytes(builder, bytes);
			builder.append(character == '+' ? ' ' : character);
			index++;
		}
		flushBytes(builder, bytes);
		return builder.toString();
	}

	private static void flushBytes(
		StringBuilder builder,
		ByteArrayOutputStream bytes
	) {
		if (bytes.size() == 0) {
			return;
		}
		builder.append(bytes.toString(StandardCharsets.UTF_8));
		bytes.reset();
	}

	private static int parseHex(
		char first,
		char second
	) {
		int high = Character.digit(first, 16);
		int low = Character.digit(second, 16);
		if (high < 0 || low < 0) {
			return -1;
		}
		return (high << 4) + low;
	}

	private static boolean isAllowedUrlCharacter(int codePoint) {
		return isAlphaNumeric(codePoint) || "-._~:/?#[]@!$&'()*+,;=".indexOf(codePoint) >= 0;
	}

	private static boolean isAlphaNumeric(int codePoint) {
		return (codePoint >= 'a' && codePoint <= 'z')
			|| (codePoint >= 'A' && codePoint <= 'Z')
			|| (codePoint >= '0' && codePoint <= '9');
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
