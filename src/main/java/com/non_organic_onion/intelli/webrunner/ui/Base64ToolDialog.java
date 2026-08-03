package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import javax.swing.JDialog;
import java.awt.Component;

public final class Base64ToolDialog {

	private Base64ToolDialog() {
	}

	public static void show(Component parent, Project project) {
		JDialog dialog = new JDialog();
		dialog.setTitle("Base64");
		dialog.getContentPane().add(new Base64ToolPanel(project).getComponent());
		dialog.setSize(900, 520);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}
}
