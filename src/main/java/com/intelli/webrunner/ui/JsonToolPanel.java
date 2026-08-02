package com.intelli.webrunner.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.find.EditorSearchSession;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intelli.webrunner.util.JsonTextOperations;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

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
		root.add(actions, BorderLayout.SOUTH);
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
		Document document = createJsonDocument(project);
		if (document == null) {
			return new JsonToolEditorField(project);
		}
		return new JsonToolEditorField(project, document);
	}

	private static Document createJsonDocument(Project project) {
		PsiFile file = PsiFileFactory.getInstance(project)
			.createFileFromText("webrunner-json-tool.json", JsonFileType.INSTANCE, "");
		return PsiDocumentManager.getInstance(project).getDocument(file);
	}

	private static final class JsonToolEditorField extends JsonBodyEditorField {
		private static final String FIND_ACTION_ID = "webrunner.json.tool.editor.find";

		private JsonToolEditorField(Project project) {
			super(project);
			installSearch();
		}

		private JsonToolEditorField(Project project, Document document) {
			super(project, document);
			installSearch();
		}

		private void installSearch() {
			setOneLineMode(false);
			addSettingsProvider(editor -> {
				bindFindShortcut(editor.getComponent(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, editor);
				bindFindShortcut(editor.getContentComponent(), JComponent.WHEN_FOCUSED, editor);
				bindFindShortcut(editor.getContentComponent(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, editor);
			});
		}

		private void bindFindShortcut(JComponent component, int condition, Editor editor) {
			component.getInputMap(condition)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), FIND_ACTION_ID);
			component.getInputMap(condition)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.META_DOWN_MASK), FIND_ACTION_ID);
			component.getActionMap().put(FIND_ACTION_ID, new javax.swing.AbstractAction() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) {
					Project project = editor.getProject();
					if (project != null) {
						EditorSearchSession.start(editor, project);
					}
				}
			});
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
