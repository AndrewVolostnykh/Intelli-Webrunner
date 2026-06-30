package com.intelli.webrunner.ui;

import javax.swing.JDialog;
import java.awt.Component;

public final class TextToolDialog {

	private TextToolDialog() {
	}

	public static void show(Component parent) {
		JDialog dialog = new JDialog();
		dialog.setTitle("Text");
		dialog.getContentPane().add(new TextToolPanel().getComponent());
		dialog.setSize(760, 560);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}
}
