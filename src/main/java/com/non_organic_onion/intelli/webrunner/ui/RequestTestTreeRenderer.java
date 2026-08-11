package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.intelli.webrunner.state.RequestTestState;
import com.intellij.ui.JBColor;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

public final class RequestTestTreeRenderer extends JPanel implements TreeCellRenderer {

	private final JLabel badgeLabel = new JLabel();
	private final JLabel nameLabel = new JLabel();

	public RequestTestTreeRenderer() {
		setLayout(new BorderLayout(8, 0));
		setOpaque(true);
		badgeLabel.setOpaque(true);
		badgeLabel.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
		badgeLabel.setForeground(Color.WHITE);
		badgeLabel.setFont(badgeLabel.getFont().deriveFont(Font.BOLD, Math.max(10f, badgeLabel.getFont().getSize2D() - 2f)));
		add(badgeLabel, BorderLayout.WEST);
		add(nameLabel, BorderLayout.CENTER);
	}

	@Override
	public Component getTreeCellRendererComponent(
		JTree tree,
		Object value,
		boolean selected,
		boolean expanded,
		boolean leaf,
		int row,
		boolean hasFocus
	) {
		Color background = selected ? UIManager.getColor("Tree.selectionBackground") : tree.getBackground();
		Color foreground = selected ? UIManager.getColor("Tree.selectionForeground") : tree.getForeground();
		if (background == null) {
			background = tree.getBackground();
		}
		if (foreground == null) {
			foreground = tree.getForeground();
		}
		setBackground(background);
		nameLabel.setForeground(foreground);

		Object userObject = value instanceof DefaultMutableTreeNode node ? node.getUserObject() : value;
		TestView test = userObject instanceof TestView testView ? testView : null;
		nameLabel.setText(test == null ? String.valueOf(userObject) : test.name());
		String status = test == null ? "" : test.status();
		if (status.isBlank()) {
			badgeLabel.setVisible(false);
		} else {
			badgeLabel.setText(status);
			badgeLabel.setBackground(resolveStatusColor(status));
			badgeLabel.setVisible(true);
		}
		return this;
	}

	private Color resolveStatusColor(String status) {
		return switch (status) {
			case "Passed" -> new JBColor(new Color(42, 142, 73), new Color(61, 168, 92));
			case "Failed" -> new JBColor(new Color(190, 55, 55), new Color(214, 78, 78));
			case "Disabled" -> JBColor.GRAY;
			default -> JBColor.GRAY;
		};
	}

	public record TestView(String id, String name, boolean base, boolean disabled, String resultStatus) {
		public String status() {
			if (disabled) {
				return "Disabled";
			}
			return resultStatus == null ? "" : resultStatus;
		}

		public static TestView base(String resultStatus) {
			return new TestView(null, "Base", true, false, resultStatus);
		}

		public static TestView test(RequestTestState state) {
			return new TestView(
				state.id,
				state.name == null || state.name.isBlank() ? "Unnamed test" : state.name,
				false,
				state.disabled,
				state.resultStatus
			);
		}
	}
}
