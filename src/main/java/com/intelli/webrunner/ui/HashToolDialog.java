package com.intelli.webrunner.ui;

import javax.swing.JDialog;
import java.awt.Component;

public final class HashToolDialog {

	private HashToolDialog() {
	}

	public static void show(Component parent) {
		JDialog dialog = new JDialog();
		dialog.setTitle("Hash");
		dialog.getContentPane().add(new HashToolPanel().getComponent());
		dialog.setSize(820, 560);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}
}
