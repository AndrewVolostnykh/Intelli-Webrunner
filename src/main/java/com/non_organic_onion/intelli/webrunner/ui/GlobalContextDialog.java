package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.intelli.webrunner.state.GlobalContextState;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;

/**
 * UI-only dialog for future global context support. It currently owns only editable controls;
 * persistence and execution integration are intentionally left out.
 */
public final class GlobalContextDialog {

	private GlobalContextDialog() {
	}

	public static void show(
		Component parent,
		Project project,
		GlobalWebrunnerStateService stateService
	) {
		JFrame dialog = TaskbarWindowSupport.createFrame("Global Context", parent);
		dialog.getContentPane().add(buildContent(project, stateService, dialog));
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	private static JComponent buildContent(
		Project project,
		GlobalWebrunnerStateService stateService,
		JFrame dialog
	) {
		GlobalContextState state = stateService.getGlobalContext();
		HeaderTableModel variablesModel = new HeaderTableModel();
		variablesModel.setHeaders(state.variables, false);
		EditorTextField scriptField = createScriptField(project);
		scriptField.setText(state.script == null ? "" : state.script);

		JTabbedPane tabs = new JTabbedPane();
		tabs.add("\u0417\u043C\u0456\u043D\u043D\u0456", buildVariablesPanel(variablesModel));
		tabs.add("JS Code", new JBScrollPane(scriptField));

		JPanel root = new JPanel(new BorderLayout());
		root.add(tabs, BorderLayout.CENTER);

		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveButton = new JButton("Save");
		JButton cancelButton = new JButton("Cancel");
		saveButton.addActionListener(e -> {
			stopTableEditing(tabs);
			GlobalContextState updated = new GlobalContextState();
			updated.variables = variablesModel.getHeaders();
			updated.script = scriptField.getText();
			stateService.saveGlobalContext(updated);
			dialog.dispose();
		});
		cancelButton.addActionListener(e -> dialog.dispose());
		footer.add(saveButton);
		footer.add(cancelButton);
		root.add(footer, BorderLayout.SOUTH);
		return root;
	}

	private static JComponent buildVariablesPanel(HeaderTableModel model) {
		JTable table = new JTable(model);
		table.setFillsViewportHeight(true);
		configureEnabledColumn(table);

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JBScrollPane(table), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton addButton = new JButton("Add");
		JButton removeButton = new JButton("Remove");
		addButton.addActionListener(e -> model.addEmptyRow());
		removeButton.addActionListener(e -> model.removeRow(table.getSelectedRow()));
		actions.add(addButton);
		actions.add(removeButton);
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	private static EditorTextField createScriptField(Project project) {
		EditorTextField scriptField =
			EditorThemeSupport.configure(new EditorTextField("", project, resolveScriptFileType()));
		scriptField.setOneLineMode(false);
		return scriptField;
	}

	private static void configureEnabledColumn(JTable table) {
		TableColumn enabledColumn = table.getColumnModel().getColumn(0);
		enabledColumn.setPreferredWidth(60);
		enabledColumn.setMinWidth(60);
		enabledColumn.setMaxWidth(60);
		enabledColumn.setResizable(false);
	}

	private static FileType resolveScriptFileType() {
		FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension("js");
		if (fileType == null || fileType == PlainTextFileType.INSTANCE) {
			fileType = PlainTextFileType.INSTANCE;
		}
		return fileType;
	}

	private static void stopTableEditing(JTabbedPane tabs) {
		for (int i = 0; i < tabs.getTabCount(); i++) {
			Component component = tabs.getComponentAt(i);
			if (component instanceof JPanel panel) {
				stopTableEditing(panel);
			}
		}
	}

	private static void stopTableEditing(JPanel panel) {
		for (Component component : panel.getComponents()) {
			if (component instanceof JBScrollPane scrollPane
				&& scrollPane.getViewport().getView() instanceof JTable table
				&& table.isEditing()) {
				TableCellEditor editor = table.getCellEditor();
				if (editor != null) {
					editor.stopCellEditing();
				}
			} else if (component instanceof JPanel childPanel) {
				stopTableEditing(childPanel);
			}
		}
	}
}
