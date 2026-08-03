package com.non_organic_onion.intelli.webrunner.ui;

import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import java.awt.Component;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Editable combo-box cell editor for header values. Suggestions come from a header-preset map
 * supplied lazily so it always reflects the current presets.
 */
public final class HeaderValueCellEditor extends AbstractCellEditor implements TableCellEditor {

	private final JComboBox<String> combo = new JComboBox<>();
	private final Supplier<Map<String, List<String>>> presetMapSupplier;

	public HeaderValueCellEditor(Supplier<Map<String, List<String>>> presetMapSupplier) {
		this.presetMapSupplier = presetMapSupplier;
		combo.setEditable(true);
	}

	@Override
	public Object getCellEditorValue() {
		Object value = combo.getEditor().getItem();
		return value == null ? "" : String.valueOf(value);
	}

	@Override
	public Component getTableCellEditorComponent(
		JTable table,
		Object value,
		boolean isSelected,
		int row,
		int column
	) {
		String current = value == null ? "" : String.valueOf(value);
		combo.removeAllItems();
		String headerName = "";
		Object nameValue = table.getValueAt(row, 1);
		if (nameValue != null) {
			headerName = String.valueOf(nameValue);
		}
		Map<String, List<String>> presetMap = presetMapSupplier.get();
		List<String> values =
			presetMap.getOrDefault(headerName.trim().toLowerCase(Locale.ROOT), List.of());
		if (!values.isEmpty()) {
			for (String item : values) {
				combo.addItem(item);
			}
		}
		combo.setSelectedItem(current);
		return combo;
	}
}
