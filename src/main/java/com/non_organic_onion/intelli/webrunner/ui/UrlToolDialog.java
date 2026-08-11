package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import java.awt.Component;

public final class UrlToolDialog {

	private UrlToolDialog() {
	}

	public static void show(Component parent, Project project) {
		TaskbarWindowSupport.showFrame("URL", new UrlToolPanel(project).getComponent(), parent, 900, 520);
	}
}
