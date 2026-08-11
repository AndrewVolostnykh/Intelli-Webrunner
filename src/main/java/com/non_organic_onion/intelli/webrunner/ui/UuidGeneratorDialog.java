package com.non_organic_onion.intelli.webrunner.ui;

import java.awt.Component;

public final class UuidGeneratorDialog {

	private UuidGeneratorDialog() {
	}

	public static void show(Component parent) {
		TaskbarWindowSupport.showFrame("Generate UUID", new UuidGeneratorPanel().getComponent(), parent, 520, 180);
	}
}
