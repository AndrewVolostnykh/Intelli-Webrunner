package com.intelli.webrunner.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JwtDecoderPanel {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final JPanel root = new JPanel(new BorderLayout());
	private final EditorTextField jwtField;
	private final EditorTextField decodedField;

	public JwtDecoderPanel(Project project) {
		this.jwtField = createTextField(project, PlainTextFileType.INSTANCE);
		this.decodedField = createTextField(project, resolveJsonFileType());
		this.decodedField.setViewer(true);
		this.decodedField.setForeground(resolveReadableForeground());
		buildUi();
		attachListener();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
		JPanel inputPanel = new JPanel(new BorderLayout());
		inputPanel.add(new JLabel("JWT"), BorderLayout.NORTH);
		inputPanel.add(new JBScrollPane(jwtField), BorderLayout.CENTER);

		JPanel outputPanel = new JPanel(new BorderLayout());
		outputPanel.add(new JLabel("Decoded JSON"), BorderLayout.NORTH);
		outputPanel.add(new JBScrollPane(decodedField), BorderLayout.CENTER);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, outputPanel);
		splitPane.setResizeWeight(0.5);
		root.add(splitPane, BorderLayout.CENTER);
	}

	private void attachListener() {
		jwtField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void documentChanged(DocumentEvent event) {
				updateDecoded();
			}
		});
	}

	private void updateDecoded() {
		decodedField.setText(decode(jwtField.getText()));
	}

	private static EditorTextField createTextField(Project project, FileType fileType) {
		EditorTextField field = new EditorTextField("", project, fileType);
		field.setOneLineMode(false);
		return field;
	}

	private static String decode(String value) {
		String token = normalizeToken(value);
		if (token.isBlank()) {
			return "";
		}
		String[] parts = token.split("\\.", -1);
		if (parts.length < 2) {
			return "Invalid JWT: expected at least header and payload parts.";
		}
		try {
			Map<String, Object> decoded = new LinkedHashMap<>();
			decoded.put("header", decodeJsonPart(parts[0]));
			decoded.put("payload", decodeJsonPart(parts[1]));
			if (parts.length > 2 && !parts[2].isBlank()) {
				decoded.put("signature", parts[2]);
			}
			return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(decoded);
		} catch (Exception e) {
			return "Invalid JWT: " + e.getMessage();
		}
	}

	private static String normalizeToken(String value) {
		String token = value == null ? "" : value.trim();
		if (token.regionMatches(true, 0, "Bearer", 0, "Bearer".length())) {
			token = token.substring("Bearer".length()).trim();
		}
		return token;
	}

	private static Object decodeJsonPart(String part) throws Exception {
		String json = new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
		return MAPPER.readValue(json, Object.class);
	}

	private static FileType resolveJsonFileType() {
		FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension("json");
		if (fileType == null || fileType == PlainTextFileType.INSTANCE) {
			fileType = PlainTextFileType.INSTANCE;
		}
		return fileType;
	}

	private static Color resolveReadableForeground() {
		EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
		Color foreground = scheme.getDefaultForeground();
		return foreground == null ? Color.WHITE : foreground;
	}
}
