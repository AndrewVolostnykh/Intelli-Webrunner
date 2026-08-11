package com.non_organic_onion.intelli.webrunner.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.non_organic_onion.intelli.webrunner.util.JsonTextOperations;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public final class JsonToolPanel {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final JPanel root = new JPanel(new BorderLayout(8, 8));
	private final JsonToolEditorField jsonField;
	private final JButton minifyButton = new JButton("Minify");
	private final JButton beautifyButton = new JButton("Beautify");
	private final JButton moreButton = new JButton("\u22EE");
	private final JLabel statusLabel = new JLabel(" ");

	public JsonToolPanel(Project project) {
		this.jsonField = createJsonField(project);
		buildUi();
		attachActions();
	}

	public JComponent getComponent() {
		return root;
	}

	public void requestEditorFocus() {
		jsonField.requestEditorFocus();
	}

	private void buildUi() {
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		root.add(new JBScrollPane(jsonField), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.add(statusLabel);
		actions.add(minifyButton);
		actions.add(beautifyButton);
		actions.add(moreButton);
		JPanel bottom = new JPanel(new BorderLayout(0, 8));
		bottom.add(EditorLocalFindSupport.create(jsonField, root), BorderLayout.NORTH);
		bottom.add(actions, BorderLayout.SOUTH);
		root.add(bottom, BorderLayout.SOUTH);
	}

	private void attachActions() {
		minifyButton.addActionListener(e -> format(false));
		beautifyButton.addActionListener(e -> format(true));
		moreButton.addActionListener(e -> showMoreMenu());
	}

	private void showMoreMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem removeItem = new JMenuItem("Remove");
		JMenuItem replaceItem = new JMenuItem("Replace");
		removeItem.addActionListener(e -> JsonRemoveDialog.show(root, this::removeText));
		replaceItem.addActionListener(e -> JsonReplaceDialog.show(root, this::replaceText));
		menu.add(removeItem);
		menu.add(replaceItem);
		menu.show(moreButton, 0, moreButton.getHeight());
	}

	private void removeText(String value) {
		jsonField.setText(JsonTextOperations.remove(jsonField.getText(), value));
		jsonField.setCaretPosition(0);
		statusLabel.setText(" ");
	}

	private void replaceText(String target, String replacement) {
		jsonField.setText(JsonTextOperations.replace(jsonField.getText(), target, replacement));
		jsonField.setCaretPosition(0);
		statusLabel.setText(" ");
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

	private static JsonToolEditorField createJsonField(Project project) {
		return new JsonToolEditorField(project);
	}

	private static final class JsonToolEditorField extends JsonBodyEditorField {
		private JsonToolEditorField(Project project) {
			super(project);
			setOneLineMode(false);
		}

		private void requestEditorFocus() {
			Editor editor = getEditor();
			if (editor != null) {
				editor.getContentComponent().requestFocusInWindow();
			} else {
				getFocusTarget().requestFocusInWindow();
			}
		}
	}
}
