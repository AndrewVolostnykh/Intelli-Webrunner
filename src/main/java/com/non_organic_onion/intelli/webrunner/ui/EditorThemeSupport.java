package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.EditorTextField;

import javax.swing.UIManager;
import java.awt.Color;

public final class EditorThemeSupport {
	private EditorThemeSupport() {
	}

	public static EditorTextField configure(EditorTextField field) {
		applyComponentColors(field);
		field.addSettingsProvider(EditorThemeSupport::applyEditorColors);
		return field;
	}

	public static void applyEditorColors(EditorEx editor) {
		Color background = themedColor("TextField.background", "EditorPane.background");
		Color foreground = themedColor("TextField.foreground", "EditorPane.foreground");
		if (background != null) {
			editor.setBackgroundColor(background);
			editor.getComponent().setBackground(background);
			editor.getContentComponent().setBackground(background);
		}
		if (foreground != null) {
			editor.getComponent().setForeground(foreground);
			editor.getContentComponent().setForeground(foreground);
		}
	}

	private static void applyComponentColors(EditorTextField field) {
		Color background = themedColor("TextField.background", "EditorPane.background");
		Color foreground = themedColor("TextField.foreground", "EditorPane.foreground");
		if (background != null) {
			field.setBackground(background);
		}
		if (foreground != null) {
			field.setForeground(foreground);
		}
	}

	private static Color themedColor(
		String primaryKey,
		String fallbackKey
	) {
		Color color = UIManager.getColor(primaryKey);
		if (color != null) {
			return color;
		}
		return UIManager.getColor(fallbackKey);
	}
}
