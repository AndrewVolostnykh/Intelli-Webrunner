package com.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import javax.swing.JDialog;
import java.awt.Component;

public final class CompareToolDialog {

	private CompareToolDialog() {
	}

	public static void show(Component parent, Project project) {
		JDialog dialog = new JDialog();
		dialog.setTitle("Compare");
		dialog.getContentPane().add(new CompareToolPanel(project).getComponent());
		dialog.setSize(900, 560);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}
}
