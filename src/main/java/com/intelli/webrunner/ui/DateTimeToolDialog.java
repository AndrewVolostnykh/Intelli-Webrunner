package com.intelli.webrunner.ui;

import javax.swing.JDialog;
import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class DateTimeToolDialog {

	private DateTimeToolDialog() {
	}

	public static void show(Component parent) {
		JDialog dialog = new JDialog();
		DateTimeToolPanel panel = new DateTimeToolPanel();
		dialog.setTitle("DateTime");
		dialog.getContentPane().add(panel.getComponent());
		dialog.setSize(980, 420);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				panel.dispose();
			}

			@Override
			public void windowClosing(WindowEvent event) {
				panel.dispose();
			}
		});
		dialog.setVisible(true);
	}
}
