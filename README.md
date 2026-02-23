<p align="center">
  <img src="./src/main/resources/icons/pluginIcon.svg" width="80" alt="App Icon"><br>
</p>

<h1 align="center">Intelli Webrunner</h1>

Intelli Webrunner is an IntelliJ IDEA tool window for running HTTP and gRPC requests directly inside the IDE. It focuses on fast iteration, request organization, scripting, and multi-step flows without leaving your editor.

### Features
- Request tree with folders: create, rename, delete, and drag & drop to organize collections.
- HTTP requests with method, URL, body, headers, and query params.
- gRPC requests with target, service, and method, plus service discovery via server reflection (Reload).
- Response viewer for body, headers, and execution logs.
- Before/After JavaScript scripts with access to `vars`, `request`, `response`, `log`, and helpers.
- Template placeholders in URL/body/headers/params using `{{var}}` values.
- Chain mode to run multiple requests sequentially with shared `vars`, logs, and current state.
- Import/export IntelliJ `.http` files for HTTP requests.

### Scripting
Scripts run with the Rhino JS engine and can:
- Read and write `vars` across requests or chain steps.
- Mutate the outgoing `request` (body, headers, params) in `Before Request`.
- Validate and inspect the `response` in `After Request`.
- Use helper functions like `log(...)`, `assert(actual, expected, message)`, and `uuid()`.

### Chain Mode
Chain mode executes a list of requests in order. All steps share the same `vars` store so data can flow between requests. A debug mode lets you step through the chain one request at a time and inspect logs/state.

### Requirements
- IntelliJ IDEA 2024.2+ (build `242`).
- Java 17 for development/builds.

### Supported API protocols

- HTTP
- gRPC
