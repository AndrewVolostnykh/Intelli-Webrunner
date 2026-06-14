package com.intelli.webrunner.ui;

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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DateTimeToolPanel {
	private static final DateTimeFormatter DATE_TIME_FORMATTER =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	private final JPanel root = new JPanel(new BorderLayout());
	private final JCheckBox localCheckbox = new JCheckBox("Local");
	private final JCheckBox fixCheckbox = new JCheckBox("Fix");
	private final JButton convertButton = new JButton("Convert");
	private final JLabel statusLabel = new JLabel(" ");
	private final Map<TimeField, JTextField> currentFields = new LinkedHashMap<>();
	private final Map<TimeField, JTextField> inputFields = new LinkedHashMap<>();
	private final Timer timer = new Timer(200, e -> updateCurrentTime());

	private Instant fixedInstant;
	private Instant inputInstant = Instant.now();
	private TimeField lastInputField = TimeField.MILLIS;
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
		Map<TimeField, JTextField> fields,
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
		for (TimeField field : TimeField.values()) {
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
		for (Map.Entry<TimeField, JTextField> entry : inputFields.entrySet()) {
			TimeField field = entry.getKey();
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

	private void handleInputChanged(TimeField field) {
		if (updatingFields) {
			return;
		}
		lastInputField = field;
		statusLabel.setText(" ");
	}

	private void convertInput() {
		TimeField field = lastInputField;
		String value = inputFields.get(field).getText();
		if (value == null || value.isBlank()) {
			statusLabel.setText(" ");
			return;
		}
		try {
			inputInstant = parse(field, value.trim());
			writeFields(inputFields, inputInstant);
			statusLabel.setText(" ");
		} catch (RuntimeException error) {
			statusLabel.setText("Invalid " + field.label);
		}
	}

	private void writeFields(
		Map<TimeField, JTextField> fields,
		Instant instant
	) {
		updatingFields = true;
		try {
			for (Map.Entry<TimeField, JTextField> entry : fields.entrySet()) {
				entry.getValue().setText(format(entry.getKey(), instant));
			}
		} finally {
			updatingFields = false;
		}
	}

	private String format(
		TimeField field,
		Instant instant
	) {
		ZoneId zone = selectedZone();
		ZonedDateTime dateTime = instant.atZone(zone);
		return switch (field) {
			case MILLIS -> String.valueOf(instant.toEpochMilli());
			case EPOCH_SECONDS -> String.valueOf(instant.getEpochSecond());
			case ISO_8601 -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime);
			case RFC_1123 -> DateTimeFormatter.RFC_1123_DATE_TIME.format(dateTime);
			case DATE_TIME -> DATE_TIME_FORMATTER.format(dateTime);
			case DATE -> DATE_FORMATTER.format(dateTime);
			case TIME -> TIME_FORMATTER.format(dateTime);
		};
	}

	private Instant parse(
		TimeField field,
		String value
	) {
		ZoneId zone = selectedZone();
		return switch (field) {
			case MILLIS -> Instant.ofEpochMilli(Long.parseLong(value));
			case EPOCH_SECONDS -> Instant.ofEpochSecond(Long.parseLong(value));
			case ISO_8601 -> parseIso(value, zone);
			case RFC_1123 -> ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
			case DATE_TIME -> LocalDateTime.parse(value, DATE_TIME_FORMATTER).atZone(zone).toInstant();
			case DATE -> LocalDate.parse(value, DATE_FORMATTER).atStartOfDay(zone).toInstant();
			case TIME -> LocalTime.parse(value, TIME_FORMATTER)
				.atDate(LocalDate.now(zone))
				.atZone(zone)
				.toInstant();
		};
	}

	private Instant parseIso(
		String value,
		ZoneId zone
	) {
		try {
			return ZonedDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
		} catch (DateTimeParseException ignored) {
			return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone).toInstant();
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

	private enum TimeField {
		MILLIS("Millis"),
		EPOCH_SECONDS("Epoch seconds"),
		ISO_8601("ISO 8601"),
		RFC_1123("RFC 1123"),
		DATE_TIME("Date time"),
		DATE("Date"),
		TIME("Time");

		private final String label;

		TimeField(String label) {
			this.label = label;
		}
	}
}
