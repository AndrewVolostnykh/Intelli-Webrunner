package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.webrunner.core.state.NodeState;
import com.non_organic_onion.webrunner.core.state.NodeType;
import com.non_organic_onion.webrunner.core.state.RequestDetailsState;
import com.non_organic_onion.webrunner.core.state.RequestType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;

/**
 * Renders request-tree nodes with folder icons or request-type badges.
 */
public final class NodeTreeCellRenderer extends DefaultTreeCellRenderer {
	private static final Icon GRPC_UNARY_ICON = IconLoader.getIcon("/icons/proto-u.svg", NodeTreeCellRenderer.class);
	private static final Icon GRPC_CLIENT_STREAM_ICON =
		IconLoader.getIcon("/icons/proto-up.svg", NodeTreeCellRenderer.class);
	private static final Icon GRPC_SERVER_STREAM_ICON =
		IconLoader.getIcon("/icons/proto-down.svg", NodeTreeCellRenderer.class);
	private static final Icon GRPC_BIDI_STREAM_ICON =
		IconLoader.getIcon("/icons/proto-bi.svg", NodeTreeCellRenderer.class);
	private static final Icon KAFKA_SEND_ICON = IconLoader.getIcon("/icons/kafka-send.svg", NodeTreeCellRenderer.class);
	private static final Icon KAFKA_LISTEN_ICON =
		IconLoader.getIcon("/icons/kafka-listen.svg", NodeTreeCellRenderer.class);

	private final GlobalWebrunnerStateService stateService;

	public NodeTreeCellRenderer(GlobalWebrunnerStateService stateService) {
		this.stateService = stateService;
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
		Component component =
			super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
		if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof NodeState state) {
			setText(state.name == null ? "" : state.name);
			if (state.type == NodeType.FOLDER) {
				setIcon(expanded ? getDefaultOpenIcon() : getDefaultClosedIcon());
			} else if (state.requestType == RequestType.GRPC) {
				setIcon(resolveGrpcIcon(state));
			} else if (state.requestType == RequestType.KAFKA) {
				setIcon(KAFKA_SEND_ICON);
			} else if (state.requestType == RequestType.KAFKA_LISTEN) {
				setIcon(KAFKA_LISTEN_ICON);
			} else {
				setIcon(new RequestBadgeIcon(resolveBadgeText(state), resolveBadgeColor(state)));
			}
		}
		return component;
	}

	private Icon resolveGrpcIcon(NodeState state) {
		RequestDetailsState details = stateService.getRequestDetails(state.id);
		String kind = details == null || details.grpcStreamingKind == null ? "" : details.grpcStreamingKind;
		return switch (kind) {
			case "CLIENT" -> GRPC_CLIENT_STREAM_ICON;
			case "SERVER" -> GRPC_SERVER_STREAM_ICON;
			case "BIDI" -> GRPC_BIDI_STREAM_ICON;
			default -> GRPC_UNARY_ICON;
		};
	}

	private String resolveBadgeText(NodeState state) {
		if (state.requestType == RequestType.HTTP) {
			RequestDetailsState details = stateService.getRequestDetails(state.id);
			String method = details == null || details.method == null || details.method.isBlank()
				? "GET"
				: details.method;
			return method.trim().toUpperCase(Locale.ROOT);
		}
		if (state.requestType == RequestType.GRPC) {
			return "gRPC";
		}
		if (state.requestType == RequestType.KAFKA || state.requestType == RequestType.KAFKA_LISTEN) {
			return "KAFKA";
		}
		if (state.requestType == RequestType.CHAIN) {
			return "CHAIN";
		}
		return "REQ";
	}

	private Color resolveBadgeColor(NodeState state) {
		String badge = resolveBadgeText(state);
		return switch (badge) {
			case "GET" -> new JBColor(new Color(36, 145, 70), new Color(61, 168, 92));
			case "POST" -> new JBColor(new Color(41, 108, 205), new Color(68, 133, 222));
			case "PUT" -> new JBColor(new Color(188, 112, 22), new Color(205, 132, 36));
			case "PATCH" -> new JBColor(new Color(126, 83, 190), new Color(151, 108, 215));
			case "DELETE" -> new JBColor(new Color(193, 54, 54), new Color(214, 78, 78));
			case "HEAD" -> new JBColor(new Color(83, 99, 116), new Color(112, 126, 143));
			case "OPTIONS" -> new JBColor(new Color(24, 136, 141), new Color(48, 158, 164));
			case "gRPC" -> new JBColor(new Color(102, 91, 177), new Color(129, 116, 204));
			case "KAFKA" -> new JBColor(new Color(119, 91, 47), new Color(151, 116, 63));
			case "CHAIN" -> new JBColor(new Color(89, 114, 133), new Color(116, 143, 164));
			default -> new JBColor(new Color(92, 99, 112), new Color(120, 127, 140));
		};
	}

	private static final class RequestBadgeIcon implements Icon {
		private static final int HEIGHT = 16;
		private static final int HORIZONTAL_PADDING = 5;
		private static final int MIN_WIDTH = 30;

		private final String text;
		private final Color background;

		private RequestBadgeIcon(String text, Color background) {
			this.text = text;
			this.background = background;
		}

		@Override
		public int getIconWidth() {
			return Math.max(MIN_WIDTH, text.length() * 7 + HORIZONTAL_PADDING * 2);
		}

		@Override
		public int getIconHeight() {
			return HEIGHT;
		}

		@Override
		public void paintIcon(Component c, Graphics graphics, int x, int y) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setFont(getBadgeFont(c));
				FontMetrics metrics = g.getFontMetrics();
				int width = getIconWidth();
				int arc = 6;
				g.setColor(background);
				g.fillRoundRect(x, y, width, HEIGHT, arc, arc);
				g.setColor(Color.WHITE);
				int textX = x + (width - metrics.stringWidth(text)) / 2;
				int textY = y + (HEIGHT - metrics.getHeight()) / 2 + metrics.getAscent();
				g.drawString(text, textX, textY);
			} finally {
				g.dispose();
			}
		}

		private static Font getBadgeFont(Component component) {
			Font base = component == null ? null : component.getFont();
			if (base == null) {
				return new Font(Font.SANS_SERIF, Font.BOLD, 10);
			}
			return base.deriveFont(Font.BOLD, Math.max(10f, base.getSize2D() - 2f));
		}
	}
}
