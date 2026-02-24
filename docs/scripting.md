Language: English | [Ukrainian](uk/scripting.md)

# Scripting

Scripts run in JavaScript (Rhino) in two places: `Before Request` and `After Request`.
Both stages share the same `vars` store and log list, so data can flow between them.

## Before Request
1. Runs before template resolution.
2. Can mutate `request` fields: body, headers, params, formData, binaryFilePath.
3. Can read `rawRequest` which is the original request state.
4. Any exception stops the request execution.
5. After the script finishes, a snapshot of `vars` is used for template resolution.

## After Request
1. Runs only if the request was sent and a response exists.
2. Can read `response`, `request` (templated), and `rawRequest`.
3. Script logs are appended to the main log list.

## Template resolution
1. Syntax is `{{var}}`.
2. Applies to URL, body, headers, params, formData, binaryFilePath.
3. If body is valid JSON, templates can replace values as numbers, booleans, objects, or arrays.
4. If body is not JSON, templates are resolved as plain text.

## Context objects
1. `vars` is a key-value store with `get`, `set`, `add`, `all`.
2. `request` is the ScriptRequest you can mutate.
3. `rawRequest` is the original ScriptRequest before mutations.
4. `response` is the HTTP or gRPC response.
5. `context` aggregates `vars`, `request`, `response`, `rawRequest`, `helpers`, `log`.

## request structure
1. `request.body` is a string or JSON object.
2. `request.headers` is an array of `{ name, value, enabled }`.
3. `request.params` is an array of `{ name, value, enabled }`.
4. `request.formData` is an array of `{ name, value, enabled, file }`.
5. `request.binaryFilePath` is a string path.

## response structure
1. HTTP: `response.statusCode`, `response.headers`, `response.body`.
2. gRPC: `response.statusCode`, `response.statusMessage`, `response.headers`, `response.body`.
3. `response.body` is parsed to JSON if valid, otherwise a string.

## Helpers and functions
1. `log(...)` logs values as text or JSON.
2. `assert(actual, expected, message)` logs assertion failures.
3. `uuid()` generates a random UUID.
4. `stringify(value)` converts any value to a JSON string.
5. `jsonify(value)` converts JSON strings to JS objects when possible.

## Examples

```javascript
log("Before: prepare token");
vars.add("token", "Bearer " + uuid());
request.headers = [
  { name: "Authorization", value: "{{token}}", enabled: true }
];
```

```javascript
log("Status:", response.statusCode);
assert(response.statusCode, 200, "Expected 200 OK");
```

Navigation: [Home](index.md) | [Previous: Response Viewer](response-viewer.md) | [Next: Debug Call](debug-call.md)
