package com.intelli.webrunner.ui;

import com.intellij.ui.components.JBScrollPane;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JTabbedPane;
import java.awt.Component;

/**
 * Non-modal, read-only help dialog focused on the scripting API.
 */
public final class WebrunnerInfoDialog {

	private WebrunnerInfoDialog() {
	}

	public static void show(Component parent) {
		JDialog dialog = new JDialog();
		dialog.setTitle("Webrunner Info");
		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Scripting", buildScriptingTabs());
		dialog.getContentPane().add(tabs);
		dialog.setSize(900, 650);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private static JComponent buildScriptingTabs() {
		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Funcs", createHtmlTab(funcsHtml()));
		tabs.add("Context", createHtmlTab(contextHtml()));
		tabs.add("Assertions", createHtmlTab(assertionsHtml()));
		return tabs;
	}

	private static JComponent createHtmlTab(String html) {
		JEditorPane pane = new JEditorPane("text/html", page(html));
		pane.setEditable(false);
		pane.setOpaque(false);
		return new JBScrollPane(pane);
	}

	private static String page(String body) {
		return """
			<html>
			<head>
			  <style>
			    body { font-family: sans-serif; font-size: 12px; margin: 10px; }
			    .item { margin: 0 0 11px 0; }
			    .fn { font-family: monospace; font-weight: bold; color: #1f6feb; }
			    .obj { font-family: monospace; font-weight: bold; color: #8957e5; }
			    .desc { margin-top: 2px; color: #ffffff; }
			    .section { font-weight: bold; margin: 14px 0 8px 0; }
			  </style>
			</head>
			<body>
			""" + body + """
			</body>
			</html>
			""";
	}

	private static String funcsHtml() {
		return """
			<div class='item'><span class='fn'>log(...args)</span><div class='desc'>Writes values to the Logs tab. Objects and arrays are serialized as JSON.</div></div>
			<div class='item'><span class='fn'>logAndReturn(value)</span><div class='desc'>Writes value to Logs and returns the same value.</div></div>
			<div class='item'><span class='fn'>logAndReturn(message, value)</span><div class='desc'>Writes message and value to Logs, then returns only value.</div></div>
			<div class='item'><span class='fn'>uuid()</span><div class='desc'>Returns a random UUID string.</div></div>
			<div class='item'><span class='fn'>stringify(value)</span><div class='desc'>Converts a JavaScript value to JSON text. Strings are returned unchanged.</div></div>
			<div class='item'><span class='fn'>jsonify(value)</span><div class='desc'>Parses JSON text into a JavaScript object. Existing objects are returned as-is.</div></div>
			<div class='item'><span class='fn'>randomString(size)</span><div class='desc'>Returns an alphanumeric random string with the requested size.</div></div>
			<div class='item'><span class='fn'>randomEmail()</span><div class='desc'>Returns a random email-like string.</div></div>
			<div class='item'><span class='fn'>randomNumber(from, to)</span><div class='desc'>Returns a random integer in the inclusive range.</div></div>
			<div class='item'><span class='fn'>randomDouble(from, to)</span><div class='desc'>Returns a decimal string with 10 digits after the decimal point.</div></div>
			<div class='item'><span class='fn'>randomDouble(from, to, afterComma)</span><div class='desc'>Returns a decimal string with exactly afterComma digits after the decimal point.</div></div>
			<div class='item'><span class='fn'>randomIsoDate()</span><div class='desc'>Returns a random UTC ISO 8601 offset date-time.</div></div>
			<div class='item'><span class='fn'>randomRfcDate()</span><div class='desc'>Returns a random UTC RFC 1123 date-time.</div></div>
			<div class='item'><span class='fn'>randomDateTime()</span><div class='desc'>Returns a random UTC date-time in yyyy-MM-dd HH:mm:ss.SSS format.</div></div>
			<div class='item'><span class='fn'>randomDate()</span><div class='desc'>Returns a random UTC date in yyyy-MM-dd format.</div></div>
			<div class='item'><span class='fn'>randomTime()</span><div class='desc'>Returns a random UTC time in HH:mm:ss.SSS format.</div></div>
			<div class='item'><span class='fn'>randomMillilsDate()</span><div class='desc'>Returns random epoch milliseconds. The name is kept as implemented.</div></div>
			<div class='item'><span class='fn'>randomEpochSecondsDate()</span><div class='desc'>Returns random epoch seconds.</div></div>
			<div class='item'><span class='fn'>currentIsoDate()</span><div class='desc'>Returns the current UTC ISO 8601 offset date-time.</div></div>
			<div class='item'><span class='fn'>currentRfcDate()</span><div class='desc'>Returns the current UTC RFC 1123 date-time.</div></div>
			<div class='item'><span class='fn'>currentDateTime()</span><div class='desc'>Returns the current UTC date-time in yyyy-MM-dd HH:mm:ss.SSS format.</div></div>
			<div class='item'><span class='fn'>currentDate()</span><div class='desc'>Returns the current UTC date in yyyy-MM-dd format.</div></div>
			<div class='item'><span class='fn'>currentTime()</span><div class='desc'>Returns the current UTC time in HH:mm:ss.SSS format.</div></div>
			<div class='item'><span class='fn'>currentMillilsDate()</span><div class='desc'>Returns current epoch milliseconds. The name is kept as implemented.</div></div>
			<div class='item'><span class='fn'>currentEpochSecondsDate()</span><div class='desc'>Returns current epoch seconds.</div></div>
			""";
	}

	private static String contextHtml() {
		return """
			<div class='section'>Global objects</div>
			<div class='item'><span class='obj'>vars</span><div class='desc'>Request or chain variable store. Values from vars have priority over globalContext during placeholder resolution.</div></div>
			<div class='item'><span class='fn'>vars.set(name, value)</span><div class='desc'>Stores a value.</div></div>
			<div class='item'><span class='fn'>vars.add(name, value)</span><div class='desc'>Alias for set.</div></div>
			<div class='item'><span class='fn'>vars.get(name)</span><div class='desc'>Reads a value.</div></div>
			<div class='item'><span class='fn'>vars.all()</span><div class='desc'>Returns all stored values.</div></div>
			<div class='item'><span class='obj'>globalContext</span><div class='desc'>Project-level shared variable store with the same API as vars.</div></div>
			<div class='item'><span class='obj'>request</span><div class='desc'>Mutable outgoing request. Available in Before Request and After Request.</div></div>
			<div class='item'><span class='obj'>rawRequest</span><div class='desc'>Original saved request snapshot before script mutations.</div></div>
			<div class='item'><span class='obj'>response</span><div class='desc'>Response object available in After Request. HTTP exposes statusCode, headers, body. gRPC also exposes statusMessage.</div></div>
			<div class='item'><span class='obj'>context</span><div class='desc'>Grouped access to vars, globalContext, request, rawRequest, response, log, logAndReturn, stringify, jsonify, and helper functions.</div></div>

			<div class='section'>Request fields</div>
			<div class='item'><span class='obj'>request.body</span><div class='desc'>Request body. JSON bodies are exposed as objects; non-JSON bodies are strings.</div></div>
			<div class='item'><span class='obj'>request.headers</span><div class='desc'>Array of { name, value, enabled } objects.</div></div>
			<div class='item'><span class='obj'>request.params</span><div class='desc'>Array of { name, value, enabled } query parameter objects.</div></div>
			<div class='item'><span class='obj'>request.formData</span><div class='desc'>Array of { name, value, enabled, file } form-data objects.</div></div>
			<div class='item'><span class='obj'>request.binaryFilePath</span><div class='desc'>Binary body file path.</div></div>
			""";
	}

	private static String assertionsHtml() {
		return """
			<div class='item'><span class='fn'>assert(actual, expected, message)</span><div class='desc'>Checks actual against expected. On mismatch it writes an assertion failure to Logs. It does not throw, so script execution continues.</div></div>
			<div class='item'><span class='fn'>assert(actual, null, message)</span><div class='desc'>When expected is null, the assertion passes if actual is truthy and not false.</div></div>
			<div class='section'>Examples</div>
			<div class='item'><span class='fn'>assert(response.statusCode, 200, "Expected HTTP 200")</span><div class='desc'>Logs a failure if the response status is not 200.</div></div>
			<div class='item'><span class='fn'>assert(response.body.ok, true, "Expected ok=true")</span><div class='desc'>Checks a parsed JSON response field.</div></div>
			<div class='item'><span class='fn'>assert(vars.get("token"), null, "Token must exist")</span><div class='desc'>Checks that a value exists and is truthy.</div></div>
			""";
	}
}
