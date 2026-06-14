package com.intelli.webrunner.ui;

import javax.swing.JDialog;
import java.awt.Component;

public final class JsonToolDialog {

	private JsonToolDialog() {
	}

	public static void show(Component parent) {
		JDialog dialog = new JDialog();
		dialog.setTitle("JSON");
		dialog.getContentPane().add(new JsonToolPanel().getComponent());
		dialog.setSize(760, 560);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}
}
