package com.intelli.webrunner.ui;

import javax.swing.JDialog;
import java.awt.Component;

public final class UuidGeneratorDialog {

	private UuidGeneratorDialog() {
	}

	public static void show(Component parent) {
		JDialog dialog = new JDialog();
		dialog.setTitle("Generate UUID");
		dialog.getContentPane().add(new UuidGeneratorPanel().getComponent());
		dialog.setSize(520, 180);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}
}
