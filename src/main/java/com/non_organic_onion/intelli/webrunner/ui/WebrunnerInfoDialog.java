package com.non_organic_onion.intelli.webrunner.ui;

import com.intellij.ui.components.JBScrollPane;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
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
		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(buildScriptingBrowser(), BorderLayout.CENTER);
		dialog.setSize(1100, 760);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private static JComponent buildScriptingBrowser() {
		DefaultMutableTreeNode root = buildDocsTree();
		JTree tree = new JTree(root);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		for (int row = 0; row < tree.getRowCount(); row++) {
			tree.expandRow(row);
		}

		JEditorPane details = new JEditorPane("text/html", page(introHtml()));
		details.setEditable(false);
		details.setOpaque(false);

		tree.addTreeSelectionListener((TreeSelectionEvent event) -> {
			DefaultMutableTreeNode node = selectedNode(event.getPath());
			Object value = node == null ? null : node.getUserObject();
			if (value instanceof DocNode doc) {
				details.setText(page(doc.html));
				details.setCaretPosition(0);
			}
		});
		tree.setSelectionRow(0);

		JSplitPane splitPane = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			new JBScrollPane(tree),
			new JBScrollPane(details)
		);
		splitPane.setResizeWeight(0.28);
		SplitPaneStyling.applyThinBlackDivider(splitPane);
		return splitPane;
	}

	private static DefaultMutableTreeNode selectedNode(TreePath path) {
		if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)) {
			return null;
		}
		return node;
	}

	private static DefaultMutableTreeNode buildDocsTree() {
		DefaultMutableTreeNode root = node("Scripting", introHtml());

		DefaultMutableTreeNode basics = category(
			"Basics",
			"Script Basics",
			"""
			<p>Scripts run in Rhino JavaScript. Before Request scripts can mutate <code>request</code>
			before templating and transport. After Request scripts can read <code>response</code> and can
			still mutate variable stores.</p>
			<p>Most globals are also available through <code>context</code>, for example
			<code>context.vars</code>, <code>context.log</code>, and <code>context.stringify</code>.</p>
			"""
		);
		basics.add(node("Execution Order", """
			<p>Normal execution order is: Global Context script, Before Request script, placeholder
			resolution, transport, After Request script.</p>
			<ul>
			  <li>Request variables override Chain Context variables.</li>
			  <li>Chain Context variables override Global Context variables.</li>
			  <li>Missing placeholders in JSON bare values become <code>null</code>.</li>
			</ul>
			<pre>vars.set('token', 'request-token');
chainContext.set('token', 'chain-token');
globalContext.set('token', 'global-token');
// {{token}} resolves to request-token</pre>
			<pre>// Before Request
request.headers = [{ name: 'Authorization', value: 'Bearer ' + vars.get('token'), enabled: true }];</pre>
			"""));
		basics.add(node("context", """
			<p>Grouped access to the same scripting API from one object. It includes variable stores,
			request objects, response objects, logging helpers, JSON helpers, chain helpers, and random/date helpers.</p>
			<pre>context.log('token', context.vars.get('token'));</pre>
			<pre>context.request.body.requestId = context.uuid();</pre>
			<pre>var previous = context.getRequest('Login');</pre>
			"""));
		basics.add(node("context.helpers", """
			<p>Java helper object exposed for advanced use. Prefer the global functions for everyday scripts;
			they call the same helper implementation.</p>
			<pre>var id = context.helpers.uuid();</pre>
			<pre>var email = context.helpers.randomEmail();</pre>
			<pre>context.helpers.assertValue(response.statusCode, 200, 'Expected 200');</pre>
			"""));
		root.add(basics);

		DefaultMutableTreeNode logging = category("Logging", "Logging Functions", """
			<p>Use these functions to write values to Logs. Objects and arrays are serialized as JSON.</p>
			""");
		logging.add(node("log(...args)", """
			<p>Writes all arguments to Logs, separated by spaces.</p>
			<pre>log('status', response.statusCode);</pre>
			<pre>log('body', response.body);</pre>
			<pre>log('headers', response.headers);</pre>
			"""));
		logging.add(node("logAndReturn(value)", """
			<p>Logs a value and returns the same value. Useful inside expressions.</p>
			<pre>vars.set('token', logAndReturn(response.body.token));</pre>
			<pre>request.headers = [{ name: 'X-Debug', value: logAndReturn('enabled'), enabled: true }];</pre>
			<pre>log('created id', logAndReturn(response.body.id));</pre>
			"""));
		logging.add(node("logAndReturn(message, value)", """
			<p>Logs a label and a value, then returns only the value.</p>
			<pre>vars.set('userId', logAndReturn('User id:', response.body.id));</pre>
			<pre>request.body.token = logAndReturn('Token:', vars.get('token'));</pre>
			<pre>logAndReturn('Full response:', response.body);</pre>
			"""));
		root.add(logging);

		DefaultMutableTreeNode variables = category("Variables", "Variable Stores", """
			<p><code>vars</code>, <code>globalContext</code>, and <code>chainContext</code> have the same API:
			<code>set</code>, <code>add</code>, <code>get</code>, and <code>all</code>.</p>
			""");
		variables.add(node("vars", """
			<p>Request or chain-run variable store. It has the highest priority for placeholders.</p>
			<pre>vars.set('token', response.body.token);</pre>
			<pre>vars.add('page', 2); // add is an alias for set</pre>
			<pre>request.params = [{ name: 'page', value: vars.get('page'), enabled: true }];</pre>
			"""));
		variables.add(node("globalContext", """
			<p>Project-level variable store configured from Global Context. It is available in request,
			chain, and Global Context scripts.</p>
			<pre>globalContext.set('baseUrl', 'https://api.example.com');</pre>
			<pre>request.headers = [{ name: 'X-Env', value: globalContext.get('env'), enabled: true }];</pre>
			<pre>log('global vars', globalContext.all());</pre>
			"""));
		variables.add(node("chainContext", """
			<p>Chain-level variable store. It is available while running chain steps and has priority over
			Global Context but lower priority than <code>vars</code>.</p>
			<pre>chainContext.set('tenantId', response.body.tenantId);</pre>
			<pre>request.body.tenantId = chainContext.get('tenantId');</pre>
			<pre>log('chain context', chainContext.all());</pre>
			"""));
		variables.add(node("set(name, value)", """
			<p>Stores or overwrites a value in the selected store.</p>
			<pre>vars.set('id', response.body.id);</pre>
			<pre>globalContext.set('authToken', response.body.token);</pre>
			<pre>chainContext.set('stepName', 'create-user');</pre>
			"""));
		variables.add(node("add(name, value)", """
			<p>Alias for <code>set</code>.</p>
			<pre>vars.add('email', randomEmail());</pre>
			<pre>globalContext.add('lastLoginAt', currentIsoDate());</pre>
			<pre>chainContext.add('attempt', 1);</pre>
			"""));
		variables.add(node("get(name)", """
			<p>Reads a value. Missing values resolve to JavaScript <code>null</code>.</p>
			<pre>log(vars.get('token'));</pre>
			<pre>request.headers = [{ name: 'Authorization', value: 'Bearer ' + globalContext.get('token'), enabled: true }];</pre>
			<pre>request.body.tenantId = chainContext.get('tenantId');</pre>
			"""));
		variables.add(node("all()", """
			<p>Returns all values as an object.</p>
			<pre>log(vars.all());</pre>
			<pre>log('global', globalContext.all());</pre>
			<pre>var snapshot = stringify(chainContext.all());</pre>
			"""));
		root.add(variables);

		DefaultMutableTreeNode requestObjects = category("Request Objects", "Request Objects", """
			<p><code>request</code> is mutable. <code>rawRequest</code> is the original saved request snapshot
			before script changes.</p>
			""");
		requestObjects.add(node("request.body", """
			<p>JSON bodies are exposed as objects. Non-JSON bodies are strings.</p>
			<pre>request.body.name = 'updated';</pre>
			<pre>request.body.createdAt = currentIsoDate();</pre>
			<pre>request.body = stringify({ name: 'manual', id: uuid() });</pre>
			"""));
		requestObjects.add(node("request.headers", """
			<p>Array of <code>{ name, value, enabled }</code> objects.</p>
			<pre>request.headers = [{ name: 'Authorization', value: 'Bearer ' + vars.get('token'), enabled: true }];</pre>
			<pre>request.headers.push({ name: 'X-Request-Id', value: uuid(), enabled: true });</pre>
			<pre>request.headers[0].enabled = false;</pre>
			"""));
		requestObjects.add(node("request.params", """
			<p>Array of query parameter objects.</p>
			<pre>request.params = [{ name: 'q', value: 'search', enabled: true }];</pre>
			<pre>request.params.push({ name: 'page', value: 2, enabled: true });</pre>
			<pre>request.params = request.params.filter(function(p) { return p.name !== 'debug'; });</pre>
			"""));
		requestObjects.add(node("request.formData", """
			<p>Array of <code>{ name, value, enabled, file }</code> form-data objects.</p>
			<pre>request.formData = [{ name: 'name', value: 'Andrew', enabled: true, file: false }];</pre>
			<pre>request.formData.push({ name: 'avatar', value: 'C:/tmp/avatar.png', enabled: true, file: true });</pre>
			<pre>request.formData[0].value = vars.get('displayName');</pre>
			"""));
		requestObjects.add(node("request.binaryFilePath", """
			<p>Path used by binary payload requests.</p>
			<pre>request.binaryFilePath = 'C:/tmp/body.bin';</pre>
			<pre>request.binaryFilePath = vars.get('downloadPath');</pre>
			<pre>log('binary file', request.binaryFilePath);</pre>
			"""));
		requestObjects.add(node("rawRequest", """
			<p>Original request snapshot. Use it to compare saved input with the mutated outgoing request.</p>
			<pre>log('original body', rawRequest.body);</pre>
			<pre>log('sent name', request.body.name, 'original', rawRequest.body.name);</pre>
			<pre>assert(rawRequest.headers.length > 0, null, 'Headers should be configured');</pre>
			"""));
		root.add(requestObjects);

		DefaultMutableTreeNode responseObjects = category("Response Objects", "Response Objects", """
			<p><code>response</code> is available in After Request scripts. If the response body is JSON,
			<code>response.body</code> is exposed as an object.</p>
			""");
		responseObjects.add(node("response.statusCode", """
			<p>HTTP status code or gRPC numeric status code.</p>
			<pre>assert(response.statusCode, 200, 'Expected success');</pre>
			<pre>if (response.statusCode === 401) interruptChain('Unauthorized');</pre>
			<pre>log('status', response.statusCode);</pre>
			"""));
		responseObjects.add(node("response.statusMessage", """
			<p>gRPC status message. HTTP responses may not expose this field in scripts.</p>
			<pre>log('grpc status', response.statusMessage);</pre>
			<pre>assert(response.statusMessage, 'OK', 'Expected gRPC OK');</pre>
			<pre>if (response.statusMessage === 'PERMISSION_DENIED') skip('No access');</pre>
			"""));
		responseObjects.add(node("response.headers", """
			<p>Map of response header names to arrays of values.</p>
			<pre>log(response.headers);</pre>
			<pre>var contentType = response.headers['content-type'][0];</pre>
			<pre>assert(response.headers['set-cookie'], null, 'Expected cookies');</pre>
			"""));
		responseObjects.add(node("response.body", """
			<p>Parsed JSON object for JSON responses, or text for non-JSON responses.</p>
			<pre>vars.set('token', response.body.token);</pre>
			<pre>assert(response.body.ok, true, 'Expected ok=true');</pre>
			<pre>log('raw text or object', response.body);</pre>
			"""));
		responseObjects.add(node("response.value", """
			<p>Fallback wrapper for response types that are not HTTP or gRPC, such as Kafka send metadata.</p>
			<pre>log(response.value);</pre>
			<pre>vars.set('kafkaOffset', response.value.offset);</pre>
			<pre>assert(response.value.topic, 'events', 'Expected Kafka topic');</pre>
			"""));
		root.add(responseObjects);

		DefaultMutableTreeNode chain = category("Chain", "Chain Functions", """
			<p>These functions are intended for Chain Before Request and After Request scripts.</p>
			""");
		chain.add(node("getRequest(nameOrId)", """
			<p>Returns a snapshot for a previously executed chain step by request name or id. If a name is
			duplicated, the latest executed step with that name wins.</p>
			<pre>var login = getRequest('Login');
request.headers = [{ name: 'Authorization', value: 'Bearer ' + login.response.body.token, enabled: true }];</pre>
			<pre>var created = context.getRequest('Create User');
assert(created.response.statusCode, 201, 'User should be created');</pre>
			<pre>var previous = getRequest('request-id');
log(previous.rawRequest, previous.sentRequest, previous.response);</pre>
			"""));
		chain.add(node("skip(...values)", """
			<p>Marks the current step as <code>Skiped</code>. If called before transport, the request is not
			sent and the chain continues. If called after transport, the response is kept and the chain
			continues.</p>
			<pre>if (!chainContext.get('runPayments')) skip('Payments disabled');</pre>
			<pre>if (response.statusCode === 404) skip('Optional resource missing', response.body);</pre>
			<pre>context.skip('Skipping tenant', chainContext.get('tenantId'));</pre>
			"""));
		chain.add(node("interruptChain(...values)", """
			<p>Marks the current step as <code>Interrupted</code> and stops the whole chain.</p>
			<pre>if (response.statusCode >= 500) interruptChain('Server failed', response.statusCode);</pre>
			<pre>if (!vars.get('token')) context.interruptChain('Token was not created');</pre>
			<pre>interruptChain('Fatal validation error', response.body);</pre>
			"""));
		chain.add(node("interrupt(...values)", """
			<p>Alias for <code>interruptChain(...values)</code>.</p>
			<pre>if (response.statusCode === 401) interrupt('Unauthorized');</pre>
			<pre>if (response.body.blocked) interrupt('Blocked user', response.body.id);</pre>
			<pre>context.interrupt('Chain cannot continue');</pre>
			"""));
		chain.add(node("interrupChain(...values)", """
			<p>Backward-compatible alias for <code>interruptChain(...values)</code>.</p>
			<pre>interrupChain('Legacy spelling still works');</pre>
			<pre>context.interrupChain('Stop from context alias');</pre>
			<pre>if (!response.body.ok) interrupChain('Not ok', response.body);</pre>
			"""));
		chain.add(node("proceed()", """
			<p>Clears a previous <code>skip</code> or <code>interrupt</code> decision in the same script.</p>
			<pre>skip('default skip');
if (response.body.forceRun) proceed();</pre>
			<pre>interruptChain('missing approval');
if (chainContext.get('override')) proceed();</pre>
			<pre>context.proceed();</pre>
			"""));
		root.add(chain);

		DefaultMutableTreeNode assertions = category("Assertions", "Assertions", """
			<p>Assertions write failures to Logs and do not throw. Script execution continues.</p>
			""");
		assertions.add(node("assert(actual, expected, message)", """
			<p>Checks exact equality. JSON-like objects and arrays are compared structurally and mismatch
			paths are logged.</p>
			<pre>assert(response.statusCode, 200, 'Expected HTTP 200');</pre>
			<pre>assert(response.body, { ok: true }, 'Expected response shape');</pre>
			<pre>assert(response.body.items, [{ id: 1 }], 'Expected first item');</pre>
			"""));
		assertions.add(node("assert(actual, null, message)", """
			<p>Truthiness check. Passes when <code>actual</code> exists and is not false.</p>
			<pre>assert(vars.get('token'), null, 'Token must exist');</pre>
			<pre>assert(response.body.id, null, 'Response id is required');</pre>
			<pre>assert(response.headers['content-type'], null, 'Content-Type is required');</pre>
			"""));
		root.add(assertions);

		DefaultMutableTreeNode json = category("JSON", "JSON Helpers", """
			<p>Use these helpers when converting between JavaScript objects and JSON text.</p>
			""");
		json.add(node("stringify(value)", """
			<p>Converts a JavaScript value to JSON text. Strings are returned unchanged.</p>
			<pre>request.body = stringify({ name: 'test', id: uuid() });</pre>
			<pre>log(stringify(response.body));</pre>
			<pre>vars.set('payload', stringify(request.body));</pre>
			"""));
		json.add(node("jsonify(value)", """
			<p>Parses JSON text into a JavaScript object. Existing objects are returned as-is.</p>
			<pre>var body = jsonify('{ "ok": true }');
log(body.ok);</pre>
			<pre>request.body = jsonify(vars.get('payload'));</pre>
			<pre>var parsed = context.jsonify(response.body);</pre>
			"""));
		root.add(json);

		DefaultMutableTreeNode random = category("Random", "Random Data Functions", """
			<p>Random helpers are available as globals and through <code>context</code>.</p>
			""");
		random.add(node("uuid()", """
			<p>Returns a random UUID string.</p>
			<pre>request.headers.push({ name: 'X-Request-Id', value: uuid(), enabled: true });</pre>
			<pre>request.body.id = uuid();</pre>
			<pre>vars.set('correlationId', context.uuid());</pre>
			"""));
		random.add(node("randomString(size)", """
			<p>Returns an alphanumeric random string with the requested size.</p>
			<pre>request.body.username = 'user-' + randomString(8);</pre>
			<pre>vars.set('password', randomString(16));</pre>
			<pre>log('short id', randomString(6));</pre>
			"""));
		random.add(node("randomEmail()", """
			<p>Returns a random email-like string.</p>
			<pre>request.body.email = randomEmail();</pre>
			<pre>vars.set('email', randomEmail());</pre>
			<pre>log('new email', context.randomEmail());</pre>
			"""));
		random.add(node("randomNumber(from, to)", """
			<p>Returns a random integer in the inclusive range.</p>
			<pre>request.body.age = randomNumber(18, 65);</pre>
			<pre>request.params.push({ name: 'limit', value: randomNumber(1, 100), enabled: true });</pre>
			<pre>vars.set('retryCount', randomNumber(1, 3));</pre>
			"""));
		random.add(node("randomDouble(from, to)", """
			<p>Returns a decimal string with 10 digits after the decimal point.</p>
			<pre>request.body.price = randomDouble(1, 100);</pre>
			<pre>vars.set('score', randomDouble(0, 1));</pre>
			<pre>log('ratio', randomDouble(10, 20));</pre>
			"""));
		random.add(node("randomDouble(from, to, afterComma)", """
			<p>Returns a decimal string with exactly <code>afterComma</code> digits after the decimal point.</p>
			<pre>request.body.price = randomDouble(1, 100, 2);</pre>
			<pre>vars.set('latency', randomDouble(10, 20, 3));</pre>
			<pre>log('percent', randomDouble(0, 100, 1));</pre>
			"""));
		root.add(random);

		DefaultMutableTreeNode dates = category("Dates", "Date And Time Functions", """
			<p>Date helpers use UTC. The <code>Millils</code> spelling is kept as implemented.</p>
			""");
		addDateNode(dates, "randomIsoDate()", "random UTC ISO 8601 offset date-time", "request.body.expiresAt = randomIsoDate();");
		addDateNode(dates, "randomRfcDate()", "random UTC RFC 1123 date-time", "request.headers.push({ name: 'X-Date', value: randomRfcDate(), enabled: true });");
		addDateNode(dates, "randomDateTime()", "random UTC date-time in yyyy-MM-dd HH:mm:ss.SSS format", "request.body.createdAt = randomDateTime();");
		addDateNode(dates, "randomDate()", "random UTC date in yyyy-MM-dd format", "request.body.birthDate = randomDate();");
		addDateNode(dates, "randomTime()", "random UTC time in HH:mm:ss.SSS format", "request.body.startTime = randomTime();");
		addDateNode(dates, "randomMillilsDate()", "random epoch milliseconds", "request.body.timestamp = randomMillilsDate();");
		addDateNode(dates, "randomEpochSecondsDate()", "random epoch seconds", "request.body.timestampSeconds = randomEpochSecondsDate();");
		addDateNode(dates, "currentIsoDate()", "current UTC ISO 8601 offset date-time", "request.body.sentAt = currentIsoDate();");
		addDateNode(dates, "currentRfcDate()", "current UTC RFC 1123 date-time", "request.headers.push({ name: 'Date', value: currentRfcDate(), enabled: true });");
		addDateNode(dates, "currentDateTime()", "current UTC date-time in yyyy-MM-dd HH:mm:ss.SSS format", "vars.set('now', currentDateTime());");
		addDateNode(dates, "currentDate()", "current UTC date in yyyy-MM-dd format", "request.params.push({ name: 'date', value: currentDate(), enabled: true });");
		addDateNode(dates, "currentTime()", "current UTC time in HH:mm:ss.SSS format", "request.body.time = currentTime();");
		addDateNode(dates, "currentMillilsDate()", "current epoch milliseconds", "request.body.timestamp = currentMillilsDate();");
		addDateNode(dates, "currentEpochSecondsDate()", "current epoch seconds", "request.body.timestampSeconds = currentEpochSecondsDate();");
		root.add(dates);

		DefaultMutableTreeNode templates = category("Templates", "Placeholder Templates", """
			<p>Templates are resolved after Before Request scripts and before transport. They can be used
			in URL, headers, params, body, form-data values, binary file path, Kafka fields, and gRPC
			endpoint fields.</p>
			""");
		templates.add(node("{{name}}", """
			<p>Reads values from <code>vars</code>, then <code>chainContext</code>, then
			<code>globalContext</code>.</p>
			<pre>URL: {{baseUrl}}/users/{{userId}}</pre>
			<pre>Header value: Bearer {{token}}</pre>
			<pre>{ "tenantId": "{{tenantId}}" }</pre>
			"""));
		templates.add(node("Bare JSON placeholders", """
			<p>When a whole JSON value is a placeholder, the original value type is preserved. Missing
			values become JSON <code>null</code>.</p>
			<pre>{ "id": {{userId}} }</pre>
			<pre>{ "enabled": {{isEnabled}} }</pre>
			<pre>{ "missing": {{unknownValue}} } // becomes null</pre>
			"""));
		templates.add(node("Template functions", """
			<p>Template expressions can call built-in generator functions.</p>
			<pre>{ "id": "{{uuid()}}" }</pre>
			<pre>{ "email": "{{randomEmail()}}" }</pre>
			<pre>{ "createdAt": "{{currentIsoDate()}}" }</pre>
			"""));
		root.add(templates);

		return root;
	}

	private static void addDateNode(DefaultMutableTreeNode parent, String name, String description, String firstExample) {
		String call = name.substring(0, name.indexOf('('));
		parent.add(node(
			name,
			"<p>Returns " + description + ".</p>" +
				"<pre>" + firstExample + "</pre>" +
				"<pre>vars.set('value', " + call + "());</pre>" +
				"<pre>log('" + call + "', context." + call + "());</pre>"
		));
	}

	private static DefaultMutableTreeNode category(String treeTitle, String title, String html) {
		return node(treeTitle, "<h1>" + title + "</h1>" + html);
	}

	private static DefaultMutableTreeNode node(String title, String html) {
		return new DefaultMutableTreeNode(new DocNode(title, html));
	}

	private static String introHtml() {
		return """
			<h1>Webrunner Scripting API</h1>
			<p>This page describes all scripting globals currently exposed by Webrunner.</p>
			<ul>
			  <li>Expand a section in the tree to see functions and objects.</li>
			  <li>Select a function to see usage notes and examples.</li>
			  <li>Examples are valid for Before Request, After Request, Chain scripts, or Global Context where the referenced objects are available.</li>
			</ul>
			""";
	}

	private static String page(String body) {
		return """
			<html>
			<head>
			  <style>
			    body { font-family: sans-serif; font-size: 12px; margin: 12px; color: #dddddd; }
			    h1 { font-size: 18px; margin: 0 0 10px 0; color: #ffffff; }
			    p { margin: 0 0 9px 0; }
			    ul { margin-top: 4px; }
			    li { margin-bottom: 4px; }
			    code { font-family: monospace; color: #9cdcfe; }
			    pre { font-family: monospace; font-size: 12px; background: #2b2d30; color: #e6e6e6; padding: 8px; margin: 8px 0; }
			  </style>
			</head>
			<body>
			""" + body + """
			</body>
			</html>
			""";
	}

	private static final class DocNode {
		private final String title;
		private final String html;

		private DocNode(String title, String html) {
			this.title = title;
			this.html = html;
		}

		@Override
		public String toString() {
			return title;
		}
	}
}
