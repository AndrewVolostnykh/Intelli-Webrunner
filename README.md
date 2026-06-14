# Webrunner

Webrunner is an IntelliJ Platform plugin for creating, running, debugging, importing, and exporting API requests directly inside the IDE. It supports HTTP requests, gRPC unary calls, chained scenarios, JavaScript scripting, variable placeholders, response inspection, and reusable project-level global context.

## Main Features

- Manage request collections in a dedicated IntelliJ tool window.
- Create folders, HTTP requests, gRPC requests, and chain requests.
- Execute requests from the editor panel, tree, or shortcuts.
- Debug requests and chains step by step with editable script stages.
- Persist request data, scripts, variables, global context, and UI state between IDE sessions.
- Import and export collections as Webrunner JSON.
- Import requests from `.http` files.
- Import and export OpenAPI documents with Webrunner metadata.
- Generate request bodies from Java classes and `.proto` messages.
- Format JSON request and response bodies.
- Use Dev Tools for JWT decoding, Base64 conversion, JSON formatting, text comparison, and UUID generation.
- Open responses in separate editor windows.
- Save binary responses to disk.

## Tool Window

The main tool window is implemented around a request tree and an editor panel.

The tree lets you:

- Add folders.
- Add HTTP requests.
- Add gRPC requests.
- Add chain requests.
- Rename, duplicate, move, and delete nodes.
- Drag and drop nodes inside the collection.
- Expand and collapse folders.
- Search and filter saved requests.
- Open requests in the editor panel.

Request changes are saved automatically to the plugin state.

## Dev Tools

The Dev Tools button in the Webrunner tool window opens small utility tools for common developer tasks.

Available tools:

- `JWT`: paste a JWT or `Bearer` token and inspect the decoded header, payload, and signature data.
- `Base64`: decode Base64 into content or swap direction and encode content into Base64. Both input areas use soft wrap for long values.
- `JSON`: paste JSON and use `Minify` to compact it or `Beautify` to format it with indentation. The editor uses soft wrap.
- `Compare`: paste left and right content, then open IntelliJ's Diff Viewer in a separate window to inspect differences.
- `Generate UUID`: generate a random UUID and copy it to the clipboard.

## HTTP Requests

HTTP requests support:

- Methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, and `OPTIONS`.
- URL editing with variable placeholders.
- Query parameters.
- Headers.
- Raw body.
- Form data.
- Binary body from a file path.
- Before-request JavaScript.
- After-response JavaScript.
- Normal send.
- Send and download response to a file.
- Debug call.

The HTTP editor has tabs for:

- `Body`
- `Params`
- `Headers`
- `Before`
- `After`

Body modes include:

- `Raw`
- `Form Data`
- `Binary`

## gRPC Requests

gRPC requests support dynamic unary calls.

Supported gRPC features:

- Server address input.
- Service name input.
- Method name input.
- Plaintext channel execution.
- Reflection-based service and method discovery.
- JSON request payloads.
- Request metadata.
- Binary metadata values with the `-bin` suffix using `base64:` values.
- Before-request JavaScript.
- After-response JavaScript.
- Debug call.
- Chain execution.

The gRPC response viewer displays:

- Message body.
- Response metadata.
- Status code.
- Status description.
- Timing information.

## Chain Requests

A chain request runs multiple child requests in order.

Chain behavior:

- Supports HTTP and gRPC children.
- Shares `vars` across all requests in the same chain run.
- Can use global context variables as fallback values.
- Stops execution when a child request fails.
- Runs before and after scripts for each child.
- Supports debug mode with step-by-step execution.
- Shows every child response in sequence.

## JavaScript Scripting

Webrunner uses Rhino to run JavaScript snippets.

Scripts are available in:

- HTTP before-request tab.
- HTTP after-response tab.
- gRPC before-request tab.
- gRPC after-response tab.
- Global Context script tab.
- Debug stage editor.

### Script Runtime

Scripts can use these globals:

- `vars`
- `globalContext`
- `request`
- `rawRequest`
- `response`
- `context`
- `log`
- `assert`
- `uuid`
- `stringify`
- `jsonify`

### `vars`

`vars` is the request-run variable store. In a chain, the same `vars` store is shared by all child requests in that chain run.

Supported API:

```javascript
vars.add("token", "abc");
vars.set("token", "abc");
vars.get("token");
vars.remove("token");
vars.clear();
vars.all();
```

`vars` values can be used in placeholders:

```json
{
  "token": "{{token}}"
}
```

### `globalContext`

`globalContext` is a project-level variable store shared by all requests.

It is available:

- In the Global Context dialog.
- In before-request scripts.
- In after-response scripts.
- In debug scripts.
- During normal request execution.
- During chain execution.
- Across IDE sessions.

Supported API:

```javascript
globalContext.add("baseUrl", "https://api.example.com");
globalContext.set("retryCount", 3);
globalContext.get("baseUrl");
globalContext.remove("retryCount");
globalContext.clear();
globalContext.all();
```

Global context values can be strings or numbers. Numeric table values are parsed as numbers when possible; other values are treated as text.

### Variable Priority

When resolving `{{placeholder}}` values:

1. `vars` is checked first.
2. `globalContext` is checked only if the value is missing from `vars`.

This means request-local or chain-local variables override global values.

Example:

```javascript
globalContext.add("someValue", "GLOBAL");
vars.add("someValue", "LOCAL");
```

`{{someValue}}` resolves to `LOCAL`.

### Request Object

Before-request scripts can modify the outgoing request through `request`.

HTTP request fields include:

- `request.url`
- `request.method`
- `request.body`
- `request.headers`
- `request.params`
- `request.formData`
- `request.binaryFilePath`

gRPC request fields include:

- `request.address`
- `request.service`
- `request.method`
- `request.body`
- `request.metadata`

### Response Object

After-response scripts receive `response`.

HTTP response fields include:

- `response.status`
- `response.statusText`
- `response.headers`
- `response.body`
- `response.timeMs`

gRPC response fields include:

- `response.statusCode`
- `response.statusDescription`
- `response.metadata`
- `response.body`
- `response.timeMs`

### Utility Functions

`log` writes script output to the execution log.

```javascript
log("Token: " + vars.get("token"));
```

`assert` throws a script error when the condition is false.

```javascript
assert(response.status === 200, "Expected HTTP 200");
```

`uuid` generates UUID values.

```javascript
vars.add("requestId", uuid());
```

`stringify` serializes values to JSON text.

```javascript
vars.add("payload", stringify({ enabled: true }));
```

`jsonify` parses JSON text.

```javascript
var payload = jsonify(response.body);
```

## Placeholders

Webrunner supports placeholders in the `{{name}}` format.

Placeholders are resolved after the global context script and before-request script have run.

Supported locations:

- URL.
- Headers.
- Query parameters.
- Raw body.
- Form data.
- Binary file path.
- gRPC metadata.
- gRPC body.

Quoted placeholders are replaced as strings:

```json
{
  "name": "{{userName}}"
}
```

Bare JSON placeholders preserve JSON types:

```json
{
  "someVar": {{someValue}},
  "enabled": {{isEnabled}},
  "count": {{count}}
}
```

If `someValue` is set by a script:

```javascript
vars.add("someValue", "AAA");
```

the outgoing body becomes:

```json
{
  "someVar": "AAA"
}
```

Missing bare JSON placeholders become `null`. Missing quoted placeholders are left as their original template text.

## Global Context UI

The Global Context dialog is opened from the icon button placed next to the Debug button in the request editor toolbar.

The dialog has two tabs:

- Variables.
- JS Code.

The Variables tab contains a table with:

- Enabled checkbox.
- Variable name.
- Variable value.

Only enabled variables are active. Variables are shared across all requests and saved between IDE sessions.

The JS Code tab contains JavaScript that can operate on `globalContext`.

Example:

```javascript
globalContext.add("baseUrl", "https://api.example.com");
globalContext.add("timeoutMs", 5000);
```

The global context script runs before the request before-script, so values created there are available to placeholders and request scripts.

## Debugging

Debug Call allows inspecting and controlling request execution stage by stage.

Debug stages include:

- Global context script.
- Before-request script.
- Placeholder resolution.
- Request execution.
- After-response script.
- Chain child transitions.

During debug, scripts can read and write:

- `vars`
- `globalContext`
- `request`
- `response`

For chain debugging, the debug session keeps shared chain variables and global context behavior consistent with normal execution.

## Import and Export

### Webrunner JSON

Webrunner JSON export includes:

- Request tree.
- Folders.
- HTTP requests.
- gRPC requests.
- Chain requests.
- Headers.
- Query parameters.
- Bodies.
- Scripts.
- Global context variables.
- Global context script.

Importing a Webrunner JSON file restores the collection and global context.

### `.http` Import

The `.http` importer parses request definitions from IntelliJ-style HTTP files and converts them into Webrunner HTTP requests.

Supported imported data includes:

- Request method.
- URL.
- Headers.
- Body.
- Request names when available.

### OpenAPI

OpenAPI support can import operations as Webrunner requests.

It supports:

- Paths.
- Operations.
- Methods.
- Parameters.
- Request bodies.
- Headers where available.
- Webrunner-specific metadata through vendor extensions.

OpenAPI export writes the current collection into an OpenAPI document and preserves supported Webrunner metadata with vendor extensions.

Global context is project-level state, so Webrunner JSON is the format that preserves it directly.

## Body Generation

Webrunner can generate request bodies from code definitions.

Supported generators:

- Java classes.
- `.proto` message definitions.

The generated body can be inserted into the request body editor and then edited manually.

## Header Presets

The plugin includes header preset support for common headers and values.

Header rows can be enabled or disabled, edited manually, and reused through saved request data.

## Response Viewer

The response viewer supports:

- Status display.
- Headers and metadata display.
- Body display.
- JSON formatting.
- Timing display.
- Separate response windows.
- Downloading response content.

## Keyboard Shortcuts

The plugin registers IDE actions for common operations such as running requests, opening the tool window, importing, exporting, and creating request nodes. Exact key bindings can be changed in the IntelliJ keymap settings.

## Persistence

Project state is stored through IntelliJ persistent state services.

Persisted data includes:

- Request tree.
- Request definitions.
- Request scripts.
- Global context variables.
- Global context script.
- UI state needed by the tool window.

## Tests

The scripting and placeholder behavior is covered by JUnit tests.

Current test areas:

- JavaScript runtime globals.
- `vars` operations.
- `globalContext` operations.
- Global context persistence helpers.
- Placeholder replacement.
- Bare JSON placeholder replacement.
- Placeholder priority between `vars` and `globalContext`.
- Missing placeholder behavior.

Run tests with:

```bash
./gradlew test
```

On Windows:

```powershell
.\gradlew.bat test
```

## Development

Build the plugin with Gradle:

```bash
./gradlew build
```

Run the plugin in a sandbox IDE:

```bash
./gradlew runIde
```

The project uses:

- Java.
- Gradle.
- IntelliJ Platform Gradle Plugin.
- Rhino for JavaScript execution.
- JUnit Jupiter for tests.
