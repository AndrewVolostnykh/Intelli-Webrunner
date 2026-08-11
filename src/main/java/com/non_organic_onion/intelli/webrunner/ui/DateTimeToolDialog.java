package com.non_organic_onion.intelli.webrunner.ui;

import javax.swing.JFrame;
import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class DateTimeToolDialog {

	private DateTimeToolDialog() {
	}

	public static void show(Component parent) {
		DateTimeToolPanel panel = new DateTimeToolPanel();
		JFrame dialog = TaskbarWindowSupport.createFrame("DateTime", parent);
		dialog.getContentPane().add(panel.getComponent());
		dialog.setSize(980, 420);
		dialog.setLocationRelativeTo(parent);
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
