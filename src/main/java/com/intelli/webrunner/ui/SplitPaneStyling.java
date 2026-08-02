package com.intelli.webrunner.ui;

import com.intellij.ui.JBColor;

import javax.swing.BorderFactory;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Color;
import java.awt.Graphics;

public final class SplitPaneStyling {
	private static final int DIVIDER_HIT_SIZE = 7;
	private static final int DIVIDER_LINE_SIZE = 1;

	private SplitPaneStyling() {
	}

	public static void applyThinBlackDivider(JSplitPane splitPane) {
		splitPane.setBorder(BorderFactory.createEmptyBorder());
		splitPane.setContinuousLayout(true);
		splitPane.setDividerSize(DIVIDER_HIT_SIZE);
		splitPane.setUI(new ThinDividerSplitPaneUi());
	}

	private static final class ThinDividerSplitPaneUi extends BasicSplitPaneUI {
		@Override
		public BasicSplitPaneDivider createDefaultDivider() {
			return new ThinDivider(this);
		}
	}

	private static final class ThinDivider extends BasicSplitPaneDivider {
		private ThinDivider(BasicSplitPaneUI ui) {
			super(ui);
			setBorder(BorderFactory.createEmptyBorder());
		}

		@Override
		public void paint(Graphics graphics) {
			Color previousColor = graphics.getColor();
			graphics.setColor(JBColor.BLACK);
			if (splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT) {
				int x = (getWidth() - DIVIDER_LINE_SIZE) / 2;
				graphics.fillRect(x, 0, DIVIDER_LINE_SIZE, getHeight());
			} else {
				int y = (getHeight() - DIVIDER_LINE_SIZE) / 2;
				graphics.fillRect(0, y, getWidth(), DIVIDER_LINE_SIZE);
			}
			graphics.setColor(previousColor);
		}
	}
}
