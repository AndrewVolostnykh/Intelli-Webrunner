package com.intelli.webrunner.ui;

import com.intellij.json.JsonFileType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.EditorTextField;

public class JsonBodyEditorField extends EditorTextField {
    private static final Key<Boolean> JSON_BODY_EDITOR = Key.create("webrunner.json.body.editor");
    public JsonBodyEditorField(Project project) {
        super("", project, JsonFileType.INSTANCE);
        setOneLineMode(false);
        applySettings();
    }

    public JsonBodyEditorField(Project project, Document document) {
        super(document, project, JsonFileType.INSTANCE, false, false);
        setOneLineMode(false);
        applySettings();
    }

    private void applySettings() {
        addSettingsProvider(editor -> {
            EditorSettings settings = editor.getSettings();
            settings.setLineNumbersShown(true);
            settings.setLineMarkerAreaShown(true);
            settings.setFoldingOutlineShown(true);
            settings.setIndentGuidesShown(true);
            settings.setCaretRowShown(true);
            editor.putUserData(JSON_BODY_EDITOR, Boolean.TRUE);
            ensureTypedHandler();
        });
    }

    private static void ensureTypedHandler() {
        TypedAction typedAction = EditorActionManager.getInstance().getTypedAction();
        TypedActionHandler current = typedAction.getHandler();
        if (current instanceof JsonBodyTypedHandler) {
            return;
        }
        typedAction.setupHandler(new JsonBodyTypedHandler(current));
    }

    private static class JsonBodyTypedHandler implements TypedActionHandler {
        private final TypedActionHandler delegate;

        JsonBodyTypedHandler(TypedActionHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Editor editor, char c, DataContext dataContext) {
            if (!Boolean.TRUE.equals(editor.getUserData(JSON_BODY_EDITOR))) {
                if (delegate != null) {
                    delegate.execute(editor, c, dataContext);
                }
                return;
            }
            if (handleAutoPair(editor, c)) {
                return;
            }
            if (delegate != null) {
                delegate.execute(editor, c, dataContext);
            }
        }

        private boolean handleAutoPair(Editor editor, char c) {
            if (c != '{' && c != '[' && c != '}' && c != ']' && c != '"') {
                return false;
            }
            Document document = editor.getDocument();
            int offset = editor.getCaretModel().getOffset();
            CharSequence text = document.getCharsSequence();
            char next = offset < text.length() ? text.charAt(offset) : 0;

            if ((c == '}' || c == ']' || c == '"') && next == c) {
                editor.getCaretModel().moveToOffset(offset + 1);
                return true;
            }

            if (c == '{' || c == '[' || c == '"') {
                char close = c == '{' ? '}' : c == '[' ? ']' : '"';
                Project project = editor.getProject();
                Runnable action = () -> {
                    document.insertString(offset, "" + c + close);
                    editor.getCaretModel().moveToOffset(offset + 1);
                };
                if (project != null) {
                    WriteCommandAction.runWriteCommandAction(project, action);
                } else {
                    action.run();
                }
                return true;
            }
            return false;
        }
    }
}
