package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBTextField;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;

final class EditorLocalFindSupport {
	private static final String FIND_ACTION_ID = "webrunner.local.editor.find";
	private static final String NEXT_ACTION_ID = "webrunner.local.editor.find.next";
	private static final String PREVIOUS_ACTION_ID = "webrunner.local.editor.find.previous";
	private static final String CLOSE_ACTION_ID = "webrunner.local.editor.find.close";

	private EditorLocalFindSupport() {
	}

	static JPanel create(EditorTextField editorField, JComponent scope) {
		FindPanel panel = new FindPanel(editorField);
		installShortcut(scope, panel);
		installDispatcher(scope, panel);
		return panel;
	}

	private static void installShortcut(JComponent scope, FindPanel panel) {
		scope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
			.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), FIND_ACTION_ID);
		scope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
			.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.META_DOWN_MASK), FIND_ACTION_ID);
		scope.getActionMap().put(FIND_ACTION_ID, new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				panel.open();
			}
		});
	}

	private static void installDispatcher(JComponent scope, FindPanel panel) {
		KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
		java.awt.KeyEventDispatcher dispatcher = event -> {
			if (!isFindShortcut(event) || !isInsideScope(focusManager.getFocusOwner(), scope)) {
				return false;
			}
			event.consume();
			panel.open();
			return true;
		};
		focusManager.addKeyEventDispatcher(dispatcher);
		scope.addHierarchyListener(event -> {
			if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && !scope.isDisplayable()) {
				focusManager.removeKeyEventDispatcher(dispatcher);
			}
		});
	}

	private static boolean isFindShortcut(KeyEvent event) {
		if (event.getID() != KeyEvent.KEY_PRESSED || event.getKeyCode() != KeyEvent.VK_F) {
			return false;
		}
		int modifiers = event.getModifiersEx();
		boolean ctrlOrMeta = (modifiers & InputEvent.CTRL_DOWN_MASK) != 0
			|| (modifiers & InputEvent.META_DOWN_MASK) != 0;
		boolean extra = (modifiers & (InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) != 0;
		return ctrlOrMeta && !extra;
	}

	private static boolean isInsideScope(Component focusOwner, JComponent scope) {
		if (focusOwner == null) {
			return false;
		}
		if (SwingUtilities.isDescendingFrom(focusOwner, scope)) {
			return true;
		}
		Window focusWindow = SwingUtilities.getWindowAncestor(focusOwner);
		Window scopeWindow = SwingUtilities.getWindowAncestor(scope);
		return focusWindow != null && focusWindow == scopeWindow;
	}

	private static final class FindPanel extends JPanel {
		private final EditorTextField editorField;
		private final JBTextField queryField = new JBTextField(24);
		private final JLabel statusLabel = new JLabel(" ");

		private FindPanel(EditorTextField editorField) {
			super(new FlowLayout(FlowLayout.LEFT, 6, 0));
			this.editorField = editorField;
			buildUi();
			attachActions();
			setVisible(false);
		}

		private void buildUi() {
			JButton previousButton = new JButton("Previous");
			JButton nextButton = new JButton("Next");
			JButton closeButton = new JButton("Close");
			previousButton.addActionListener(event -> find(false));
			nextButton.addActionListener(event -> find(true));
			closeButton.addActionListener(event -> close());
			add(new JLabel("Find"));
			add(queryField);
			add(previousButton);
			add(nextButton);
			add(closeButton);
			add(statusLabel);
		}

		private void attachActions() {
			queryField.addActionListener(event -> find(true));
			queryField.getInputMap(JComponent.WHEN_FOCUSED)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), NEXT_ACTION_ID);
			queryField.getInputMap(JComponent.WHEN_FOCUSED)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), PREVIOUS_ACTION_ID);
			queryField.getInputMap(JComponent.WHEN_FOCUSED)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CLOSE_ACTION_ID);
			queryField.getActionMap().put(NEXT_ACTION_ID, new javax.swing.AbstractAction() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent event) {
					find(true);
				}
			});
			queryField.getActionMap().put(PREVIOUS_ACTION_ID, new javax.swing.AbstractAction() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent event) {
					find(false);
				}
			});
			queryField.getActionMap().put(CLOSE_ACTION_ID, new javax.swing.AbstractAction() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent event) {
					close();
				}
			});
		}

		private void open() {
			setVisible(true);
			queryField.requestFocusInWindow();
			queryField.selectAll();
		}

		private void close() {
			setVisible(false);
			editorField.requestFocusInWindow();
		}

		private void find(boolean forward) {
			String query = queryField.getText();
			if (query == null || query.isEmpty()) {
				statusLabel.setText(" ");
				return;
			}
			String text = editorField.getText();
			if (text == null || text.isEmpty()) {
				statusLabel.setText("No matches");
				return;
			}
			String haystack = text.toLowerCase(Locale.ROOT);
			String needle = query.toLowerCase(Locale.ROOT);
			Editor editor = editorField.getEditor();
			int caret = editor == null ? 0 : editor.getCaretModel().getOffset();
			int start = forward ? findForward(haystack, needle, caret) : findBackward(haystack, needle, caret);
			if (start < 0) {
				statusLabel.setText("No matches");
				return;
			}
			select(editor, start, start + query.length());
			statusLabel.setText((start + 1) + " / " + text.length());
		}

		private int findForward(String haystack, String needle, int caret) {
			int start = haystack.indexOf(needle, Math.min(caret + 1, haystack.length()));
			return start >= 0 ? start : haystack.indexOf(needle);
		}

		private int findBackward(String haystack, String needle, int caret) {
			int from = Math.max(0, Math.min(caret - 1, haystack.length()));
			int start = haystack.lastIndexOf(needle, from);
			return start >= 0 ? start : haystack.lastIndexOf(needle);
		}

		private void select(Editor editor, int start, int end) {
			if (editor == null) {
				editorField.setCaretPosition(start);
				return;
			}
			editor.getSelectionModel().setSelection(start, end);
			editor.getCaretModel().moveToOffset(end);
			editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
			editor.getContentComponent().requestFocusInWindow();
		}
	}
}
