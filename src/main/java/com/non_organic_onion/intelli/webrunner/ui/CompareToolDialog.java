package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import java.awt.Component;

public final class CompareToolDialog {

	private CompareToolDialog() {
	}

	public static void show(Component parent, Project project) {
		TaskbarWindowSupport.showFrame("Compare", new CompareToolPanel(project).getComponent(), parent, 900, 560);
	}
}
