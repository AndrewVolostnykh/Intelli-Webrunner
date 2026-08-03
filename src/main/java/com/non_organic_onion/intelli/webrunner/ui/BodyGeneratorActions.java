package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.intelli.webrunner.proto.ProtoBodyGenerator;
import com.non_organic_onion.intelli.webrunner.proto.ProtoMessageSelection;
import com.non_organic_onion.intelli.webrunner.util.JsonUtils;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;

/**
 * Proto body generation action: prompt for options, pick a {@code .proto} message,
 * and fill the request body field with a sample JSON document.
 */
public final class BodyGeneratorActions {

	private final Project project;
	private final Component parent;
	private final EditorTextField bodyField;

	public BodyGeneratorActions(
		Project project,
		Component parent,
		EditorTextField bodyField
	) {
		this.project = project;
		this.parent = parent;
		this.bodyField = bodyField;
	}

	public void generateFromProto() {
		JCheckBox useNulls = new JCheckBox("Use null values", false);
		JPanel options = new JPanel(new GridLayout(0, 1));
		options.add(useNulls);
		int confirm = JOptionPane.showConfirmDialog(parent, options, "Proto body options", JOptionPane.OK_CANCEL_OPTION);
		if (confirm != JOptionPane.OK_OPTION) {
			return;
		}
		ProtoMessageSelection selection = chooseProtoMessage();
		if (selection == null) {
			return;
		}
		Map<String, Object> body = new ProtoBodyGenerator(project).buildBody(selection, useNulls.isSelected());
		String json = JsonUtils.toJson(body);
		bodyField.setText(json);
		bodyField.requestFocusInWindow();
	}

	private ProtoMessageSelection chooseProtoMessage() {
		VirtualFile file = chooseProtoFile();
		if (file == null) {
			return null;
		}
		List<ProtoMessageSelection> fileMessages = new ProtoBodyGenerator(project).loadMessages(file);
		if (fileMessages.isEmpty()) {
			JOptionPane.showMessageDialog(
				parent,
				"No messages found in selected .proto file.",
				"Proto body",
				JOptionPane.INFORMATION_MESSAGE
			);
			return null;
		}
		DefaultListModel<ProtoMessageSelection> model = new DefaultListModel<>();
		for (ProtoMessageSelection message : fileMessages) {
			model.addElement(message);
		}
		JBList<ProtoMessageSelection> list = new JBList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus
			) {
				Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof ProtoMessageSelection selection) {
					((JLabel) component).setText(selection.getDisplay());
				}
				return component;
			}
		});
		new ListSpeedSearch<>(list, selection -> selection == null ? "" : selection.getDisplay());
		JScrollPane scrollPane = new JBScrollPane(list);
		scrollPane.setPreferredSize(new Dimension(520, 360));
		int confirm =
			JOptionPane.showConfirmDialog(parent, scrollPane, "Select Proto Message", JOptionPane.OK_CANCEL_OPTION);
		if (confirm != JOptionPane.OK_OPTION) {
			return null;
		}
		return list.getSelectedValue();
	}

	private VirtualFile chooseProtoFile() {
		FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("proto");
		descriptor.setTitle("Select Proto File");
		return FileChooser.chooseFile(descriptor, project, null);
	}
}
