package com.intelli.webrunner.ui;

import com.intelli.webrunner.state.HeaderPresetState;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class HeaderPresetTableModel extends AbstractTableModel {
    private final List<HeaderPresetState> presets = new ArrayList<>();

    private static final String[] COLUMNS = new String[]{"Header", "Values (comma-separated)"};

    public void setPresets(List<HeaderPresetState> entries) {
        presets.clear();
        if (entries != null) {
            presets.addAll(entries);
        }
        fireTableDataChanged();
    }

    public List<HeaderPresetState> getPresets() {
        List<HeaderPresetState> copy = new ArrayList<>();
        for (HeaderPresetState preset : presets) {
            HeaderPresetState clone = new HeaderPresetState();
            clone.name = preset.name;
            clone.values = preset.values == null ? new ArrayList<>() : new ArrayList<>(preset.values);
            copy.add(clone);
        }
        return copy;
    }

    public void addEmptyRow() {
        HeaderPresetState preset = new HeaderPresetState();
        preset.name = "";
        preset.values = new ArrayList<>();
        presets.add(preset);
        fireTableRowsInserted(presets.size() - 1, presets.size() - 1);
    }

    public void removeRow(int index) {
        if (index < 0 || index >= presets.size()) {
            return;
        }
        presets.remove(index);
        fireTableRowsDeleted(index, index);
    }

    @Override
    public int getRowCount() {
        return presets.size();
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
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= presets.size()) {
            return "";
        }
        HeaderPresetState preset = presets.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> preset.name == null ? "" : preset.name;
            case 1 -> preset.values == null ? "" : String.join(", ", preset.values);
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= presets.size()) {
            return;
        }
        HeaderPresetState preset = presets.get(rowIndex);
        if (columnIndex == 0) {
            preset.name = aValue == null ? "" : String.valueOf(aValue);
        } else if (columnIndex == 1) {
            String raw = aValue == null ? "" : String.valueOf(aValue);
            List<String> values = new ArrayList<>();
            for (String part : raw.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            preset.values = values;
        }
        fireTableCellUpdated(rowIndex, columnIndex);
    }
}
