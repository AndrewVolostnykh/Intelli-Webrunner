package com.non_organic_onion.intelli.webrunner.ui;

import java.awt.Component;

public final class TextToolDialog {

	private TextToolDialog() {
	}

	public static void show(Component parent) {
		TaskbarWindowSupport.showFrame("Text", new TextToolPanel().getComponent(), parent, 760, 560);
	}
}
