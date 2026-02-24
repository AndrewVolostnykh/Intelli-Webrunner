package com.intelli.webrunner.ui;

import com.intelli.webrunner.state.FormEntryState;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FormDataTableModel extends AbstractTableModel {
    private final List<FormEntryState> entries = new ArrayList<>();

    private static final String[] COLUMNS = new String[]{"Enabled", "Name", "Type", "Value"};

    public void setEntries(List<FormEntryState> items) {
        entries.clear();
        if (items != null) {
            entries.addAll(items);
        }
        fireTableDataChanged();
    }

    public List<FormEntryState> getEntries() {
        return new ArrayList<>(entries);
    }

    public void addEmptyRow() {
        FormEntryState entry = new FormEntryState();
        entry.id = UUID.randomUUID().toString();
        entry.name = "";
        entry.value = "";
        entry.enabled = true;
        entry.file = false;
        entries.add(entry);
        fireTableRowsInserted(entries.size() - 1, entries.size() - 1);
    }

    public void removeRow(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        entries.remove(index);
        fireTableRowsDeleted(index, index);
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 0 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= entries.size()) {
            return "";
        }
        FormEntryState entry = entries.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> entry.enabled;
            case 1 -> entry.name;
            case 2 -> entry.file ? "File" : "Text";
            case 3 -> entry.value;
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= entries.size()) {
            return;
        }
        FormEntryState entry = entries.get(rowIndex);
        if (columnIndex == 0) {
            entry.enabled = Boolean.TRUE.equals(aValue);
        } else if (columnIndex == 1) {
            entry.name = aValue == null ? "" : String.valueOf(aValue);
        } else if (columnIndex == 2) {
            String text = aValue == null ? "" : String.valueOf(aValue);
            entry.file = "file".equalsIgnoreCase(text);
        } else if (columnIndex == 3) {
            entry.value = aValue == null ? "" : String.valueOf(aValue);
        }
        fireTableCellUpdated(rowIndex, columnIndex);
    }
}
