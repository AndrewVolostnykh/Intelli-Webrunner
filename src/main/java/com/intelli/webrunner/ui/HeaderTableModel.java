package com.intelli.webrunner.ui;

import com.intelli.webrunner.state.HeaderEntryState;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HeaderTableModel extends AbstractTableModel {
    private final List<HeaderEntryState> headers = new ArrayList<>();

    private static final String[] COLUMNS = new String[]{"Enabled", "Name", "Value"};

    public void setHeaders(List<HeaderEntryState> entries) {
        setHeaders(entries, true);
    }

    public void setHeaders(List<HeaderEntryState> entries, boolean includeDefault) {
        headers.clear();
        if (entries != null) {
            headers.addAll(entries);
        }
        fireTableDataChanged();
    }

    public List<HeaderEntryState> getHeaders() {
        return new ArrayList<>(headers);
    }

    public void addEmptyRow() {
        HeaderEntryState entry = new HeaderEntryState();
        entry.id = UUID.randomUUID().toString();
        entry.name = "";
        entry.value = "";
        entry.enabled = true;
        headers.add(entry);
        fireTableRowsInserted(headers.size() - 1, headers.size() - 1);
    }

    public void removeRow(int index) {
        if (index < 0 || index >= headers.size()) {
            return;
        }
        headers.remove(index);
        fireTableRowsDeleted(index, index);
    }

    @Override
    public int getRowCount() {
        return headers.size();
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
        if (rowIndex < 0 || rowIndex >= headers.size()) {
            return "";
        }
        HeaderEntryState entry = headers.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> entry.enabled;
            case 1 -> entry.name;
            case 2 -> entry.value;
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= headers.size()) {
            return;
        }
        HeaderEntryState entry = headers.get(rowIndex);
        if (columnIndex == 0) {
            entry.enabled = Boolean.TRUE.equals(aValue);
        } else if (columnIndex == 1) {
            entry.name = aValue == null ? "" : String.valueOf(aValue);
        } else if (columnIndex == 2) {
            entry.value = aValue == null ? "" : String.valueOf(aValue);
        }
        fireTableCellUpdated(rowIndex, columnIndex);
    }
}
