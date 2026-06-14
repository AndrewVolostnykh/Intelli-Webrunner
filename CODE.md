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

`RequestTreePanel` renders the saved request tree and handles tree-level actions such as add, rename, duplicate, delete, move, drag and drop, filtering, and node selection.

`RequestTreeNode` wraps request model objects for display in the Swing tree.

Tree actions usually update the model and then ask the state service to persist the changed collection.

## Request Editor

### `com.intelli.webrunner.ui.RequestEditorPanel`

`RequestEditorPanel` renders and edits the selected request.

It is responsible for:

- HTTP method selector.
- URL or gRPC target fields.
- Send button.
- Send and download button.
- Debug button.
- Global Context icon button.
- Body, params, headers, before-script, and after-script tabs.
- Raw, form-data, and binary body modes.
- Editor-to-model synchronization.

The Global Context button opens `GlobalContextDialog`.

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

Current Dev Tools entries include:

- JWT
- Base64
- JSON
- Compare
- Generate UUID

### `com.intelli.webrunner.ui.JwtDecoderDialog`

`JwtDecoderDialog` opens the JWT decoder window.

### `com.intelli.webrunner.ui.JwtDecoderPanel`

`JwtDecoderPanel` provides the JWT decoder UI. It accepts a JWT or `Bearer` token and displays decoded header and payload JSON.

### `com.intelli.webrunner.ui.Base64ToolDialog`

`Base64ToolDialog` opens the Base64 utility window.

### `com.intelli.webrunner.ui.Base64ToolPanel`

`Base64ToolPanel` provides two soft-wrapped text areas for Base64 conversion. It can decode Base64 to content or swap direction and encode content to Base64.

### `com.intelli.webrunner.ui.JsonToolDialog`

`JsonToolDialog` opens the JSON utility window.

### `com.intelli.webrunner.ui.JsonToolPanel`

`JsonToolPanel` provides a soft-wrapped JSON editor with Minify and Beautify actions backed by Jackson parsing and formatting.

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

`ResponsePanel` displays response status, headers or metadata, body, timing, and formatting actions.

`ResponseWindowManager` opens responses in separate IDE windows.

Response UI classes receive execution results from `RequestExecutionService` or debug sessions and render them for the user.

## Request Models

### `com.intelli.webrunner.model`

The model package defines persisted request data.

Core model types include:

- HTTP requests.
- gRPC requests.
- Chain requests.
- Folders.
- Headers.
- Parameters.
- Form-data rows.
- Binary request configuration.
- Response data.

These model objects are used by the request tree, editor panel, execution layer, import/export layer, and persistent state service.

## Execution

### `com.intelli.webrunner.execution.RequestExecutionService`

`RequestExecutionService` is the main runtime entry point for normal request execution.

It is responsible for:

- Running HTTP requests.
- Running gRPC requests.
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
- `assert`
- `uuid`
- `stringify`
- `jsonify`

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

## Body Generation

### `com.intelli.webrunner.generator`

Body generator classes create sample request bodies from source definitions.

Supported generation areas include:

- Java class body generation.
- `.proto` message body generation.

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
