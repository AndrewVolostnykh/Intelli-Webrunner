package com.intelli.webrunner.ui;

import com.intelli.webrunner.state.HeaderPresetState;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.table.TableCellEditor;
import com.intellij.ui.components.JBScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Consumer;

/**
 * Non-modal settings dialog for editing reusable header presets. On save it hands the edited
 * presets back through {@code onSaved}; the caller persists them and refreshes editors.
 */
public final class SettingsDialog {

	private SettingsDialog() {
	}

	public static void show(
		Component parent,
		List<HeaderPresetState> presets,
		boolean stressTestsEnabled,
		Consumer<SettingsResult> onSaved
	) {
		JDialog dialog = new JDialog();
		dialog.setTitle("Settings");
		JTabbedPane tabs = new JTabbedPane();

		HeaderPresetTableModel model = new HeaderPresetTableModel();
		JPanel headersPanel = new JPanel(new BorderLayout());
		JTable presetsTable = new JTable(model);
		model.setPresets(presets);
		presetsTable.setFillsViewportHeight(true);
		headersPanel.add(new JBScrollPane(presetsTable), BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton addPreset = new JButton("Add");
		JButton removePreset = new JButton("Remove");
		actions.add(addPreset);
		actions.add(removePreset);
		addPreset.addActionListener(e -> model.addEmptyRow());
		removePreset.addActionListener(e -> {
			int row = presetsTable.getSelectedRow();
			model.removeRow(row);
		});
		headersPanel.add(actions, BorderLayout.SOUTH);

		tabs.add("Headers", headersPanel);
		JCheckBox stressTestsCheckbox = new JCheckBox("Stress Tests", stressTestsEnabled);
		JPanel featuresPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		featuresPanel.add(stressTestsCheckbox);
		tabs.add("Features", featuresPanel);

		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveButton = new JButton("Save");
		JButton cancelButton = new JButton("Cancel");
		footer.add(saveButton);
		footer.add(cancelButton);
		saveButton.addActionListener(e -> {
			if (presetsTable.isEditing()) {
				TableCellEditor editor = presetsTable.getCellEditor();
				if (editor != null) {
					editor.stopCellEditing();
				}
			}
			onSaved.accept(new SettingsResult(model.getPresets(), stressTestsCheckbox.isSelected()));
			dialog.dispose();
		});
		cancelButton.addActionListener(e -> dialog.dispose());

		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(tabs, BorderLayout.CENTER);
		dialog.getContentPane().add(footer, BorderLayout.SOUTH);
		dialog.setSize(700, 500);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	public record SettingsResult(List<HeaderPresetState> headerPresets, boolean stressTestsEnabled) {
	}
}
