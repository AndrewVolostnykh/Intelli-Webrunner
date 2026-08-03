package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import javax.swing.JDialog;
import java.awt.Component;

public final class UrlToolDialog {

	private UrlToolDialog() {
	}

	public static void show(Component parent, Project project) {
		JDialog dialog = new JDialog();
		dialog.setTitle("URL");
		dialog.getContentPane().add(new UrlToolPanel(project).getComponent());
		dialog.setSize(900, 520);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}
}
