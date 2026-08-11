package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import java.awt.Component;

public final class JsonToolDialog {

	private JsonToolDialog() {
	}

	public static void show(Component parent, Project project) {
		JsonToolPanel panel = new JsonToolPanel(project);
		TaskbarWindowSupport.showFrame("JSON", panel.getComponent(), parent, 760, 560);
		javax.swing.SwingUtilities.invokeLater(panel::requestEditorFocus);
	}
}
