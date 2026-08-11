package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import java.awt.Component;

public final class Base64ToolDialog {

	private Base64ToolDialog() {
	}

	public static void show(Component parent, Project project) {
		TaskbarWindowSupport.showFrame("Base64", new Base64ToolPanel(project).getComponent(), parent, 900, 520);
	}
}
