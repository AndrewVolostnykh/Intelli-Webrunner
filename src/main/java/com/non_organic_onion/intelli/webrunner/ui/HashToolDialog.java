package com.non_organic_onion.intelli.webrunner.ui;

import java.awt.Component;

public final class HashToolDialog {

	private HashToolDialog() {
	}

	public static void show(Component parent) {
		TaskbarWindowSupport.showFrame("Hash", new HashToolPanel().getComponent(), parent, 820, 560);
	}
}
