package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.webrunner.core.util.DateTimeField;
import com.non_organic_onion.webrunner.core.util.DateTimeTextService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DateTimeToolPanel {
	private final JPanel root = new JPanel(new BorderLayout());
	private final JCheckBox localCheckbox = new JCheckBox("Local");
	private final JCheckBox fixCheckbox = new JCheckBox("Fix");
	private final JButton convertButton = new JButton("Convert");
	private final JLabel statusLabel = new JLabel(" ");
	private final Map<DateTimeField, JTextField> currentFields = new LinkedHashMap<>();
	private final Map<DateTimeField, JTextField> inputFields = new LinkedHashMap<>();
	private final Timer timer = new Timer(200, e -> updateCurrentTime());

	private Instant fixedInstant;
	private Instant inputInstant = Instant.now();
	private DateTimeField lastInputField = DateTimeField.MILLIS;
	private boolean updatingFields;

	public DateTimeToolPanel() {
		buildUi();
		attachActions();
		updateCurrentTime();
		updateInputFields();
		timer.start();
	}

	public JComponent getComponent() {
		return root;
	}

	public void dispose() {
		timer.stop();
	}

	private void buildUi() {
		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JPanel options = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
		options.add(localCheckbox);
		options.add(fixCheckbox);
		options.add(statusLabel);
		content.add(options, BorderLayout.NORTH);

		JPanel columns = new JPanel(new GridBagLayout());
		columns.add(buildColumn("Current", currentFields, false), columnConstraints(0));
		columns.add(buildColumn("Input", inputFields, true), columnConstraints(1));
		content.add(columns, BorderLayout.CENTER);

		root.add(content, BorderLayout.CENTER);
	}

	private JPanel buildColumn(
		String title,
		Map<DateTimeField, JTextField> fields,
		boolean editable
	) {
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createTitledBorder(title));

		if (editable) {
			JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
			actions.add(convertButton);
			panel.add(actions, BorderLayout.NORTH);
		}

		JPanel fieldsPanel = new JPanel(new GridBagLayout());
		int row = 0;
		for (DateTimeField field : DateTimeField.values()) {
			JLabel label = new JLabel(field.label);
			JTextField textField = new JTextField();
			textField.setEditable(editable);
			textField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textField.getFont().getSize()));
			fields.put(field, textField);

			GridBagConstraints labelConstraints = new GridBagConstraints();
			labelConstraints.gridx = 0;
			labelConstraints.gridy = row;
			labelConstraints.anchor = GridBagConstraints.WEST;
			labelConstraints.insets = new Insets(0, 0, 8, 8);
			fieldsPanel.add(label, labelConstraints);

			GridBagConstraints fieldConstraints = new GridBagConstraints();
			fieldConstraints.gridx = 1;
			fieldConstraints.gridy = row;
			fieldConstraints.weightx = 1;
			fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
			fieldConstraints.insets = new Insets(0, 0, 8, 0);
			fieldsPanel.add(textField, fieldConstraints);
			row++;
		}

		panel.add(fieldsPanel, BorderLayout.CENTER);
		return panel;
	}

	private void attachActions() {
		localCheckbox.addActionListener(e -> {
			updateCurrentTime();
			updateInputFields();
		});
		fixCheckbox.addActionListener(e -> {
			if (fixCheckbox.isSelected()) {
				fixedInstant = Instant.now();
			} else {
				fixedInstant = null;
			}
			updateCurrentTime();
		});
		convertButton.addActionListener(e -> convertInput());
		for (Map.Entry<DateTimeField, JTextField> entry : inputFields.entrySet()) {
			DateTimeField field = entry.getKey();
			entry.getValue().getDocument().addDocumentListener(new DocumentListener() {
				@Override
				public void insertUpdate(DocumentEvent event) {
					handleInputChanged(field);
				}

				@Override
				public void removeUpdate(DocumentEvent event) {
					handleInputChanged(field);
				}

				@Override
				public void changedUpdate(DocumentEvent event) {
					handleInputChanged(field);
				}
			});
		}
	}

	private void updateCurrentTime() {
		Instant instant = fixCheckbox.isSelected() && fixedInstant != null ? fixedInstant : Instant.now();
		writeFields(currentFields, instant);
	}

	private void updateInputFields() {
		writeFields(inputFields, inputInstant);
	}

	private void handleInputChanged(DateTimeField field) {
		if (updatingFields) {
			return;
		}
		lastInputField = field;
		statusLabel.setText(" ");
	}

	private void convertInput() {
		DateTimeField field = lastInputField;
		String value = inputFields.get(field).getText();
		if (value == null || value.isBlank()) {
			statusLabel.setText(" ");
			return;
		}
		try {
			inputInstant = DateTimeTextService.parse(field, value, selectedZone(), LocalDate.now(selectedZone()));
			writeFields(inputFields, inputInstant);
			statusLabel.setText(" ");
		} catch (RuntimeException error) {
			statusLabel.setText("Invalid " + field.label);
		}
	}

	private void writeFields(
		Map<DateTimeField, JTextField> fields,
		Instant instant
	) {
		updatingFields = true;
		try {
			for (Map.Entry<DateTimeField, JTextField> entry : fields.entrySet()) {
				entry.getValue().setText(DateTimeTextService.format(entry.getKey(), instant, selectedZone()));
			}
		} finally {
			updatingFields = false;
		}
	}

	private ZoneId selectedZone() {
		return localCheckbox.isSelected() ? ZoneId.systemDefault() : ZoneOffset.UTC;
	}

	private static GridBagConstraints columnConstraints(int gridx) {
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = gridx;
		constraints.gridy = 0;
		constraints.weightx = 1;
		constraints.weighty = 1;
		constraints.fill = GridBagConstraints.BOTH;
		constraints.insets = new Insets(0, gridx == 0 ? 0 : 8, 0, gridx == 0 ? 8 : 0);
		return constraints;
	}

}
