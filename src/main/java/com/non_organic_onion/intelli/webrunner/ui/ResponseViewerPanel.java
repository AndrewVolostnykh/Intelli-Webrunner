package com.non_organic_onion.intelli.webrunner.ui;

import com.non_organic_onion.webrunner.core.execution.DownloadResult;
import com.non_organic_onion.webrunner.core.execution.ExecutionResult;
import com.non_organic_onion.webrunner.core.execution.ResponseResultService;
import com.non_organic_onion.webrunner.core.util.ContentDispositionUtils;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.nio.file.Files;

/**
 * Read-only view of a request's response: body, headers and logs tabs plus a status label.
 * Owns the response editor documents; the host reads/writes them through the accessors here.
 */
public final class ResponseViewerPanel {

	private final Project project;
	private final Runnable onResponsePersisted;

	private final JTabbedPane responseTabs = new JTabbedPane();
	private final JBLabel responseStatusLabel = new JBLabel("");
	private final JBLabel responseTimeLabel = new JBLabel("");
	private final EditorTextField responseBodyArea;
	private final EditorTextField responseHeadersArea;
	private final EditorTextField responseCookiesArea;
	private final JComponent responseCookiesComponent;
	private final LogViewerPanel responseLogsArea;
	private final JPanel root = new JPanel(new BorderLayout());
	private String responseLogsText = "";
	private boolean responseCookiesVisible = true;
	private Timer elapsedTimer;
	private long elapsedStartedAtMillis;

	public ResponseViewerPanel(Project project, Runnable onResponsePersisted) {
		this.project = project;
		this.onResponsePersisted = onResponsePersisted;
		this.responseBodyArea = EditorThemeSupport.configure(new EditorTextField("", project, JsonFileType.INSTANCE));
		this.responseHeadersArea = EditorThemeSupport.configure(new EditorTextField("", project, JsonFileType.INSTANCE));
		this.responseCookiesArea = EditorThemeSupport.configure(new EditorTextField("", project, JsonFileType.INSTANCE));
		this.responseLogsArea = new LogViewerPanel();
		this.responseBodyArea.setOneLineMode(false);
		this.responseHeadersArea.setOneLineMode(false);
		this.responseCookiesArea.setOneLineMode(false);
		this.responseCookiesComponent = new JBScrollPane(responseCookiesArea);

		responseTabs.add("Response", new JBScrollPane(responseBodyArea));
		responseTabs.add("Response Headers", new JBScrollPane(responseHeadersArea));
		responseTabs.add("Cookies", responseCookiesComponent);
		responseTabs.add("Logs", responseLogsArea.getComponent());

		responseStatusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		responseTimeLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		JPanel statusPanel = new JPanel(new BorderLayout());
		statusPanel.add(responseStatusLabel, BorderLayout.WEST);
		statusPanel.add(responseTimeLabel, BorderLayout.EAST);
		root.add(statusPanel, BorderLayout.NORTH);
		root.add(responseTabs, BorderLayout.CENTER);
	}

	public JComponent getComponent() {
		return root;
	}

	public JTabbedPane getTabs() {
		return responseTabs;
	}

	public void setCookiesVisible(boolean visible) {
		if (responseCookiesVisible == visible) {
			return;
		}
		responseCookiesVisible = visible;
		int cookiesIndex = responseTabs.indexOfComponent(responseCookiesComponent);
		if (visible) {
			if (cookiesIndex < 0) {
				responseTabs.insertTab("Cookies", null, responseCookiesComponent, null, Math.min(2, responseTabs.getTabCount()));
			}
			return;
		}
		if (cookiesIndex >= 0) {
			responseTabs.removeTabAt(cookiesIndex);
		}
	}

	public EditorTextField getBodyField() {
		return responseBodyArea;
	}

	public EditorTextField getHeadersField() {
		return responseHeadersArea;
	}

	public EditorTextField getCookiesField() {
		return responseCookiesArea;
	}

	public String getResponseBody() {
		return responseBodyArea.getText();
	}

	public String getResponseHeaders() {
		return responseHeadersArea.getText();
	}

	public String getResponseCookies() {
		return responseCookiesArea.getText();
	}

	public String getLogs() {
		return responseLogsText;
	}

	/** Loads persisted response content and clears the status label. */
	public void setContent(String body, String headers, String cookies, String logs) {
		responseBodyArea.setText(body == null ? "" : body);
		responseHeadersArea.setText(headers == null ? "" : headers);
		responseCookiesArea.setText(cookies == null ? "" : cookies);
		setLogs(logs);
		responseStatusLabel.setText("");
		responseTimeLabel.setText("");
	}

	public void showLog(String message) {
		setLogs(message);
	}

	public void appendLog(String message) {
		String existing = responseLogsText;
		if (existing == null || existing.isBlank()) {
			setLogs(message);
			return;
		}
		setLogs(existing + "\n" + message);
	}

	public void clearStatus() {
		invokeLater(() -> {
			responseStatusLabel.setText("");
			responseTimeLabel.setText("");
		});
	}

	public void startElapsedTimer() {
		invokeLater(() -> {
			stopElapsedTimer();
			elapsedStartedAtMillis = System.currentTimeMillis();
			responseStatusLabel.setText("0 ms");
			responseTimeLabel.setText("");
			elapsedTimer = new Timer(100, e -> {
				long elapsed = Math.max(0, System.currentTimeMillis() - elapsedStartedAtMillis);
				responseStatusLabel.setText(ResponseResultService.formatDuration(elapsed));
			});
			elapsedTimer.start();
		});
	}

	public void stopElapsedTimer() {
		if (elapsedTimer != null) {
			elapsedTimer.stop();
			elapsedTimer = null;
		}
	}

	/** Applies an execution result to the UI on the EDT, then notifies the host to persist it. */
	public void updateResponse(ExecutionResult result, boolean isGrpc) {
		invokeLater(() -> {
			stopElapsedTimer();
			responseBodyArea.setText(result.responseBody);
			responseHeadersArea.setText(result.responseHeaders);
			responseCookiesArea.setText(result.responseCookies);
			setLogs(result.logs);
			responseStatusLabel.setForeground(result.statusCode >= 400 ? JBColor.RED : JBColor.GREEN);
			responseTimeLabel.setText("");
			responseStatusLabel.setText(ResponseResultService.resultMetadata(result));
			if (onResponsePersisted != null) {
				onResponsePersisted.run();
			}
		});
	}

	public void promptSaveDownload(DownloadResult result, Component parent) {
		if (result == null || result.bodyBytes == null) {
			return;
		}
		String suggestedName = ContentDispositionUtils.suggestDownloadFilename(result.headers);
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save Response");
		if (suggestedName != null && !suggestedName.isBlank()) {
			chooser.setSelectedFile(new File(suggestedName));
		}
		int dialogResult = TaskbarWindowSupport.showSaveDialog(chooser, parent);
		if (dialogResult != JFileChooser.APPROVE_OPTION) {
			appendLog("Download canceled.");
			return;
		}
		File file = chooser.getSelectedFile();
		if (file == null) {
			appendLog("Download canceled.");
			return;
		}
		try {
			Files.write(file.toPath(), result.bodyBytes);
			appendLog("Saved response to: " + file.getAbsolutePath());
		} catch (Exception error) {
			appendLog("Failed to save response: " + error.getMessage());
		}
	}

	public void openInWindow(String title, Component parent) {
		JTabbedPane tabs = new JTabbedPane();

		EditorTextField bodyField =
			EditorThemeSupport.configure(
				new EditorTextField(responseBodyArea.getDocument(), project, JsonFileType.INSTANCE, false, false)
			);
		bodyField.setOneLineMode(false);
		EditorTextField headersField =
			EditorThemeSupport.configure(
				new EditorTextField(responseHeadersArea.getDocument(), project, JsonFileType.INSTANCE, false, false)
			);
		headersField.setOneLineMode(false);
		EditorTextField cookiesField =
			EditorThemeSupport.configure(
				new EditorTextField(responseCookiesArea.getDocument(), project, JsonFileType.INSTANCE, false, false)
			);
		cookiesField.setOneLineMode(false);
		LogViewerPanel logsArea = new LogViewerPanel();
		printLogs(logsArea, responseLogsText);

		tabs.add("Response", new JBScrollPane(bodyField));
		tabs.add("Response Headers", new JBScrollPane(headersField));
		if (responseCookiesVisible) {
			tabs.add("Cookies", new JBScrollPane(cookiesField));
		}
		tabs.add("Logs", logsArea.getComponent());

		TaskbarWindowSupport.showFrame(title, tabs, parent, 900, 700);
	}

	private void invokeLater(Runnable runnable) {
		ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
	}

	private void setLogs(String logs) {
		responseLogsText = logs == null ? "" : logs;
		invokeLater(() -> printLogs(responseLogsArea, responseLogsText));
	}

	private void printLogs(
		LogViewerPanel logsPanel,
		String logs
	) {
		logsPanel.setLogs(
			logs,
			line -> line != null && line.contains("Assertion failed") ? JBColor.RED : JBColor.foreground(),
			line -> line
		);
	}

}
