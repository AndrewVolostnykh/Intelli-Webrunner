package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.webrunner.core.state.NodeState;
import com.intellij.ui.JBColor;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.function.IntFunction;

/**
 * Renders chain entries (request ids) as "name (type)", resolved via the state service.
 * Shared by both the chain list and the request combo.
 */
public final class ChainNodeRenderer extends JPanel implements ListCellRenderer<Object> {

	private final GlobalWebrunnerStateService stateService;
	private final IntFunction<StepMetadata> stepMetadataProvider;
	private final JLabel badgeLabel = new JLabel();
	private final JLabel nameLabel = new JLabel();
	private final JLabel metadataLabel = new JLabel();

	public ChainNodeRenderer(GlobalWebrunnerStateService stateService) {
		this(stateService, null);
	}

	public ChainNodeRenderer(
		GlobalWebrunnerStateService stateService,
		IntFunction<StepMetadata> stepMetadataProvider
	) {
		this.stateService = stateService;
		this.stepMetadataProvider = stepMetadataProvider;
		setLayout(new BorderLayout(8, 0));
		setOpaque(true);
		badgeLabel.setOpaque(true);
		badgeLabel.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
		badgeLabel.setForeground(Color.WHITE);
		badgeLabel.setFont(badgeLabel.getFont().deriveFont(Font.BOLD, Math.max(10f, badgeLabel.getFont().getSize2D() - 2f)));
		metadataLabel.setForeground(JBColor.GRAY);
		add(badgeLabel, BorderLayout.WEST);
		add(nameLabel, BorderLayout.CENTER);
		add(metadataLabel, BorderLayout.EAST);
	}

	@Override
	public Component getListCellRendererComponent(
		JList<?> list,
		Object value,
		int index,
		boolean isSelected,
		boolean cellHasFocus
	) {
		Color background = isSelected ? list.getSelectionBackground() : list.getBackground();
		Color foreground = isSelected ? list.getSelectionForeground() : list.getForeground();
		setBackground(background);
		nameLabel.setForeground(foreground);
		nameLabel.setBackground(background);
		metadataLabel.setBackground(background);
		metadataLabel.setForeground(isSelected ? foreground : JBColor.GRAY);

		if (value instanceof String id) {
			NodeState node = stateService.findNode(id);
			String label = node == null ? id : node.name + " (" + node.requestType + ")";
			nameLabel.setText(label);
		} else {
			nameLabel.setText(value == null ? "" : String.valueOf(value));
		}

		StepMetadata metadata = stepMetadataProvider == null || index < 0 ? null : stepMetadataProvider.apply(index);
		if (metadata == null) {
			badgeLabel.setVisible(false);
			metadataLabel.setText("");
		} else {
			badgeLabel.setText(metadata.status);
			badgeLabel.setBackground(resolveStatusColor(metadata.status));
			badgeLabel.setVisible(true);
			metadataLabel.setText(metadata.details);
		}
		return this;
	}

	private Color resolveStatusColor(String status) {
		return switch (status) {
			case "Passed" -> new JBColor(new Color(42, 142, 73), new Color(61, 168, 92));
			case "Failed" -> new JBColor(new Color(190, 55, 55), new Color(214, 78, 78));
			case "Interrupted" -> new JBColor(new Color(190, 55, 55), new Color(214, 78, 78));
			case "Skipped" -> new JBColor(new Color(188, 112, 22), new Color(205, 132, 36));
			default -> JBColor.GRAY;
		};
	}

	public static final class StepMetadata {
		private final String status;
		private final String details;

		public StepMetadata(String status, String details) {
			this.status = status;
			this.details = details;
		}
	}
}
