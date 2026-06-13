package com.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.TextFieldWithAutoCompletion;

import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import java.awt.Component;
import java.util.List;

/**
 * Table cell editor for header/param names backed by an auto-completing text field.
 */
public final class HeaderNameCellEditor extends AbstractCellEditor implements TableCellEditor {

	private final TextFieldWithAutoCompletion<String> field;

	public HeaderNameCellEditor(
		Project project,
		List<String> variants
	) {
		this.field = TextFieldWithAutoCompletion.create(project, variants, true, "");
	}

	@Override
	public Object getCellEditorValue() {
		return field.getText();
	}

	@Override
	public Component getTableCellEditorComponent(
		JTable table,
		Object value,
		boolean isSelected,
		int row,
		int column
	) {
		field.setText(value == null ? "" : String.valueOf(value));
		return field;
	}
}
