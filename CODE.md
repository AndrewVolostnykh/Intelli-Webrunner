# Code Map

This document maps the main Webrunner features to the packages and classes that implement them.

## Tool Window and UI Composition

### `com.intelli.webrunner.toolwindow`

`WebrunnerToolWindowFactory` creates the Webrunner tool window content inside IntelliJ.

`WebrunnerToolWindowPanel` is the main UI container. It wires together:

- Request tree.
- Request editor.
- Response viewer.
- Execution service.
- Debug sessions.
- Import and export actions.
- Project state service.

It owns the high-level event flow between selected tree nodes, editor changes, request execution, response display, and state persistence.

## Request Tree

### `com.intelli.webrunner.ui`

`RequestTreePanel` renders the saved request tree and handles tree-level actions such as add, rename, clone, delete, move, drag and drop, filtering, and node selection.

`RequestTreeNode` wraps request model objects for display in the Swing tree.

Tree actions usually update the model and then ask the state service to persist the changed collection.

## Request Editor

### `com.intelli.webrunner.ui.RequestEditorPanel`

`RequestEditorPanel` renders and edits the selected request.

It is responsible for:

- HTTP method selector.
- URL or gRPC target fields.
- Kafka producer and listener target fields.
- Send button.
- Send and download button.
- Stop button.
- Debug button.
- Global Context icon button.
- Body, params, headers, before-script, and after-script tabs.
- Stress tab.
- Raw, form-data, and binary body modes.
- Kafka send params such as key type, body type, and partition.
- Kafka listen params such as offset strategy.
- Kafka topic refresh, send, start listening, stop listening, and live response updates.
- Editor-to-model synchronization.
- HTTP request action menu, including `Get cURL`.

The Global Context button opens `GlobalContextDialog`.

During HTTP, gRPC, and Kafka send execution, `RequestEditorPanel` disables start buttons and enables
the stop button until the background request task finishes or is cancelled.

For HTTP requests, `Get cURL` reads the current editor state, generates a cURL command through
`CurlCommandBuilder`, copies it to the system clipboard, and reports the result in the response log.

`StressSettingsPanel` implements the Stress tab UI. It shows HTTP stress configuration fields and
shows `Not implemented` for gRPC and Kafka request types until stress execution is implemented.
When HTTP Stress is enabled, `RequestEditorPanel` starts a background task and delegates repeated
HTTP execution to `HttpStressExecutionService`.

### `com.intelli.webrunner.ui.ChainEditorPanel`

`ChainEditorPanel` renders and edits chain requests. Its toolbar contains icon buttons for running,
stopping, debugging, and stepping to the next chain request, plus a Chain Context icon button for
viewing the current chain state.

The chain editor has a left work area for the chain request list and logs/current state, and a
separate right work area with Options, Request, and Result tabs. Options contains Config controls
for success codes and basic hook execution checkboxes, plus JavaScript editor tabs for run
conditions and chain hooks. Request contains editor-backed Raw Request, Sent Request, and Response
snapshots from the execution pipeline. Result contains editor-backed tabs for body, response
metadata, headers, cookies, and a body snapshot.

Chain Options, Request, and Result are bound to the selected chain step. Each step persists its own
success codes, basic hook checkboxes, JavaScript editor content, request snapshots, and last result
fields. During debug or normal execution, selecting the active chain row also switches the right
work area to that row's configuration and result.

After a chain step executes, the chain list shows a colored runtime badge before the request name.
The status is `Passed` when the response code is included in that step's Success Codes field and
`Failed` otherwise. The same row also shows response code, duration, and response body size.

### `com.intelli.webrunner.ui.GlobalContextDialog`

`GlobalContextDialog` implements the Global Context UI. It contains:

- Variables tab.
- JavaScript code tab.
- Enabled/name/value table for variables.
- Save and cancel actions.

It loads and saves global context through `GlobalWebrunnerStateService`.

## Dev Tools

### `com.intelli.webrunner.toolwindow.WebrunnerToolWindowPanel`

`WebrunnerToolWindowPanel` owns the Dev Tools toolbar button and menu. The menu opens small utility dialogs for ad hoc developer tasks.

It also opens `SettingsDialog` for reusable header presets and feature flags such as Stress Tests.

Current Dev Tools entries include:

- JWT
- Base64
- URL
- JSON
- Text
- Hash
- Compare
- Generate UUID
- DateTime

### `com.intelli.webrunner.ui.JwtDecoderDialog`

`JwtDecoderDialog` opens the JWT decoder window.

### `com.intelli.webrunner.ui.JwtDecoderPanel`

`JwtDecoderPanel` provides the JWT decoder UI. It accepts a soft-wrapped JWT or `Bearer` token, displays expiry derived from the standard `exp` claim, and lets users edit decoded JSON before re-signing HMAC JWTs with a secret.

`com.intelli.webrunner.util.JwtTokenService` decodes JWT header/payload JSON, evaluates `exp`, and creates signatures for `HS256`, `HS384`, and `HS512` tokens.

### `com.intelli.webrunner.ui.Base64ToolDialog`

`Base64ToolDialog` opens the Base64 utility window.

### `com.intelli.webrunner.ui.Base64ToolPanel`

`Base64ToolPanel` provides two soft-wrapped text areas for Base64 conversion. It can decode Base64 to content or swap direction and encode content to Base64.

### `com.intelli.webrunner.ui.JsonToolDialog`

`JsonToolDialog` opens the JSON utility window.

### `com.intelli.webrunner.ui.JsonToolPanel`

`JsonToolPanel` provides a soft-wrapped IntelliJ `EditorTextField` JSON editor with Minify and
Beautify actions backed by Jackson parsing and formatting.

Its three-dot actions menu contains:

- `Remove`, which opens `JsonRemoveDialog`.
- `Replace`, which opens `JsonReplaceDialog`.

Both actions apply literal text transformations to the current JSON editor contents through
`JsonTextOperations`.

### `com.intelli.webrunner.ui.JsonRemoveDialog`

`JsonRemoveDialog` owns the separate Remove window and closes it after the operation is submitted.

### `com.intelli.webrunner.ui.JsonRemovePanel`

`JsonRemovePanel` contains the text input and Remove button used to remove all matching literal text
from the JSON editor.

### `com.intelli.webrunner.ui.JsonReplaceDialog`

`JsonReplaceDialog` owns the separate Replace window and closes it after the operation is submitted.

### `com.intelli.webrunner.ui.JsonReplacePanel`

`JsonReplacePanel` contains the target and replacement inputs and the Replace button used to replace
all matching literal text in the JSON editor.

### `com.intelli.webrunner.ui.TextToolDialog`

`TextToolDialog` opens the plain-text utility window.

### `com.intelli.webrunner.ui.TextToolPanel`

`TextToolPanel` provides a soft-wrapped text editor with whitespace minification, period-based
line-break formatting, and literal Remove and Replace actions.

### `com.intelli.webrunner.ui.HashToolDialog`

`HashToolDialog` opens the Hash utility window.

### `com.intelli.webrunner.ui.HashToolPanel`

`HashToolPanel` provides two soft-wrapped text areas for input and hash output, a hash algorithm
selector, an optional HMAC secret input, and a Hash action.

`com.intelli.webrunner.util.HashingService` hashes UTF-8 text with standard JDK `MessageDigest`
algorithms or, when a non-empty secret is provided, with the matching `javax.crypto.Mac` HMAC
algorithm.

### `com.intelli.webrunner.ui.CompareToolDialog`

`CompareToolDialog` opens the Compare utility input window.

### `com.intelli.webrunner.ui.CompareToolPanel`

`CompareToolPanel` provides left and right soft-wrapped text inputs and opens IntelliJ's Diff Viewer with `DiffManager` and `SimpleDiffRequest`. The diff is opened with `DiffDialogHints.FRAME` so it appears in a separate IntelliJ window instead of an editor tab.

### `com.intelli.webrunner.ui.UuidGeneratorDialog`

`UuidGeneratorDialog` opens the UUID generator window.

### `com.intelli.webrunner.ui.UuidGeneratorPanel`

`UuidGeneratorPanel` displays a generated UUID and provides Generate and Copy actions.

## Response UI

### `com.intelli.webrunner.ui`

`ResponsePanel` displays response status, headers or metadata, cookies, body, timing, and formatting actions.

`ResponseWindowManager` opens responses in separate IDE windows.

Response UI classes receive execution results from `RequestExecutionService` or debug sessions and render them for the user.
HTTP status labels use `HttpStatusReasons` to render standard reason phrases, such as `200 - OK`,
when Java's HTTP client only provides the numeric status code.

## Request Models

### `com.intelli.webrunner.model`

The model package defines persisted request data.

Core model types include:

- HTTP requests.
- gRPC requests.
- Kafka producer requests.
- Kafka listener requests.
- Chain requests.
- Folders.
- Headers.
- Parameters.
- Form-data rows.
- Binary request configuration.
- Response data.
- Kafka bootstrap servers, topic, key, group id, key/body types, partition, and offset strategy fields.

These model objects are used by the request tree, editor panel, execution layer, import/export layer, and persistent state service.

## Execution

### `com.intelli.webrunner.execution.RequestExecutionService`

`RequestExecutionService` is the main runtime entry point for normal request execution.

It is responsible for:

- Running HTTP requests.
- Running gRPC requests.
- Running Kafka producer requests.
- Running chain requests.
- Loading global context.
- Running the global context script before request scripts.
- Running before-request scripts.
- Resolving placeholders.
- Applying `vars` priority over `globalContext`.
- Executing the transport call.
- Running after-response scripts.
- Persisting changed global context.
- Returning execution results to the UI.

For chain execution, it shares one `vars` store across child requests while still using global context as the fallback placeholder source.

Kafka producer execution follows the same before-script, placeholder resolution, transport call, after-script, and response persistence flow. It sends the resolved body, key, headers, optional partition, key type, and body type through `KafkaMessageProducer`, then returns Kafka metadata and the sent payload snapshot in the response body.

`HttpStressExecutionService` runs HTTP stress executions on a worker pool. It uses `HttpStressConfig`
and `HttpStressRequest`, schedules repeated request executions by rate, total duration, request
count, workers, ramp-up, delay, and jitter, and returns a summary `ExecutionResult`.

## HTTP Transport

### `com.intelli.webrunner.http`

HTTP transport classes prepare and send HTTP requests based on the edited model.

They handle:

- Method.
- URL.
- Headers.
- Query parameters.
- Raw body.
- Form-data body.
- Binary body.
- Response conversion.
- Downloaded response content.

`RequestExecutionService` calls into this layer after scripts and placeholders have prepared the final request data.

## gRPC Transport

### `com.intelli.webrunner.grpc`

The gRPC package implements dynamic unary gRPC execution and reflection support.

It handles:

- Plaintext channel creation.
- Reflection-based service discovery.
- Reflection-based method discovery.
- JSON-to-message conversion.
- Dynamic unary calls.
- Metadata conversion.
- Binary metadata with `-bin` keys and `base64:` values.
- Response conversion back to displayable data.

## Kafka Transport

### `com.intelli.webrunner.kafka`

The Kafka package keeps Kafka-specific behavior outside Swing UI code.

`KafkaMetadataService` uses Kafka `AdminClient` to load topic names from the configured bootstrap servers.

`KafkaMessageProducer` sends Kafka messages using `KafkaProducer<byte[], byte[]>`. It handles:

- Bootstrap server validation.
- Topic validation.
- Optional partition selection.
- Key encoding.
- Body encoding.
- Header encoding.
- Kafka send metadata conversion.

`KafkaSendRequest` is the transport input model for producer sends. It carries bootstrap servers, topic, key, key type, body, body type, partition, and headers.

`KafkaSendResult` is the producer result model. It stores topic, partition, offset, timestamp, key bytes, value bytes, and header count.

`KafkaListenerService` owns Kafka consumer sessions. It starts background polling, tracks active listener sessions by request id, stops consumers through `KafkaConsumer#wakeup`, and shuts down all listeners when the tool window is disposed.

`KafkaListenRequest` is the input model for listener sessions. It carries bootstrap servers, topic, group id, and offset strategy.

`KafkaListenMessage` is the display model for consumed records. It stores topic, partition, offset, timestamp, decoded key, decoded body, and decoded headers.

Kafka listener responses are historical. New consumed messages are appended to the persisted response body for the listener request instead of replacing prior messages. If the user switches to another request while listening, polling continues and updates only the listener request state; the visible response panel is updated live only when that listener request is active.

## Scripting

### `com.intelli.webrunner.script.ScriptRuntime`

`ScriptRuntime` executes JavaScript through Rhino.

It exposes these globals to scripts:

- `vars`
- `globalContext`
- `request`
- `rawRequest`
- `response`
- `context`
- `log`
- `logAndReturn`
- `assert`
- `uuid`
- `stringify`
- `jsonify`
- `randomString`
- `randomEmail`
- `randomIsoDate`
- `randomRfcDate`
- `randomDateTime`
- `randomDate`
- `randomTime`
- `randomMillilsDate`
- `randomEpochSecondsDate`
- `currentIsoDate`
- `currentRfcDate`
- `currentDateTime`
- `currentDate`
- `currentTime`
- `currentMillilsDate`
- `currentEpochSecondsDate`
- `randomNumber`
- `randomDouble`

It is used by normal execution and debug execution for before-request, after-response, and inline debug scripts.

### `com.intelli.webrunner.script.ScriptContext`

`ScriptContext` carries runtime objects into script execution.

It stores:

- Current request object.
- Raw persisted request object.
- Current response object.
- `vars` store.
- `globalContext` store.
- Logging sink.

### `com.intelli.webrunner.script.VarsStore`

`VarsStore` is the key/value variable store used by both request variables and global context variables.

It provides the JavaScript-facing API:

- `add`
- `set`
- `get`
- `remove`
- `clear`
- `all`

### `com.intelli.webrunner.script.GlobalContextRuntime`

`GlobalContextRuntime` bridges persistent global context state and the runtime `VarsStore`.

It is responsible for:

- Loading enabled global variables.
- Parsing persisted string values into strings or numbers.
- Running global context JavaScript.
- Producing updated persistent global context variables.
- Merging `globalContext` and `vars` for placeholder resolution.

### `com.intelli.webrunner.script.ScriptLog`

`ScriptLog` captures script log output and script errors for display during normal execution or debugging.

## Placeholder Resolution

### `com.intelli.webrunner.util.TemplateEngine`

`TemplateEngine` replaces `{{name}}` placeholders in request data.

It supports:

- String placeholders.
- Bare JSON placeholders.
- Whitelisted predefined function placeholders such as `{{uuid()}}`.
- JSON string escaping.
- JSON literal preservation for numbers, booleans, arrays, objects, and null.
- Missing bare JSON placeholder replacement with `null`.
- Missing quoted placeholder preservation.

Execution code passes merged variables where `vars` overrides `globalContext`.

## Debugging

### `com.intelli.webrunner.debug.DebugCallSession`

`DebugCallSession` implements step-by-step request and chain debugging.

It handles:

- Loading global context.
- Running global context script.
- Running before-request scripts.
- Resolving placeholders.
- Executing HTTP or gRPC calls.
- Running after-response scripts.
- Running inline debug JavaScript.
- Persisting changed global context.
- Maintaining shared chain `vars`.
- Moving through chain child requests.

### `com.intelli.webrunner.debug`

Other debug package classes represent debug stages, debug state, and debug UI coordination.

## Global Context State

### `com.intelli.webrunner.state.GlobalContextState`

`GlobalContextState` is the persisted model for project-level global context.

It stores:

- Variable rows.
- Enabled flags.
- Variable names.
- Variable values.
- Global JavaScript code.

### `com.intelli.webrunner.state.WebrunnerState`

`WebrunnerState` is the root persisted project state.

It stores:

- Request tree.
- Request data.
- Global context.
- Other plugin-level persisted fields.

### `com.intelli.webrunner.state.GlobalWebrunnerStateService`

`GlobalWebrunnerStateService` is the IntelliJ persistent state service.

It is responsible for:

- Loading state.
- Saving request tree changes.
- Cloning requests with their details, status, and chain state.
- Saving global context.
- Saving global context variables.
- Cloning state for import/export.
- Replacing state during import.
- Merging imported state.

Webrunner JSON import/export includes global context through this service.

## Import and Export

### `com.intelli.webrunner.importexport`

Import/export classes convert between external formats and Webrunner models.

Supported flows include:

- Webrunner JSON export.
- Webrunner JSON import.
- `.http` import.
- OpenAPI import.
- OpenAPI export.

Webrunner JSON preserves request collections and global context. OpenAPI support maps request operations and Webrunner metadata, while global context remains project-level Webrunner state.

### cURL import and export

`WebrunnerToolWindowPanel` adds `Use cURL` to the request-tree three-dot menu. The action opens
`CurlImportDialog`, parses the entered command, creates an HTTP request in the selected folder, and
opens the created request. The dialog also accepts an optional request name; when it is empty, the
generated name is `<METHOD> <URL>`.

`RequestEditorPanel` adds `Get cURL` to the HTTP request action menu. It exports the current method,
URL, enabled headers and parameters, and the selected raw, form-data, or binary body.

### `com.intelli.webrunner.ui.CurlImportDialog`

`CurlImportDialog` contains the optional request-name field and multiline cURL command input.

### `com.intelli.webrunner.util.CurlCommandParser`

`CurlCommandParser` tokenizes and parses common cURL commands, including multiline commands,
single-quoted and double-quoted values, explicit methods, headers, query parameters, raw data,
form-data, binary files, `--url`, and GET data.

### `com.intelli.webrunner.util.CurlRequest`

`CurlRequest` is the parsed cURL data model used to populate a newly created HTTP request.

### `com.intelli.webrunner.util.CurlCommandBuilder`

`CurlCommandBuilder` generates a shell-compatible cURL command from the current HTTP request. It
supports raw bodies, form-data, binary files, enabled headers, and query parameters.

## Body Generation

### `com.intelli.webrunner.proto`

Proto body generator classes create sample request bodies from `.proto` message definitions.

Generated bodies are inserted into the request editor and can then be edited manually.

## Actions

### `com.intelli.webrunner.action`

Action classes register IntelliJ actions for common user operations.

They connect IDE menu items, toolbar items, shortcuts, and context actions to the tool window behavior.

Typical actions include:

- Run request.
- Create request.
- Import collection.
- Export collection.
- Open Webrunner tool window.

## Utilities

### `com.intelli.webrunner.util`

Utility classes provide shared helpers for:

- Template replacement.
- JSON formatting.
- HTTP response cookie extraction from `Set-Cookie` headers.
- Literal JSON editor text removal and replacement through `JsonTextOperations`.
- Plain-text whitespace and period formatting through `TextFormatting`.
- cURL command generation and parsing.
- File handling.
- Model conversion.
- UI helpers.
- Small reusable transformations.

## Tests

### `src/test/java/com/intelli/webrunner/script/ScriptRuntimeTest.java`

Covers JavaScript runtime behavior, script globals, `vars`, `globalContext`, request mutation, response access, assertions, UUID generation, and JSON helper functions.

### `src/test/java/com/intelli/webrunner/script/GlobalContextRuntimeTest.java`

Covers loading, parsing, executing, saving, and merging global context variables.

### `src/test/java/com/intelli/webrunner/util/TemplateEngineTest.java`

Covers placeholder replacement, escaped JSON string values, bare JSON placeholders, missing placeholder behavior, and variable priority scenarios.

### `src/test/java/com/intelli/webrunner/util/CurlCommandBuilderTest.java`

Covers cURL generation for raw requests, enabled headers, query parameters, form-data, binary files,
default protocols, and shell quoting.

### `src/test/java/com/intelli/webrunner/util/CurlCommandParserTest.java`

Covers parsing browser-style multiline cURL commands, headers, explicit methods, query parameters,
raw bodies, multipart form-data, binary files, and GET data.

### `src/test/java/com/intelli/webrunner/util/JsonTextOperationsTest.java`

Covers removing and replacing all literal text occurrences, empty targets, and null replacement
values.

### `src/test/java/com/intelli/webrunner/util/ResponseCookieUtilsTest.java`

Covers extracting response cookies from `Set-Cookie` headers, including cookie attributes and
missing-cookie cases.

### `build.gradle`

Configures the IntelliJ plugin build and test dependencies. JUnit Jupiter is used for the current unit test suite.

Run tests with:

```bash
./gradlew test
```

On Windows:

```powershell
.\gradlew.bat test
```
