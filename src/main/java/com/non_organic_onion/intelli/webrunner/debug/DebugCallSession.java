package com.non_organic_onion.intelli.webrunner.debug;

import com.non_organic_onion.intelli.webrunner.execution.HttpExecutionResponse;
import com.non_organic_onion.intelli.webrunner.execution.HttpExecutor;
import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutionResponse;
import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutor;
import com.non_organic_onion.intelli.webrunner.script.GlobalContextRuntime;
import com.non_organic_onion.intelli.webrunner.script.ScriptContext;
import com.non_organic_onion.intelli.webrunner.script.ScriptHelpers;
import com.non_organic_onion.intelli.webrunner.script.ScriptLogger;
import com.non_organic_onion.intelli.webrunner.script.ScriptRequest;
import com.non_organic_onion.intelli.webrunner.script.ScriptRuntime;
import com.non_organic_onion.intelli.webrunner.script.VarsStore;
import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import com.non_organic_onion.intelli.webrunner.state.IntellijGlobalContextStore;
import com.non_organic_onion.intelli.webrunner.state.NodeState;
import com.non_organic_onion.intelli.webrunner.state.RequestDetailsState;
import com.non_organic_onion.intelli.webrunner.state.RequestStatusState;
import com.non_organic_onion.intelli.webrunner.state.RequestType;
import com.non_organic_onion.intelli.webrunner.ui.TaskbarWindowSupport;
import com.non_organic_onion.intelli.webrunner.util.HttpStatusReasons;
import com.non_organic_onion.intelli.webrunner.util.PayloadTypes;
import com.non_organic_onion.intelli.webrunner.util.StateCopyUtils;
import com.non_organic_onion.intelli.webrunner.util.TemplateEngine;
import com.non_organic_onion.intelli.webrunner.util.UrlParamUtils;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
	private final ScriptRuntime scriptRuntime;
	private final TemplateEngine templateEngine;
	private final HttpExecutor httpExecutor;
	private final GrpcExecutor grpcExecutor;
	private final GlobalContextRuntime globalContextRuntime;
	private final DebugStageFormatter stageFormatter = new DebugStageFormatter();
	private final DebugRequestPolicy requestPolicy = new DebugRequestPolicy();

	private final String requestId;
	private final RequestType requestType;
	private final RequestDetailsState details;
	private final RequestStatusState status;

	private JFrame dialog;
	private ConsoleView outputConsole;
	private JBTextField inlineScriptField;
	private JButton inlineRunButton;
	private JButton nextButton;
	private JButton rerunButton;
	private JButton stopButton;

	private int stepIndex = 1;
	private volatile boolean abandoned = false;
	private volatile int runGeneration = 0;
	private Future<?> pendingTask;

	private VarsStore vars;
	private VarsStore globalContext;
	private ScriptHelpers helpers;
	private ScriptLogger logger;
	private List<String> logs;
	private List<String> beforeLogs = List.of();
	private List<String> afterLogs = List.of();
	private ScriptRequest rawRequest;
	private ScriptRequest beforeRequest;
	private ScriptRequest afterRequest;
	private ScriptRequest currentRequest;
	private String templatedBody = "";
	private List<HeaderEntryState> templatedHeaders = List.of();
	private List<HeaderEntryState> templatedParams = List.of();
	private String templatedUrlBase = "";
	private String templatedUrl = "";
	private String templatedGrpcTarget = "";
	private String templatedGrpcService = "";
	private String templatedGrpcMethod = "";
	private HttpExecutionResponse httpResponse;
	private GrpcExecutionResponse grpcResponse;
	private boolean beforeFailed = false;
	private boolean requestFailed = false;
	private String validationError;
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
		this.scriptRuntime = scriptRuntime;
		this.templateEngine = templateEngine;
		this.httpExecutor = httpExecutor;
		this.grpcExecutor = grpcExecutor;
		this.globalContextRuntime = new GlobalContextRuntime(new IntellijGlobalContextStore(stateService), scriptRuntime);
		this.requestId = requestId;
		this.requestType = requestType;
		this.details = stateService.getRequestDetails(requestId);
		this.status = stateService.getRequestStatus(requestId);
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
			DebugStageResult result = runStage(stepIndex);
			if (result == null || abandoned || generation != runGeneration) {
				return;
			}
			invokeLater(() -> {
				if (abandoned || generation != runGeneration) {
					return;
				}
				appendStage(result);
				stepIndex++;
				nextButton.setEnabled(result.hasNext);
				updateInlineRunButton();
			});
		});
	}

	private DebugStageResult runStage(int step) {
		return switch (step) {
			case 1 -> buildBeforeStage();
			case 2 -> buildResponseStage();
			case 3 -> buildAfterStage();
			case 4 -> buildFinalStage();
			default -> null;
		};
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
		resetExecutionState();
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
			DebugStageResult result = executeInlineScript(script);
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

	private void resetExecutionState() {
		stepIndex = 1;
		vars = null;
		globalContext = null;
		helpers = null;
		logger = null;
		logs = null;
		beforeLogs = List.of();
		afterLogs = List.of();
		rawRequest = null;
		beforeRequest = null;
		afterRequest = null;
		currentRequest = null;
		templatedBody = "";
		templatedHeaders = List.of();
		templatedParams = List.of();
		templatedUrlBase = "";
		templatedUrl = "";
		templatedGrpcTarget = "";
		templatedGrpcService = "";
		templatedGrpcMethod = "";
		httpResponse = null;
		grpcResponse = null;
		beforeFailed = false;
		requestFailed = false;
		validationError = null;
	}

	private DebugStageResult executeInlineScript(String script) {
		long start = System.nanoTime();
		List<String> lines = new ArrayList<>();
		if (vars == null || logger == null || helpers == null) {
			lines.add("Context not ready. Run Next first.");
			long duration = System.nanoTime() - start;
			return new DebugStageResult("Inline Script", duration, lines, nextButton.isEnabled());
		}
		if (logs == null) {
			logs = new ArrayList<>();
		}
		int logStart = logs.size();
		ScriptRequest contextRequest = afterRequest != null ? afterRequest : currentRequest;
		if (contextRequest == null) {
			contextRequest = new ScriptRequest("", List.of(), List.of());
		}
		Object response = null;
		if (requestType == RequestType.HTTP) {
			response = httpResponse;
		} else if (requestType == RequestType.GRPC) {
			response = grpcResponse;
		}
		try {
			scriptRuntime.runScript(
				script,
				new ScriptContext(vars, logger, helpers, contextRequest, rawRequest, response, globalContext)
			);
			globalContextRuntime.persist(globalContext);
		} catch (Exception error) {
			logs.add("Inline script error: " + error.getMessage());
		}
		List<String> scriptLogs =
			logs.size() == logStart ? List.of() : new ArrayList<>(logs.subList(logStart, logs.size()));
		lines.addAll(stageFormatter.formatLogs("Inline script logs", scriptLogs));
		long duration = System.nanoTime() - start;
		return new DebugStageResult("Inline Script", duration, lines, nextButton.isEnabled());
	}

	private DebugStageResult buildInitialStage() {
		long start = System.nanoTime();
		List<String> lines = new ArrayList<>();
		lines.add("Request Id: " + requestId);
		if (requestType == RequestType.HTTP) {
			String method = details == null || details.method == null ? "GET" : details.method;
			String url = details == null || details.url == null ? "" : details.url;
			lines.add("Method: " + method);
			lines.add("URL: " + (url.isBlank() ? "<missing>" : url));
		} else {
			String target = details == null || details.target == null ? "" : details.target;
			String service = details == null || details.service == null ? "" : details.service;
			String method = details == null || details.grpcMethod == null ? "" : details.grpcMethod;
			lines.add("Target: " + (target.isBlank() ? "<missing>" : target));
			lines.add("Service: " + (service.isBlank() ? "<missing>" : service));
			lines.add("Method: " + (method.isBlank() ? "<missing>" : method));
		}
		ScriptRequest snapshot = new ScriptRequest(
			status == null ? "" : status.requestBody,
			status == null ? List.of() : status.requestHeaders,
			status == null ? List.of() : status.requestParams
		);
		if (status != null) {
			snapshot.setFormData(StateCopyUtils.cloneFormData(status.formData));
			snapshot.setBinaryFilePath(status.binaryFilePath);
		}
		lines.addAll(stageFormatter.formatRequestSnapshot(snapshot));
		long duration = System.nanoTime() - start;
		return new DebugStageResult("Current Request", duration, lines, true);
	}

	private DebugStageResult buildBeforeStage() {
		long start = System.nanoTime();
		List<String> lines = new ArrayList<>();
		String body = status == null ? "" : safe(status.requestBody);
		List<HeaderEntryState> headers = status == null ? List.of() : status.requestHeaders;
		List<HeaderEntryState> params = status == null ? List.of() : status.requestParams;
		List<FormEntryState> formData = status == null ? List.of() : status.formData;
		String binaryFilePath = status == null ? "" : safe(status.binaryFilePath);
		String before = status == null ? "" : safe(status.beforeScript);

		logs = new ArrayList<>();
		logger = message -> logs.add(message);
		helpers = new ScriptHelpers(logger);
		vars = new VarsStore();
		try {
			globalContext = globalContextRuntime.loadAndRun(logger);
		} catch (Exception error) {
			logs.add("Global context error: " + error.getMessage());
			globalContext = new VarsStore();
		}
		rawRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		rawRequest.setFormData(StateCopyUtils.cloneFormData(formData));
		rawRequest.setBinaryFilePath(binaryFilePath);
		beforeRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		beforeRequest.setFormData(StateCopyUtils.cloneFormData(formData));
		beforeRequest.setBinaryFilePath(binaryFilePath);

		validationError = requestPolicy.validateDetails(requestType, details);
		if (validationError != null) {
			beforeFailed = true;
			lines.add("Error: " + validationError);
		} else {
			try {
				scriptRuntime.runScript(
					before,
					new ScriptContext(vars, logger, helpers, beforeRequest, rawRequest, null, globalContext)
				);
			} catch (Exception error) {
				logs.add("Before request error: " + error.getMessage());
				beforeFailed = true;
			}
		}
		beforeLogs = logs.isEmpty() ? List.of() : new ArrayList<>(logs);

		if (!beforeFailed) {
			Map<String, Object> varsSnapshot = globalContextRuntime.mergeForTemplates(globalContext, vars);
			templatedBody = templateEngine.applyToBody(beforeRequest.getBody(), varsSnapshot);
			templatedHeaders = templateEngine.applyToHeaders(beforeRequest.getHeaders(), varsSnapshot);
			templatedParams = templateEngine.applyToParams(beforeRequest.getParams(), varsSnapshot);
			List<FormEntryState> templatedFormData =
				templateEngine.applyToFormData(beforeRequest.getFormData(), varsSnapshot);
			String templatedBinaryPath = templateEngine.applyToText(
				beforeRequest.getBinaryFilePath(),
				varsSnapshot
			);
			if (requestType == RequestType.HTTP) {
				String url = details == null || details.url == null ? "" : details.url;
				templatedUrlBase = templateEngine.applyToText(url, varsSnapshot);
				templatedUrl = UrlParamUtils.applyDefaultProtocol(
					UrlParamUtils.replaceQueryParams(templatedUrlBase, templatedParams)
				);
			} else if (requestType == RequestType.GRPC) {
				templatedGrpcTarget = templateEngine.applyToText(details == null ? "" : details.target, varsSnapshot);
				templatedGrpcService = templateEngine.applyToText(details == null ? "" : details.service, varsSnapshot);
				templatedGrpcMethod = templateEngine.applyToText(details == null ? "" : details.grpcMethod, varsSnapshot);
			}
			currentRequest = new ScriptRequest(templatedBody, templatedHeaders, templatedParams);
			currentRequest.setFormData(StateCopyUtils.cloneFormData(templatedFormData));
			currentRequest.setBinaryFilePath(templatedBinaryPath);
		} else {
			templatedBody = beforeRequest.getBody();
			templatedHeaders = beforeRequest.getHeaders();
			templatedParams = beforeRequest.getParams();
			templatedUrlBase = details == null || details.url == null ? "" : details.url;
			templatedUrl = templatedUrlBase;
			templatedGrpcTarget = details == null || details.target == null ? "" : details.target;
			templatedGrpcService = details == null || details.service == null ? "" : details.service;
			templatedGrpcMethod = details == null || details.grpcMethod == null ? "" : details.grpcMethod;
			currentRequest = new ScriptRequest(templatedBody, templatedHeaders, templatedParams);
			currentRequest.setFormData(StateCopyUtils.cloneFormData(beforeRequest.getFormData()));
			currentRequest.setBinaryFilePath(beforeRequest.getBinaryFilePath());
		}
		globalContextRuntime.persist(globalContext);

		if (requestType == RequestType.HTTP) {
			String method = details == null || details.method == null ? "GET" : details.method;
			lines.add("Method: " + method);
			lines.add("URL: " + (templatedUrl == null || templatedUrl.isBlank() ? "<missing>" : templatedUrl));
		} else {
			String target = templatedGrpcTarget == null ? "" : templatedGrpcTarget;
			String service = templatedGrpcService == null ? "" : templatedGrpcService;
			String method = templatedGrpcMethod == null ? "" : templatedGrpcMethod;
			lines.add("Target: " + (target.isBlank() ? "<missing>" : target));
			lines.add("Service: " + (service.isBlank() ? "<missing>" : service));
			lines.add("Method: " + (method.isBlank() ? "<missing>" : method));
		}

		lines.add("Request:");
		lines.addAll(stageFormatter.formatRequestSnapshot(currentRequest));
		lines.addAll(stageFormatter.formatLogs("Before request logs", beforeLogs));
		if (beforeFailed) {
			lines.add("Request will not be sent.");
		}

		long duration = System.nanoTime() - start;
		return new DebugStageResult("Sent Request", duration, lines, true);
	}

	private DebugStageResult buildResponseStage() {
		long start = System.nanoTime();
		List<String> lines = new ArrayList<>();
		if (beforeFailed) {
			lines.add("Request skipped because before request failed.");
			long duration = System.nanoTime() - start;
			return new DebugStageResult("Response Received", duration, lines, true);
		}
		try {
			if (requestType == RequestType.HTTP) {
				String method = details == null || details.method == null ? "GET" : details.method;
				String payloadType = details == null ? "RAW" : details.payloadType;
				ScriptRequest requestToSend = currentRequest == null
					? new ScriptRequest(templatedBody, templatedHeaders, templatedParams)
					: currentRequest;
				templatedBody = requestToSend.getBody();
				templatedHeaders = requestToSend.getHeaders() == null ? List.of() : requestToSend.getHeaders();
				templatedParams = requestToSend.getParams() == null ? List.of() : requestToSend.getParams();
				templatedUrl = UrlParamUtils.applyDefaultProtocol(
					UrlParamUtils.replaceQueryParams(templatedUrlBase, templatedParams)
				);
				List<FormEntryState> formData =
					requestToSend.getFormData() == null ? List.of() : requestToSend.getFormData();
				String binaryPath =
					requestToSend.getBinaryFilePath() == null ? "" : requestToSend.getBinaryFilePath();
				httpResponse = httpExecutor.execute(
					method,
					templatedUrl,
					templatedHeaders,
					templatedBody,
					formData,
					binaryPath,
					PayloadTypes.resolveType(payloadType),
					requestPolicy.resolveTimeoutMillis(details, stateService.getDefaultTimeoutMillis())
				);
			} else {
				if (currentRequest != null) {
					templatedBody = currentRequest.getBody();
					templatedHeaders = currentRequest.getHeaders() == null ? List.of() : currentRequest.getHeaders();
					templatedParams = currentRequest.getParams() == null ? List.of() : currentRequest.getParams();
				}
				grpcResponse = grpcExecutor.execute(
					templatedGrpcTarget,
					templatedGrpcService,
					templatedGrpcMethod,
					templatedBody,
					templatedHeaders,
					requestPolicy.resolveTimeoutMillis(details, stateService.getDefaultTimeoutMillis())
				);
			}
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception error) {
			requestFailed = true;
			if (requestType == RequestType.HTTP) {
				logs.add("Request failed: " + error.getMessage());
			} else {
				logs.add("gRPC request failed: " + error.getMessage());
			}
		}

		if (requestFailed) {
			lines.add("Request failed. No response received.");
		} else if (requestType == RequestType.HTTP && httpResponse != null) {
			lines.add("Status: " + HttpStatusReasons.format(httpResponse.statusCode, ""));
			lines.addAll(stageFormatter.formatResponseSnapshot(httpResponse.body, httpResponse.headers));
		} else if (requestType == RequestType.GRPC && grpcResponse != null) {
			lines.add("Status: " + HttpStatusReasons.format(grpcResponse.statusCode, safe(grpcResponse.statusMessage)));
			lines.addAll(stageFormatter.formatResponseSnapshot(grpcResponse.body, grpcResponse.headers));
		} else {
			lines.add("No response received.");
		}

		long duration = System.nanoTime() - start;
		return new DebugStageResult("Response Received", duration, lines, true);
	}

	private DebugStageResult buildAfterStage() {
		long start = System.nanoTime();
		List<String> lines = new ArrayList<>();
		if (beforeFailed) {
			lines.add("After request skipped because before request failed.");
		} else if (requestFailed || (requestType == RequestType.HTTP && httpResponse == null) ||
			(requestType == RequestType.GRPC && grpcResponse == null)) {
			lines.add("After request skipped because request failed.");
		} else {
			int logStart = logs.size();
			String after = status == null ? "" : safe(status.afterScript);
			afterRequest = new ScriptRequest(templatedBody, StateCopyUtils.cloneHeaders(templatedHeaders), StateCopyUtils.cloneHeaders(templatedParams));
			if (currentRequest != null) {
				afterRequest.setFormData(StateCopyUtils.cloneFormData(currentRequest.getFormData()));
				afterRequest.setBinaryFilePath(currentRequest.getBinaryFilePath());
			}
			try {
				Object response = requestType == RequestType.HTTP ? httpResponse : grpcResponse;
				scriptRuntime.runScript(
					after,
					new ScriptContext(vars, logger, helpers, afterRequest, rawRequest, response, globalContext)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			globalContextRuntime.persist(globalContext);
			afterLogs = logs.size() == logStart ? List.of() : new ArrayList<>(logs.subList(logStart, logs.size()));
			lines.addAll(stageFormatter.formatLogs("After request logs", afterLogs));
		}
		long duration = System.nanoTime() - start;
		return new DebugStageResult("After Request Logs", duration, lines, true);
	}

	private DebugStageResult buildFinalStage() {
		long start = System.nanoTime();
		List<String> lines = new ArrayList<>();
		ScriptRequest finalRequest = afterRequest != null ? afterRequest : currentRequest;
		if (finalRequest == null) {
			finalRequest = new ScriptRequest("", List.of(), List.of());
		}
		lines.addAll(stageFormatter.formatRequestSnapshot(finalRequest));
		lines.addAll(stageFormatter.formatLogs("Logs after request", logs == null ? List.of() : logs));
		long duration = System.nanoTime() - start;
		return new DebugStageResult("Final State", duration, lines, false);
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
			inlineRunButton.setEnabled(!abandoned && vars != null && logger != null && helpers != null);
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

	private String safe(String value) {
		return value == null ? "" : value;
	}

}
