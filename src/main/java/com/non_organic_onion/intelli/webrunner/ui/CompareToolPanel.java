package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

public final class CompareToolPanel {
	private final Project project;
	private final JPanel root = new JPanel(new BorderLayout(8, 8));
	private final JTextArea leftField = createTextArea();
	private final JTextArea rightField = createTextArea();
	private final JButton compareButton = new JButton("Compare");

	public CompareToolPanel(Project project) {
		this.project = project;
		buildUi();
		attachActions();
	}

	public JComponent getComponent() {
		return root;
	}

	private void buildUi() {
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JPanel fields = new JPanel(new GridLayout(1, 2, 8, 0));
		fields.add(createTextPanel("Left", leftField));
		fields.add(createTextPanel("Right", rightField));
		root.add(fields, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.add(compareButton);
		root.add(actions, BorderLayout.SOUTH);
	}

	private void attachActions() {
		compareButton.addActionListener(e -> showDiff());
	}

	private void showDiff() {
		DiffContentFactory contentFactory = DiffContentFactory.getInstance();
		DiffContent leftContent = contentFactory.create(project, leftField.getText());
		DiffContent rightContent = contentFactory.create(project, rightField.getText());
		SimpleDiffRequest request = new SimpleDiffRequest(
			"Compare",
			leftContent,
			rightContent,
			"Left",
			"Right"
		);
		DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME);
	}

	private static JPanel createTextPanel(String title, JTextArea textArea) {
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.add(new JLabel(title), BorderLayout.NORTH);
		panel.add(new JBScrollPane(textArea), BorderLayout.CENTER);
		return panel;
	}

	private static JTextArea createTextArea() {
		JTextArea field = new JTextArea();
		field.setLineWrap(true);
		field.setWrapStyleWord(false);
		field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, field.getFont().getSize()));
		return field;
	}
}
