package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;

import java.awt.Component;

public final class JwtDecoderDialog {

	private JwtDecoderDialog() {
	}

	public static void show(Component parent, Project project) {
		TaskbarWindowSupport.showFrame("JWT Decoder", new JwtDecoderPanel(project).getComponent(), parent, 1000, 700);
	}
}
