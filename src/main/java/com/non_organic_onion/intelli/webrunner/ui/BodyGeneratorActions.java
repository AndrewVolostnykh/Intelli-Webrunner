package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.non_organic_onion.intelli.webrunner.proto.ProtoImportIndex;
import com.non_organic_onion.webrunner.core.util.JsonUtils;
import com.non_organic_onion.webrunner.core.proto.ProtoBodyGenerator;
import com.non_organic_onion.webrunner.core.proto.ProtoMessageSelection;
import com.non_organic_onion.webrunner.core.proto.ProtoRegistry;
import com.non_organic_onion.webrunner.core.proto.ProtoSource;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.util.Collection;
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
	private ProtoRegistry selectedRegistry;

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
		int confirm = TaskbarWindowSupport.showConfirmDialog(
			parent,
			options,
			"Proto body options",
			JOptionPane.OK_CANCEL_OPTION
		);
		if (confirm != JOptionPane.OK_OPTION) {
			return;
		}
		ProtoMessageSelection selection = chooseProtoMessage();
		if (selection == null) {
			return;
		}
		Map<String, Object> body =
			new ProtoBodyGenerator().buildBody(selection, selectedRegistry, useNulls.isSelected());
		String json = JsonUtils.toJson(body);
		bodyField.setText(json);
		bodyField.requestFocusInWindow();
	}

	private ProtoMessageSelection chooseProtoMessage() {
		VirtualFile file = chooseProtoFile();
		if (file == null) {
			return null;
		}
		Collection<VirtualFile> files =
			FilenameIndex.getAllFilesByExt(project, "proto", GlobalSearchScope.projectScope(project));
		ProtoImportIndex index = new ProtoImportIndex(project, files);
		ProtoSource root = index.sourceOfRoot(file);
		ProtoBodyGenerator.LoadedProtoMessages loadedMessages = new ProtoBodyGenerator().loadMessages(root, index);
		selectedRegistry = loadedMessages.registry();
		List<ProtoMessageSelection> fileMessages = loadedMessages.selections();
		if (fileMessages.isEmpty()) {
			TaskbarWindowSupport.showMessageDialog(
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
			TaskbarWindowSupport.showConfirmDialog(parent, scrollPane, "Select Proto Message", JOptionPane.OK_CANCEL_OPTION);
		if (confirm != JOptionPane.OK_OPTION) {
			return null;
		}
		return list.getSelectedValue();
	}

	private VirtualFile chooseProtoFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select Proto File");
		chooser.setFileFilter(new FileNameExtensionFilter("Proto files", "proto"));
		int result = TaskbarWindowSupport.showOpenDialog(chooser, parent);
		if (result != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		File file = chooser.getSelectedFile();
		return file == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
	}
}
