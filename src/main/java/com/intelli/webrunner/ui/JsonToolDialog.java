package com.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import javax.swing.JDialog;
import java.awt.Component;

public final class JsonToolDialog {

	private JsonToolDialog() {
	}

	public static void show(Component parent, Project project) {
		JDialog dialog = new JDialog();
		JsonToolPanel panel = new JsonToolPanel(project);
		dialog.setTitle("JSON");
		dialog.getContentPane().add(panel.getComponent());
		dialog.setSize(760, 560);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
		javax.swing.SwingUtilities.invokeLater(panel::requestEditorFocus);
	}
}
