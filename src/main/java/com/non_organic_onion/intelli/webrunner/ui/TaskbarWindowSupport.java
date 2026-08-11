package com.non_organic_onion.intelli.webrunner.ui;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.util.List;

/**
 * Creates plugin-owned top-level windows as frames so Windows exposes them in the taskbar.
 */
public final class TaskbarWindowSupport {

	private TaskbarWindowSupport() {
	}

	public static JFrame createFrame(String title, Component parent) {
		JFrame frame = new JFrame(title);
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		copyWindowIcons(frame, parent);
		return frame;
	}

	public static JFrame showFrame(
		String title,
		Component content,
		Component parent,
		int width,
		int height
	) {
		JFrame frame = createFrame(title, parent);
		frame.getContentPane().add(content);
		frame.setSize(width, height);
		frame.setLocationRelativeTo(parent);
		frame.setVisible(true);
		return frame;
	}

	public static int showOpenDialog(JFileChooser chooser, Component parent) {
		return showFileChooser(chooser, parent, JFileChooser.OPEN_DIALOG);
	}

	public static int showSaveDialog(JFileChooser chooser, Component parent) {
		return showFileChooser(chooser, parent, JFileChooser.SAVE_DIALOG);
	}

	public static void showMessageDialog(
		Component parent,
		Object message,
		String title,
		int messageType
	) {
		JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION);
		showOptionPaneAndWait(pane, parent, title);
	}

	public static int showConfirmDialog(
		Component parent,
		Object message,
		String title,
		int optionType
	) {
		JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE, optionType);
		Object value = showOptionPaneAndWait(pane, parent, title);
		return value instanceof Integer integer ? integer : JOptionPane.CLOSED_OPTION;
	}

	public static int showOptionDialog(
		Component parent,
		Object message,
		String title,
		int optionType,
		int messageType,
		Object[] options,
		Object initialValue
	) {
		JOptionPane pane = new JOptionPane(message, messageType, optionType, null, options, initialValue);
		Object value = showOptionPaneAndWait(pane, parent, title);
		if (value == null || value == JOptionPane.UNINITIALIZED_VALUE) {
			return JOptionPane.CLOSED_OPTION;
		}
		if (value instanceof Integer integer) {
			return integer;
		}
		for (int index = 0; index < options.length; index++) {
			if (value.equals(options[index])) {
				return index;
			}
		}
		return JOptionPane.CLOSED_OPTION;
	}

	public static String showInputDialog(
		Component parent,
		Object message,
		String title,
		String initialValue
	) {
		JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		pane.setWantsInput(true);
		pane.setInitialSelectionValue(initialValue);
		Object value = showOptionPaneAndWait(pane, parent, title);
		if (!(value instanceof Integer integer) || integer != JOptionPane.OK_OPTION) {
			return null;
		}
		Object input = pane.getInputValue();
		return input == JOptionPane.UNINITIALIZED_VALUE ? null : String.valueOf(input);
	}

	public static void showFrameAndWait(JFrame frame) {
		EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
		java.awt.SecondaryLoop loop = eventQueue.createSecondaryLoop();
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				loop.exit();
			}
		});
		frame.setVisible(true);
		loop.enter();
	}

	private static int showFileChooser(JFileChooser chooser, Component parent, int dialogType) {
		chooser.setDialogType(dialogType);
		Result result = new Result();
		JFrame frame = createFrame(chooser.getDialogTitle(), parent);
		frame.getContentPane().add(chooser);
		frame.pack();
		frame.setLocationRelativeTo(parent);

		EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
		java.awt.SecondaryLoop loop = eventQueue.createSecondaryLoop();
		chooser.addActionListener(event -> {
			String command = event.getActionCommand();
			result.value = JFileChooser.APPROVE_SELECTION.equals(command)
				? JFileChooser.APPROVE_OPTION
				: JFileChooser.CANCEL_OPTION;
			frame.dispose();
			loop.exit();
		});
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				loop.exit();
			}
		});
		frame.setVisible(true);
		loop.enter();
		return result.value;
	}

	private static Object showOptionPaneAndWait(JOptionPane pane, Component parent, String title) {
		JFrame frame = createFrame(title, parent);
		frame.getContentPane().add(pane);
		frame.pack();
		frame.setLocationRelativeTo(parent);
		EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
		java.awt.SecondaryLoop loop = eventQueue.createSecondaryLoop();
		pane.addPropertyChangeListener(event -> closeOptionPaneFrame(event, frame, loop));
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				if (pane.getValue() == JOptionPane.UNINITIALIZED_VALUE) {
					pane.setValue(JOptionPane.CLOSED_OPTION);
				}
				loop.exit();
			}
		});
		frame.setVisible(true);
		loop.enter();
		return pane.getValue();
	}

	private static void closeOptionPaneFrame(
		PropertyChangeEvent event,
		JFrame frame,
		java.awt.SecondaryLoop loop
	) {
		String property = event.getPropertyName();
		if (!JOptionPane.VALUE_PROPERTY.equals(property)) {
			return;
		}
		Object value = event.getNewValue();
		if (value == null || value == JOptionPane.UNINITIALIZED_VALUE) {
			return;
		}
		frame.dispose();
		loop.exit();
	}

	private static void copyWindowIcons(JFrame frame, Component parent) {
		Window parentWindow = parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent);
		if (parentWindow == null) {
			return;
		}
		List<Image> icons = parentWindow.getIconImages();
		if (icons != null && !icons.isEmpty()) {
			frame.setIconImages(icons);
		}
	}

	private static final class Result {
		private int value = JFileChooser.CANCEL_OPTION;
	}
}
