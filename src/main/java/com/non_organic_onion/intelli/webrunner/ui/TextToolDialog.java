package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import java.awt.Component;

public final class TextToolDialog {

	private TextToolDialog() {
	}

	public static void show(Component parent, Project project) {
		TaskbarWindowSupport.showFrame("Text", new TextToolPanel(project).getComponent(), parent, 760, 560);
	}
}
