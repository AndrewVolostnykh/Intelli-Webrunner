package com.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import javax.swing.JDialog;
import java.awt.Component;

public final class JwtDecoderDialog {

	private JwtDecoderDialog() {
	}

	public static void show(Component parent, Project project) {
		JDialog dialog = new JDialog();
		dialog.setTitle("JWT Decoder");
		dialog.getContentPane().add(new JwtDecoderPanel(project).getComponent());
		dialog.setSize(1000, 700);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}
}
