package com.non_organic_onion.intelli.webrunner.ui;

import javax.swing.JDialog;
import java.awt.Component;
import java.util.function.Consumer;

public final class JsonRemoveDialog {

	private JsonRemoveDialog() {
	}

	public static void show(Component parent, Consumer<String> onRemove) {
		show(parent, "Remove from JSON", onRemove);
	}

	public static void show(Component parent, String title, Consumer<String> onRemove) {
		JDialog dialog = new JDialog();
		JsonRemovePanel panel = new JsonRemovePanel(value -> {
			onRemove.accept(value);
			dialog.dispose();
		});
		dialog.setTitle(title);
		dialog.getContentPane().add(panel.getComponent());
		dialog.setSize(520, 140);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}
}
