package com.non_organic_onion.intelli.webrunner.ui;

import javax.swing.JDialog;
import java.awt.Component;
import java.util.function.BiConsumer;

public final class JsonReplaceDialog {

	private JsonReplaceDialog() {
	}

	public static void show(Component parent, BiConsumer<String, String> onReplace) {
		show(parent, "Replace in JSON", onReplace);
	}

	public static void show(Component parent, String title, BiConsumer<String, String> onReplace) {
		JDialog dialog = new JDialog();
		JsonReplacePanel panel = new JsonReplacePanel((target, replacement) -> {
			onReplace.accept(target, replacement);
			dialog.dispose();
		});
		dialog.setTitle(title);
		dialog.getContentPane().add(panel.getComponent());
		dialog.setSize(560, 190);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}
}
