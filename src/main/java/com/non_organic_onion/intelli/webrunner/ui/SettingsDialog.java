package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.intelli.webrunner.state.HeaderPresetState;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.table.TableCellEditor;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.JBScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
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
		String collectionsFilePath,
		String settingsFilePath,
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
		JBTextField collectionsPathField = new JBTextField(collectionsFilePath == null ? "" : collectionsFilePath);
		JBTextField settingsPathField = new JBTextField(settingsFilePath == null ? "" : settingsFilePath);
		settingsPathField.setEditable(false);
		JPanel storagePanel = buildStoragePanel(parent, collectionsPathField, settingsPathField);
		tabs.add("Storage", storagePanel);

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
			onSaved.accept(new SettingsResult(
				model.getPresets(),
				stressTestsCheckbox.isSelected(),
				collectionsPathField.getText()
			));
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

	private static JPanel buildStoragePanel(
		Component parent,
		JBTextField collectionsPathField,
		JBTextField settingsPathField
	) {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(8, 8, 0, 8);
		constraints.anchor = GridBagConstraints.WEST;
		constraints.gridy = 0;
		constraints.gridx = 0;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		panel.add(new JLabel("Collections file"), constraints);

		constraints.gridx = 1;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		panel.add(collectionsPathField, constraints);

		JButton browseButton = new JButton("Browse");
		constraints.gridx = 2;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		panel.add(browseButton, constraints);

		constraints.gridy = 1;
		constraints.gridx = 0;
		panel.add(new JLabel("Settings file"), constraints);

		constraints.gridx = 1;
		constraints.gridwidth = 2;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		panel.add(settingsPathField, constraints);

		constraints.gridy = 2;
		constraints.gridx = 0;
		constraints.gridwidth = 3;
		constraints.weighty = 1;
		constraints.fill = GridBagConstraints.BOTH;
		panel.add(new JPanel(), constraints);

		browseButton.addActionListener(e -> chooseCollectionsFile(parent, collectionsPathField));
		return panel;
	}

	private static void chooseCollectionsFile(
		Component parent,
		JBTextField collectionsPathField
	) {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose Webrunner collections file");
		chooser.setFileFilter(new FileNameExtensionFilter("Webrunner JSON collections", "json"));
		String current = collectionsPathField.getText();
		if (current != null && !current.isBlank()) {
			File file = new File(current);
			chooser.setSelectedFile(file);
			File parentDir = file.getParentFile();
			if (parentDir != null) {
				chooser.setCurrentDirectory(parentDir);
			}
		}
		int result = chooser.showSaveDialog(parent);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File selected = chooser.getSelectedFile();
		if (selected != null) {
			collectionsPathField.setText(selected.getAbsolutePath());
		}
	}

	public record SettingsResult(
		List<HeaderPresetState> headerPresets,
		boolean stressTestsEnabled,
		String collectionsFilePath
	) {
	}
}
