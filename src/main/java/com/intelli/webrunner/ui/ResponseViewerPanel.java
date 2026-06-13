package com.intelli.webrunner.ui;

import com.intelli.webrunner.execution.DownloadResult;
import com.intelli.webrunner.execution.ExecutionResult;
import com.intelli.webrunner.util.ContentDispositionUtils;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
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
	private final EditorTextField responseBodyArea;
	private final EditorTextField responseHeadersArea;
	private final JBTextArea responseLogsArea = new JBTextArea();
	private final JPanel root = new JPanel(new BorderLayout());

	public ResponseViewerPanel(Project project, Runnable onResponsePersisted) {
		this.project = project;
		this.onResponsePersisted = onResponsePersisted;
		this.responseBodyArea = new EditorTextField("", project, JsonFileType.INSTANCE);
		this.responseHeadersArea = new EditorTextField("", project, JsonFileType.INSTANCE);
		this.responseBodyArea.setOneLineMode(false);
		this.responseHeadersArea.setOneLineMode(false);

		responseTabs.add("Response Body", new JBScrollPane(responseBodyArea));
		responseTabs.add("Response Headers", new JBScrollPane(responseHeadersArea));
		responseTabs.add("Logs", new JBScrollPane(responseLogsArea));

		responseStatusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		root.add(responseStatusLabel, BorderLayout.NORTH);
		root.add(responseTabs, BorderLayout.CENTER);
	}

	public JComponent getComponent() {
		return root;
	}

	public JTabbedPane getTabs() {
		return responseTabs;
	}

	public EditorTextField getBodyField() {
		return responseBodyArea;
	}

	public EditorTextField getHeadersField() {
		return responseHeadersArea;
	}

	public String getResponseBody() {
		return responseBodyArea.getText();
	}

	public String getResponseHeaders() {
		return responseHeadersArea.getText();
	}

	public String getLogs() {
		return responseLogsArea.getText();
	}

	/** Loads persisted response content and clears the status label. */
	public void setContent(String body, String headers, String logs) {
		responseBodyArea.setText(body == null ? "" : body);
		responseHeadersArea.setText(headers == null ? "" : headers);
		responseLogsArea.setText(logs == null ? "" : logs);
		responseStatusLabel.setText("");
	}

	public void showLog(String message) {
		responseLogsArea.setText(message);
	}

	public void appendLog(String message) {
		String existing = responseLogsArea.getText();
		if (existing == null || existing.isBlank()) {
			responseLogsArea.setText(message);
			return;
		}
		responseLogsArea.setText(existing + "\n" + message);
	}

	/** Applies an execution result to the UI on the EDT, then notifies the host to persist it. */
	public void updateResponse(ExecutionResult result, boolean isGrpc) {
		invokeLater(() -> {
			responseBodyArea.setText(result.responseBody);
			responseHeadersArea.setText(result.responseHeaders);
			responseLogsArea.setText(result.logs);
			responseStatusLabel.setForeground(result.statusCode >= 400 ? JBColor.RED : JBColor.GREEN);
			if (isGrpc) {
				responseStatusLabel.setText("Status: " + result.statusCode + " " + result.statusMessage);
			} else {
				responseStatusLabel.setText("Status: " + result.statusCode);
			}
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
		int dialogResult = chooser.showSaveDialog(parent);
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
		JDialog dialog = new JDialog();
		dialog.setTitle(title);
		JTabbedPane tabs = new JTabbedPane();

		EditorTextField bodyField =
			new EditorTextField(responseBodyArea.getDocument(), project, JsonFileType.INSTANCE, false, false);
		bodyField.setOneLineMode(false);
		EditorTextField headersField =
			new EditorTextField(responseHeadersArea.getDocument(), project, JsonFileType.INSTANCE, false, false);
		headersField.setOneLineMode(false);
		JBTextArea logsArea = new JBTextArea();
		logsArea.setDocument(responseLogsArea.getDocument());

		tabs.add("Response Body", new JBScrollPane(bodyField));
		tabs.add("Response Headers", new JBScrollPane(headersField));
		tabs.add("Logs", new JBScrollPane(logsArea));

		dialog.getContentPane().add(tabs);
		dialog.setSize(900, 700);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private void invokeLater(Runnable runnable) {
		ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
	}
}
