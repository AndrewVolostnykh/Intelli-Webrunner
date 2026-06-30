package com.intelli.webrunner.ui;

import com.intelli.webrunner.state.RequestStatusState;
import com.intelli.webrunner.state.RequestType;
import com.intellij.ui.components.JBTextField;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;

public final class StressSettingsPanel {

	private static final String[] JITTER_VALUES = {"0 ms", "50 ms", "100 ms", "250 ms", "500 ms", "1 s", "2 s", "5 s"};
	private static final String[] TIME_UNITS = {"mills", "sec", "min"};

	private final CardLayout cards = new CardLayout();
	private final JPanel root = new JPanel(cards);
	private final JCheckBox enabledCheckBox = new JCheckBox("Enabled");
	private final JBTextField requestsPerSecField = new JBTextField();
	private final JBTextField totalDurationField = new JBTextField();
	private final JComboBox<String> totalDurationUnitCombo = new JComboBox<>(TIME_UNITS);
	private final JBTextField numberOfRequestsField = new JBTextField();
	private final JBTextField parallelWorkersField = new JBTextField();
	private final JBTextField rampUpTimeField = new JBTextField();
	private final JComboBox<String> rampUpTimeUnitCombo = new JComboBox<>(TIME_UNITS);
	private final JBTextField delayBetweenRequestsField = new JBTextField();
	private final JComboBox<String> delayBetweenRequestsUnitCombo = new JComboBox<>(TIME_UNITS);
	private final JComboBox<String> jitterFromCombo = new JComboBox<>(JITTER_VALUES);
	private final JComboBox<String> jitterToCombo = new JComboBox<>(JITTER_VALUES);

	public StressSettingsPanel() {
		enabledCheckBox.setFocusable(false);
		enabledCheckBox.setRequestFocusEnabled(false);
		jitterFromCombo.setEditable(true);
		jitterToCombo.setEditable(true);
		root.add(buildHttpPanel(), "http");
		root.add(buildNotImplementedPanel(), "notImplemented");
	}

	public JComponent getComponent() {
		return root;
	}

	public void showFor(RequestType requestType) {
		cards.show(root, requestType == RequestType.HTTP ? "http" : "notImplemented");
	}

	public void load(RequestStatusState status) {
		enabledCheckBox.setSelected(status != null && status.stressEnabled);
		requestsPerSecField.setText(status != null ? safe(status.stressRequestsPerSec) : "");
		totalDurationField.setText(status != null ? safe(status.stressTotalDuration) : "");
		totalDurationUnitCombo.setSelectedItem(unitOrDefault(status != null ? status.stressTotalDurationUnit : null));
		numberOfRequestsField.setText(status != null ? safe(status.stressNumberOfRequests) : "");
		parallelWorkersField.setText(status != null ? safe(status.stressParallelWorkers) : "");
		rampUpTimeField.setText(status != null ? safe(status.stressRampUpTime) : "");
		rampUpTimeUnitCombo.setSelectedItem(unitOrDefault(status != null ? status.stressRampUpTimeUnit : null));
		delayBetweenRequestsField.setText(status != null ? safe(status.stressDelayBetweenRequests) : "");
		delayBetweenRequestsUnitCombo.setSelectedItem(
			unitOrDefault(status != null ? status.stressDelayBetweenRequestsUnit : null)
		);
		jitterFromCombo.setSelectedItem(status != null ? safe(status.stressJitterFrom) : "");
		jitterToCombo.setSelectedItem(status != null ? safe(status.stressJitterTo) : "");
	}

	public void saveTo(RequestStatusState status) {
		status.stressEnabled = enabledCheckBox.isSelected();
		status.stressRequestsPerSec = requestsPerSecField.getText();
		status.stressTotalDuration = totalDurationField.getText();
		status.stressTotalDurationUnit = comboText(totalDurationUnitCombo);
		status.stressNumberOfRequests = numberOfRequestsField.getText();
		status.stressParallelWorkers = parallelWorkersField.getText();
		status.stressRampUpTime = rampUpTimeField.getText();
		status.stressRampUpTimeUnit = comboText(rampUpTimeUnitCombo);
		status.stressDelayBetweenRequests = delayBetweenRequestsField.getText();
		status.stressDelayBetweenRequestsUnit = comboText(delayBetweenRequestsUnitCombo);
		status.stressJitterFrom = comboText(jitterFromCombo);
		status.stressJitterTo = comboText(jitterToCombo);
	}

	public RequestStatusState snapshot() {
		RequestStatusState status = new RequestStatusState();
		saveTo(status);
		return status;
	}

	public void addAutoSaveListeners(DocumentListener documentListener, ActionListener actionListener) {
		enabledCheckBox.addActionListener(actionListener);
		requestsPerSecField.getDocument().addDocumentListener(documentListener);
		totalDurationField.getDocument().addDocumentListener(documentListener);
		numberOfRequestsField.getDocument().addDocumentListener(documentListener);
		parallelWorkersField.getDocument().addDocumentListener(documentListener);
		rampUpTimeField.getDocument().addDocumentListener(documentListener);
		delayBetweenRequestsField.getDocument().addDocumentListener(documentListener);
		totalDurationUnitCombo.addActionListener(actionListener);
		rampUpTimeUnitCombo.addActionListener(actionListener);
		delayBetweenRequestsUnitCombo.addActionListener(actionListener);
		addComboAutoSave(jitterFromCombo, documentListener, actionListener);
		addComboAutoSave(jitterToCombo, documentListener, actionListener);
	}

	private JPanel buildHttpPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(8, 8, 0, 8);
		constraints.anchor = GridBagConstraints.WEST;

		addEnabledRow(form, constraints, 0);
		addPairRow(
			form,
			constraints,
			1,
			"Requests per second",
			requestsPerSecField,
			null,
			"Total Duration",
			totalDurationField,
			totalDurationUnitCombo
		);
		addPairRow(
			form,
			constraints,
			2,
			"Delay between requests",
			delayBetweenRequestsField,
			delayBetweenRequestsUnitCombo,
			"Ramp-up time",
			rampUpTimeField,
			rampUpTimeUnitCombo
		);
		addPairRow(
			form,
			constraints,
			3,
			"Number of requests",
			numberOfRequestsField,
			"Number of parallel workers",
			parallelWorkersField
		);
		addJitterRow(form, constraints, 4);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(form, BorderLayout.NORTH);
		panel.add(wrapper, BorderLayout.CENTER);
		return panel;
	}

	private void addEnabledRow(
		JPanel form,
		GridBagConstraints constraints,
		int row
	) {
		constraints.gridy = row;
		constraints.gridx = 0;
		constraints.gridwidth = 4;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		form.add(enabledCheckBox, constraints);
		constraints.gridwidth = 1;
	}

	private void addJitterRow(
		JPanel form,
		GridBagConstraints constraints,
		int row
	) {
		constraints.gridy = row;
		constraints.gridx = 0;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		form.add(new JLabel("Jitter (sec)"), constraints);

		JPanel jitterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		jitterFromCombo.setPrototypeDisplayValue("1000 ms");
		jitterToCombo.setPrototypeDisplayValue("1000 ms");
		jitterPanel.add(new JLabel("From"));
		jitterPanel.add(jitterFromCombo);
		jitterPanel.add(new JLabel("  To"));
		jitterPanel.add(jitterToCombo);

		constraints.gridx = 1;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		form.add(jitterPanel, constraints);
	}

	private void addPairRow(
		JPanel form,
		GridBagConstraints constraints,
		int row,
		String firstLabel,
		JBTextField firstField,
		String secondLabel,
		JBTextField secondField
	) {
		addPairRow(form, constraints, row, firstLabel, firstField, null, secondLabel, secondField, null);
	}

	private void addPairRow(
		JPanel form,
		GridBagConstraints constraints,
		int row,
		String firstLabel,
		JBTextField firstField,
		JComboBox<String> firstUnitCombo,
		String secondLabel,
		JBTextField secondField,
		JComboBox<String> secondUnitCombo
	) {
		addField(form, constraints, row, 0, firstLabel, firstField, firstUnitCombo);
		addField(form, constraints, row, 2, secondLabel, secondField, secondUnitCombo);
	}

	private void addField(
		JPanel form,
		GridBagConstraints constraints,
		int row,
		int labelColumn,
		String label,
		JBTextField field,
		JComboBox<String> unitCombo
	) {
		field.setColumns(16);
		constraints.gridy = row;
		constraints.gridx = labelColumn;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.NONE;
		form.add(new JLabel(label), constraints);

		constraints.gridx = labelColumn + 1;
		constraints.weightx = 0.5;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		form.add(fieldWithUnit(field, unitCombo), constraints);
	}

	private JPanel fieldWithUnit(
		JBTextField field,
		JComboBox<String> unitCombo
	) {
		if (unitCombo == null) {
			JPanel panel = new JPanel(new BorderLayout());
			panel.add(field, BorderLayout.CENTER);
			return panel;
		}
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		unitCombo.setFocusable(false);
		unitCombo.setRequestFocusEnabled(false);
		panel.add(field, BorderLayout.CENTER);
		panel.add(unitCombo, BorderLayout.EAST);
		return panel;
	}

	private JPanel buildNotImplementedPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel content = new JPanel(new FlowLayout(FlowLayout.LEFT));
		content.add(new JLabel("Not implemented"));
		panel.add(content, BorderLayout.NORTH);
		return panel;
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private String unitOrDefault(String value) {
		return value == null || value.isBlank() ? "sec" : value;
	}

	private void addComboAutoSave(
		JComboBox<String> combo,
		DocumentListener documentListener,
		ActionListener actionListener
	) {
		combo.addActionListener(actionListener);
		Component editorComponent = combo.getEditor().getEditorComponent();
		if (editorComponent instanceof JTextComponent textComponent) {
			textComponent.getDocument().addDocumentListener(documentListener);
		}
	}

	private String comboText(JComboBox<String> combo) {
		Object item = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
		return item == null ? "" : String.valueOf(item);
	}
}
