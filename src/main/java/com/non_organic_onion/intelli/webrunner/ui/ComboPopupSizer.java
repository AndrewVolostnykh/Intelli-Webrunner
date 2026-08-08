package com.non_organic_onion.intelli.webrunner.ui;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.function.IntSupplier;

/**
 * Widens a combo box's drop-down popup to fit its longest entry (up to a caller-provided max),
 * working around the default fixed popup width. Pure Swing; no application state.
 */
public final class ComboPopupSizer {

	private ComboPopupSizer() {
	}

	public static void install(
		JComboBox<String> comboBox,
		IntSupplier maxWidthSupplier
	) {
		comboBox.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				int width = calculatePopupWidth(comboBox, maxWidthSupplier.getAsInt());
				Object popup = comboBox.getUI().getAccessibleChild(comboBox, 0);
				if (popup instanceof BasicComboPopup basicPopup) {
					JList<?> list = basicPopup.getList();
					list.setFixedCellWidth(width);
					setPrototypeValue(list, findLongestValue(comboBox));
					Dimension size = basicPopup.getPreferredSize();
					size.width = width;
					basicPopup.setPreferredSize(size);
					basicPopup.setSize(size);
					Component component = basicPopup.getComponent(0);
					if (component instanceof JScrollPane scrollPane) {
						scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
						Dimension scrollSize = scrollPane.getPreferredSize();
						scrollSize.width = width;
						scrollPane.setPreferredSize(scrollSize);
						scrollPane.setSize(scrollSize);
					}
				}
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
			}
		});
	}

	private static void setPrototypeValue(
		JList<?> list,
		String value
	) {
		@SuppressWarnings("unchecked")
		JList<Object> typed = (JList<Object>) list;
		typed.setPrototypeCellValue(value);
	}

	private static int calculatePopupWidth(
		JComboBox<String> comboBox,
		int maxWidth
	) {
		ListCellRenderer<? super String> renderer = comboBox.getRenderer();
		if (renderer == null) {
			renderer = new DefaultListCellRenderer();
		}
		JList<String> list = new JList<>();
		list.setFont(comboBox.getFont());
		int width = 0;
		ComboBoxModel<String> model = comboBox.getModel();
		for (int i = 0; i < model.getSize(); i++) {
			String value = model.getElementAt(i);
			Component component = renderer.getListCellRendererComponent(list, value, i, false, false);
			int preferredWidth = component.getPreferredSize().width;
			width = Math.max(width, preferredWidth);
		}
		Insets insets = list.getInsets();
		width += (insets.left + insets.right + 12);
		if (width < 240) {
			width = 240;
		}
		if (maxWidth > 0) {
			width = Math.min(width, maxWidth);
		}
		return width;
	}

	private static String findLongestValue(JComboBox<String> comboBox) {
		ComboBoxModel<String> model = comboBox.getModel();
		String longest = "";
		for (int i = 0; i < model.getSize(); i++) {
			String value = model.getElementAt(i);
			if (value != null && value.length() > longest.length()) {
				longest = value;
			}
		}
		return longest;
	}
}
