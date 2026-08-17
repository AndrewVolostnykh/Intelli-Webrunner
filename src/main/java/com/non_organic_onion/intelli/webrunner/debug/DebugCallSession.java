package com.non_organic_onion.intelli.webrunner.debug;

import com.non_organic_onion.webrunner.core.debug.DebugCallEngine;
import com.non_organic_onion.webrunner.core.debug.DebugStageFormatter;
import com.non_organic_onion.webrunner.core.debug.DebugStageResult;
import com.non_organic_onion.webrunner.core.execution.HttpExecutor;
import com.non_organic_onion.webrunner.core.grpc.GrpcExecutor;
import com.non_organic_onion.webrunner.core.script.GlobalContextRuntime;
import com.non_organic_onion.webrunner.core.script.ScriptRuntime;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.intelli.webrunner.state.IntellijGlobalContextStore;
import com.non_organic_onion.webrunner.core.state.NodeState;
import com.non_organic_onion.webrunner.core.state.RequestDetailsState;
import com.non_organic_onion.webrunner.core.state.RequestStatusState;
import com.non_organic_onion.webrunner.core.state.RequestType;
import com.non_organic_onion.intelli.webrunner.ui.TaskbarWindowSupport;
import com.non_organic_onion.webrunner.core.util.TemplateEngine;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextField;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Future;

/**
 * Step-by-step request runner shown in its own non-modal dialog. Mirrors the normal execution
 * pipeline (before script, templating, transport, after script) but pauses between stages so the
 * user can inspect intermediate state and run inline scripts. Self-contained: it only needs the
 * shared engines, the state service, and a component to anchor the dialog.
 */
public final class DebugCallSession {

	private final Project project;
	private final JComponent parentComponent;
	private final GlobalWebrunnerStateService stateService;
	private final DebugStageFormatter stageFormatter = new DebugStageFormatter();
	private final String requestId;
	private final DebugCallEngine engine;

	private JFrame dialog;
	private ConsoleView outputConsole;
	private JBTextField inlineScriptField;
	private JButton inlineRunButton;
	private JButton nextButton;
	private JButton rerunButton;
	private JButton stopButton;

	private volatile boolean abandoned = false;
	private volatile int runGeneration = 0;
	private Future<?> pendingTask;

	private boolean outputHasContent = false;
	private static final ConsoleViewContentType STEP_OUTPUT =
		new ConsoleViewContentType(
			"WEBRUNNER_DEBUG_STEP_OUTPUT",
			new TextAttributes(new JBColor(new Color(122, 92, 190), new Color(169, 140, 245)), null, null, null, 0)
		);
	private static final ConsoleViewContentType FIELD_OUTPUT =
		new ConsoleViewContentType(
			"WEBRUNNER_DEBUG_FIELD_OUTPUT",
			new TextAttributes(new JBColor(new Color(38, 127, 153), new Color(95, 190, 215)), null, null, null, 0)
		);
	private static final ConsoleViewContentType VALUE_OUTPUT =
		new ConsoleViewContentType(
			"WEBRUNNER_DEBUG_VALUE_OUTPUT",
			new TextAttributes(new JBColor(new Color(45, 45, 45), new Color(220, 220, 220)), null, null, null, 0)
		);
	private static final ConsoleViewContentType SEPARATOR_OUTPUT =
		new ConsoleViewContentType(
			"WEBRUNNER_DEBUG_SEPARATOR_OUTPUT",
			new TextAttributes(JBColor.GRAY, null, null, null, 0)
		);

	public DebugCallSession(
		Project project,
		JComponent parentComponent,
		GlobalWebrunnerStateService stateService,
		ScriptRuntime scriptRuntime,
		TemplateEngine templateEngine,
		HttpExecutor httpExecutor,
		GrpcExecutor grpcExecutor,
		String requestId,
		RequestType requestType
	) {
		this.project = project;
		this.parentComponent = parentComponent;
		this.stateService = stateService;
		this.requestId = requestId;
		GlobalContextRuntime globalContextRuntime =
			new GlobalContextRuntime(new IntellijGlobalContextStore(stateService), scriptRuntime);
		RequestDetailsState details = stateService.getRequestDetails(requestId);
		RequestStatusState status = stateService.getRequestStatus(requestId);
		this.engine = new DebugCallEngine(
			requestId,
			requestType,
			details,
			status,
			stateService.getDefaultTimeoutMillis(),
			globalContextRuntime,
			scriptRuntime,
			templateEngine,
			httpExecutor,
			grpcExecutor
		);
	}

	public void open() {
		String title = "Debug Call";
		NodeState node = stateService.findNode(requestId);
		if (node != null && node.name != null && !node.name.isBlank()) {
			title += " - " + node.name;
		}
		dialog = TaskbarWindowSupport.createFrame(title, parentComponent);
		outputConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

		inlineScriptField = new JBTextField();
		inlineScriptField.setColumns(30);
		inlineScriptField.setToolTipText("Inline JS");

		inlineRunButton = new JButton(AllIcons.Actions.Execute);
		inlineRunButton.setToolTipText("Run Script");
		configureDialogIconButton(inlineRunButton, "Run Script");
		inlineRunButton.setEnabled(false);
		inlineRunButton.addActionListener(e -> runInlineScript());

		nextButton = new JButton(AllIcons.Actions.TraceOver);
		configureDialogIconButton(nextButton, "Next");
		rerunButton = new JButton(AllIcons.Actions.Refresh);
		configureDialogIconButton(rerunButton, "Re-run");
		stopButton = new JButton("\u25A0");
		configureDialogIconButton(stopButton, "Stop");
		stopButton.setForeground(JBColor.RED);
		nextButton.addActionListener(e -> advance());
		rerunButton.addActionListener(e -> rerun());
		stopButton.addActionListener(e -> abandon(true));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		actions.add(inlineScriptField);
		actions.add(inlineRunButton);
		actions.add(rerunButton);
		actions.add(nextButton);
		actions.add(stopButton);

		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(outputConsole.getComponent(), BorderLayout.CENTER);
		dialog.getContentPane().add(actions, BorderLayout.SOUTH);
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(parentComponent);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				abandon(true);
			}

			@Override
			public void windowClosed(WindowEvent e) {
				abandon(false);
			}
		});

		clearOutput();
		dialog.setVisible(true);
		advance();
	}

	private void advance() {
		if (abandoned) {
			return;
		}
		nextButton.setEnabled(false);
		inlineRunButton.setEnabled(false);
		int generation = runGeneration;
		pendingTask = ApplicationManager.getApplication().executeOnPooledThread(() -> {
			DebugStageResult result = engine.nextStage();
			if (result == null || abandoned || generation != runGeneration) {
				return;
			}
			invokeLater(() -> {
				if (abandoned || generation != runGeneration) {
					return;
				}
				appendStage(result);
				nextButton.setEnabled(result.hasNext);
				updateInlineRunButton();
			});
		});
	}

	public void abandon(boolean closeDialog) {
		if (abandoned) {
			return;
		}
		abandoned = true;
		runGeneration++;
		if (pendingTask != null) {
			pendingTask.cancel(true);
		}
		invokeLater(() -> {
			if (outputConsole != null) {
				clearOutput();
			}
			if (closeDialog && dialog != null && dialog.isDisplayable()) {
				dialog.dispose();
			}
		});
	}

	private void rerun() {
		if (abandoned) {
			return;
		}
		runGeneration++;
		if (pendingTask != null) {
			pendingTask.cancel(true);
			pendingTask = null;
		}
		engine.reset();
		clearOutput();
		nextButton.setEnabled(false);
		inlineRunButton.setEnabled(false);
		advance();
	}

	private void runInlineScript() {
		if (abandoned) {
			return;
		}
		String script = inlineScriptField.getText();
		if (script == null || script.isBlank()) {
			return;
		}
		inlineRunButton.setEnabled(false);
		int generation = runGeneration;
		pendingTask = ApplicationManager.getApplication().executeOnPooledThread(() -> {
			DebugStageResult result = engine.executeInlineScript(script);
			if (result == null || abandoned || generation != runGeneration) {
				return;
			}
			invokeLater(() -> {
				if (abandoned || generation != runGeneration) {
					return;
				}
				appendStage(result);
				updateInlineRunButton();
			});
		});
	}

	private void appendStage(DebugStageResult result) {
		String header = result.stageName + " (" + stageFormatter.formatDuration(result.durationNanos) + ")";
		if (outputHasContent) {
			printValue("\n");
		}
		printSeparator("========================================\n");
		printStep(header + "\n");
		printSeparator("========================================\n");
		if (result.lines == null || result.lines.isEmpty()) {
			printValue("<empty>\n");
			outputHasContent = true;
			return;
		}
		for (String line : result.lines) {
			printDebugLine(line == null ? "" : line);
		}
		outputHasContent = true;
	}

	private void printDebugLine(String line) {
		if (line == null || line.isEmpty()) {
			printValue("\n");
			return;
		}
		if (line.endsWith(":")) {
			printField(line + "\n");
			return;
		}
		int separator = line.indexOf(": ");
		if (separator > 0) {
			printField(line.substring(0, separator + 1));
			printValue(" " + line.substring(separator + 2) + "\n");
			return;
		}
		printValue(line + "\n");
	}

	private void clearOutput() {
		if (outputConsole != null) {
			outputConsole.clear();
		}
		outputHasContent = false;
	}

	private void configureDialogIconButton(JButton button, String tooltip) {
		button.setToolTipText(tooltip);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setPreferredSize(new Dimension(28, 28));
		button.setFocusable(false);
		button.setRequestFocusEnabled(false);
	}

	private void updateInlineRunButton() {
		if (inlineRunButton != null) {
			inlineRunButton.setEnabled(!abandoned && engine.isInlineReady());
		}
	}

	private void printStep(String text) {
		outputConsole.print(text, STEP_OUTPUT);
	}

	private void printField(String text) {
		outputConsole.print(text, FIELD_OUTPUT);
	}

	private void printValue(String text) {
		outputConsole.print(text, VALUE_OUTPUT);
	}

	private void printSeparator(String text) {
		outputConsole.print(text, SEPARATOR_OUTPUT);
	}

	private void invokeLater(Runnable runnable) {
		ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
	}

}
