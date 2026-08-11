# Scripting

Webrunner runs JavaScript scripts with Rhino. Use scripts to prepare outgoing requests, generate
test data, validate responses, log diagnostic information, and pass values between requests.

This page is organized as a documentation tree. Start with the overview, then open the subsection
for the feature you need.

## Documentation tree

- [Overview](#overview)
- [Where scripts run](#where-scripts-run)
- [Execution order](#execution-order)
- [Runtime globals](#runtime-globals)
- [Variable stores](#variable-stores)
- [Global Context](#global-context)
- [Chain Context](#chain-context)
- [Request objects](#request-objects)
- [Response and message objects](#response-and-message-objects)
- [Logging and assertions](#logging-and-assertions)
- [JSON helpers](#json-helpers)
- [Random and date helpers](#random-and-date-helpers)
- [Placeholders](#placeholders)
- [Chain scripting](#chain-scripting)
- [Request tests](#request-tests)
- [Debug Call](#debug-call)
- [Common examples](#common-examples)

## Overview

Scripts run inside a request execution context. The most common flow is:

1. Set or read variables.
2. Optionally mutate `request`.
3. Let Webrunner resolve placeholders such as `{{token}}`.
4. Send the request.
5. Validate `response` and store values for later requests.

Scripts can use plain JavaScript syntax plus Webrunner globals such as `vars`, `globalContext`,
`request`, `response`, `log`, `assert`, and data generation helpers like `uuid()`.

## Where scripts run

Scripts are available in:

- HTTP Before Request and After Request.
- gRPC Before Request and After Request.
- Kafka producer Before Request and After Request.
- Kafka listener message script.
- Global Context script.
- Request tests.
- Debug Call stages.
- Chain execution.

## Execution order

Normal request execution follows this order:

1. Load enabled Global Context variables.
2. Run Global Context script.
3. Run Before Request script.
4. Resolve placeholders in request data.
5. Send the HTTP, gRPC, or Kafka request.
6. Run After Request script when a response exists.
7. Persist response body, headers or metadata, cookies, logs, timing, and changed Global Context values.

If Before Request throws, the transport call is not sent and the error is written to logs. If After
Request throws, the response is still kept and the error is written to logs.

## Runtime globals

These names are available directly in scripts and through `context`.

| Global | Description |
| --- | --- |
| `vars` | Request-run variable store. Shared by chain child requests during one chain run. |
| `globalContext` | Project-level variable store loaded before request scripts. |
| `chainContext` | Chain-level variable store available during chain execution. |
| `request` | Mutable outgoing request snapshot. |
| `rawRequest` | Original saved request snapshot before script mutations. |
| `response` | Response object in After Request scripts. Empty object before a response exists. |
| `message` | Alias for `response`; mainly useful for Kafka listener scripts. |
| `context` | Object containing the same stores, request/response objects, and helper functions. |
| `log(...)` | Writes to execution logs. |
| `logAndReturn(...)` | Logs a value and returns it. |
| `assert(actual, expected, message)` | Records assertion failures in logs. |
| `stringify(value)` | Converts a value to JSON text. |
| `jsonify(value)` | Parses JSON text into an object when possible. |
| `getRequest(nameOrId)` | Reads a previous chain request snapshot. |
| `skip(...)` | Skips the current chain step. |
| `interrupt(...)` | Interrupts the chain. |
| `interruptChain(...)` | Alias for `interrupt(...)`. |
| `proceed()` | Clears chain flow-control state back to normal execution. |
| `uuid()` and random/date helpers | Generate IDs, random values, and timestamps. |

`context` exposes these same members as `context.vars`, `context.request`, `context.log(...)`,
`context.getRequest(...)`, and so on.

## Variable stores

`vars`, `globalContext`, and `chainContext` expose the same API:

| Method | Description |
| --- | --- |
| `get(name)` | Returns a stored value. Missing values return JavaScript `undefined`. |
| `set(name, value)` | Stores a value. |
| `add(name, value)` | Alias for `set`. |
| `all()` | Returns all entries as an object. |

Values can be strings, numbers, booleans, arrays, or objects.

```javascript
vars.set("token", "abc");
vars.add("count", 3);
vars.set("profile", { id: 7, enabled: true });
vars.set("roles", ["admin", "editor"]);

log(vars.get("token"));
log(stringify(vars.all()));
```

For placeholder resolution, Webrunner merges stores in this priority:

1. `globalContext`
2. `chainContext`
3. `vars`

Later stores override earlier stores. If all three contain `token`, `{{token}}` resolves from
`vars`.

## Global Context

Global Context is project-level state for values shared by requests: base URLs, tenant ids,
credentials, environment flags, or defaults.

The Global Context window has:

- Variables table: enabled name/value entries.
- JS Code tab: script that runs before request Before Request scripts.

Enabled table values are loaded first. Numeric-looking values are parsed as numbers. Then the
Global Context script runs and can mutate `globalContext`.

```javascript
globalContext.set("baseUrl", "https://api.example.com");
globalContext.set("tenant", "demo");
globalContext.set("accessToken", "secret");
```

Use those values in request URLs, headers, params, and bodies:

```text
{{baseUrl}}/users?tenant={{tenant}}
Authorization: Bearer {{accessToken}}
```

Global Context changes are persisted after request execution.

## Chain Context

`chainContext` is a variable store scoped to chain execution. It is useful for values that should be
shared across all steps in a chain but should not become project-level Global Context.

```javascript
chainContext.set("runId", uuid());
chainContext.set("startedAt", currentIsoDate());
```

In chain placeholder resolution, `chainContext` sits between Global Context and `vars`:

```text
{{runId}}
```

Chain Context can be edited from the Chain Context window and can also be changed by scripts during
the chain run.

## Request objects

`request` is the mutable outgoing request. Before Request scripts should mutate `request`; `rawRequest`
should be treated as read-only.

### `request` fields

| Field | Description |
| --- | --- |
| `request.body` | Body as an object/array when saved body is valid JSON, otherwise a string. Empty body becomes `{}`. |
| `request.headers` | Array of header entries. |
| `request.params` | Array of query parameter entries. |
| `request.formData` | Array of form-data entries. |
| `request.binaryFilePath` | Path used by binary body mode. |

Header and param entry shape:

```javascript
{ name: "Header-Name", value: "value", enabled: true }
```

Form-data entry shape:

```javascript
{ name: "file", value: "C:/tmp/report.pdf", enabled: true, file: true }
```

Examples:

```javascript
request.body.traceId = uuid();
request.body.email = randomEmail();

request.headers.push({
  name: "X-Trace-Id",
  value: request.body.traceId,
  enabled: true
});

request.params = [
  { name: "page", value: 1, enabled: true },
  { name: "size", value: 50, enabled: true }
];
```

When the script finishes, object and array bodies are serialized back to JSON.

### `rawRequest`

`rawRequest` is the original saved request before script changes and placeholder resolution. Use it
for comparison or diagnostics.

```javascript
log("Saved body", rawRequest.body);
log("Runtime body", request.body);
```

Do not mutate `rawRequest`; changes are not intended to affect execution.

## Response and message objects

`response` is available in After Request scripts. Before a response exists it is an empty object.
`message` is an alias for `response`.

### HTTP response

| Field | Description |
| --- | --- |
| `response.statusCode` | HTTP status code. |
| `response.headers` | Map of header name to array of values. |
| `response.body` | Parsed JSON object/array when possible, otherwise text. |

```javascript
assert(response.statusCode, 200, "Expected HTTP 200");
vars.set("userId", response.body.id);
```

Headers are exposed as arrays:

```javascript
var contentType = response.headers["content-type"]
  ? response.headers["content-type"][0]
  : "";
```

### gRPC response

| Field | Description |
| --- | --- |
| `response.statusCode` | gRPC status code. |
| `response.statusMessage` | gRPC status message. |
| `response.headers` | Response metadata map. |
| `response.body` | Parsed JSON object/array when possible, otherwise text. |

```javascript
assert(response.statusCode, 0, "Expected OK");
vars.set("grpcResult", response.body);
```

### Kafka listener message

Kafka listener scripts receive the consumed message as `response` and `message`.

| Field | Description |
| --- | --- |
| `message.topic` | Topic name. |
| `message.partition` | Partition number. |
| `message.offset` | Record offset. |
| `message.timestamp` | Record timestamp. |
| `message.key` | Record key. |
| `message.body` | Parsed JSON object/array when possible, otherwise text. |
| `message.headers` | Array of `{ name, value }` header entries. |

```javascript
log("Kafka message", message.topic, message.partition, message.offset);
vars.set("lastKafkaId", message.body.id);
```

## Logging and assertions

`log(...args)` writes to the Logs tab. Multiple arguments are joined with spaces. Objects and arrays
are logged as JSON.

```javascript
log("User", { id: 7, enabled: true });
```

`logAndReturn(value)` logs the value and returns it.

```javascript
vars.set("id", logAndReturn(uuid()));
```

`logAndReturn(message, value)` logs `message value` and returns only `value`.

```javascript
vars.set("token", logAndReturn("New token", response.body.token));
```

`assert(actual, expected, message)` records an assertion failure when values do not match. It does
not throw; script execution continues. Request tests and chain test runs treat assertion failures as
failed tests.

```javascript
assert(response.statusCode, 200, "Expected successful response");
assert(response.body.ok, true, "Expected ok=true");
```

When `expected` is `null`, the assertion checks that `actual` is present and truthy, except `false`
is treated as a failure.

```javascript
assert(response.body.token, null, "Expected token to exist");
```

Objects and arrays are compared structurally. Mismatch logs include JSON paths.

```javascript
assert(response.body, { ok: true, user: { id: 7 } }, "Unexpected response body");
```

## JSON helpers

`stringify(value)` converts a value to JSON text. If the input is already a string, it returns that
string.

```javascript
vars.set("payloadText", stringify({ id: 7, enabled: true }));
```

`jsonify(value)` parses JSON text into an object when possible. If the value is already an object or
array, it returns a script object/array. Empty or missing input becomes an empty object.

```javascript
var body = jsonify(response.body);
vars.set("id", body.id);
```

## Random and date helpers

General helpers:

| Function | Description |
| --- | --- |
| `uuid()` | Random UUID string. |
| `randomString(size)` | Alphanumeric random string. Returns empty string when `size <= 0`. |
| `randomEmail()` | Random email-like string. |
| `randomNumber(from, to)` | Random integer, inclusive. Throws when `from > to`. |
| `randomDouble(from, to)` | Decimal string with 10 digits after the decimal point. |
| `randomDouble(from, to, afterComma)` | Decimal string with exactly `afterComma` digits. |

Date/time helpers use UTC:

| Function | Format |
| --- | --- |
| `randomIsoDate()` | ISO 8601 offset date-time. |
| `randomRfcDate()` | RFC 1123 date-time. |
| `randomDateTime()` | `yyyy-MM-dd HH:mm:ss.SSS`. |
| `randomDate()` | `yyyy-MM-dd`. |
| `randomTime()` | `HH:mm:ss.SSS`. |
| `randomMillilsDate()` | Epoch milliseconds. |
| `randomEpochSecondsDate()` | Epoch seconds. |
| `currentIsoDate()` | Current ISO 8601 offset date-time. |
| `currentRfcDate()` | Current RFC 1123 date-time. |
| `currentDateTime()` | Current `yyyy-MM-dd HH:mm:ss.SSS`. |
| `currentDate()` | Current `yyyy-MM-dd`. |
| `currentTime()` | Current `HH:mm:ss.SSS`. |
| `currentMillilsDate()` | Current epoch milliseconds. |
| `currentEpochSecondsDate()` | Current epoch seconds. |

Example:

```javascript
request.body = {
  id: uuid(),
  email: randomEmail(),
  date: currentDate(),
  timestamp: currentMillilsDate(),
  amount: randomDouble(10, 100, 2)
};
```

## Placeholders

Placeholders are resolved after Before Request and before sending the request.

Variable placeholder:

```json
{
  "token": "{{token}}"
}
```

Function placeholder:

```json
{
  "id": "{{uuid()}}",
  "name": "test-{{randomString(6)}}",
  "createdAt": "{{currentIsoDate()}}"
}
```

Bare JSON placeholders preserve non-string values:

```json
{
  "enabled": {{enabled}},
  "count": {{count}},
  "profile": {{profile}}
}
```

If the script sets:

```javascript
vars.set("enabled", true);
vars.set("count", 3);
vars.set("profile", { id: 7, name: "Alice" });
```

the outgoing JSON becomes:

```json
{
  "enabled": true,
  "count": 3,
  "profile": { "id": 7, "name": "Alice" }
}
```

Missing bare JSON placeholders become `null`. Missing quoted placeholders stay unchanged.

Function placeholders are whitelisted. They can call predefined helpers such as `{{uuid()}}` or
`{{randomNumber(1, 5)}}`, but they do not execute arbitrary JavaScript.

## Chain scripting

In Chain mode, all child requests share one `vars` store during the chain run. This is the main
mechanism for passing response data from one step to another.

First request After Request:

```javascript
assert(response.statusCode, 200, "Expected login success");
vars.set("token", response.body.token);
vars.set("userId", response.body.user.id);
```

Second request URL/header/body:

```text
GET {{baseUrl}}/users/{{userId}}
Authorization: Bearer {{token}}
```

### Chain step scripts

Each chain step can define chain-level Before Request and After Request scripts. Step config
checkboxes decide whether Webrunner also runs the selected request's own Before Request, After
Request, Stress, and Tests settings.

### `getRequest(nameOrId)`

After a chain step runs, Webrunner stores a snapshot that can be read by later steps:

```javascript
var login = getRequest("Login");
vars.set("token", login.result.body.token);
```

Snapshot structure:

| Field | Description |
| --- | --- |
| `meta` | Request id, name, and type. |
| `configuredRequest` | Body, headers, params, formData, binary path, and protocol fields before execution. |
| `request` | Sent request snapshot. |
| `sentRequest` | Alias of sent request snapshot. |
| `rawRequest` | Original saved request snapshot. |
| `response` | Response snapshot. |
| `result` | Status, body, headers, cookies, logs, and duration. |

Snapshots are available by request name and request id.

### Flow control

Chain scripts can control execution:

| Function | Effect |
| --- | --- |
| `skip(...values)` | Marks current step as skipped and skips sending when called before transport. |
| `interrupt(...values)` | Interrupts the chain. |
| `interruptChain(...values)` | Alias for `interrupt(...)`. |
| `proceed()` | Resets flow control to normal execution. |

```javascript
if (!vars.get("token")) {
  interruptChain("Missing token");
}
```

## Request tests

Request tests are request variants. Base is the original request. Each custom test has independent
body, params, headers, form-data, binary path, Before Request, and After Response scripts.

When a test runs:

1. The selected test data is applied over the base request details.
2. The normal scripting and placeholder flow runs.
3. Assertions in After Request are written to logs.
4. The test badge becomes `Passed` for 2xx responses without assertion failures.
5. The test badge becomes `Failed` for non-2xx responses or assertion failures.
6. Disabled tests are skipped.

When Chain `Run basic tests` is enabled for a step, Webrunner runs Base and then enabled custom
tests before moving to the next chain request. If any test fails, the chain marks that request as
`Failed` and stops on that failure.

## Debug Call

Debug Call lets you inspect and edit each stage:

1. Current request.
2. Global Context script.
3. Before Request script.
4. Placeholder resolution.
5. Transport call.
6. After Request script.

Use Debug Call when a placeholder resolves incorrectly, a script changes the wrong field, or a
request works manually but fails in a chain.

## Common examples

### Add auth header from Global Context

```javascript
request.headers = request.headers || [];
request.headers.push({
  name: "Authorization",
  value: "Bearer " + globalContext.get("accessToken"),
  enabled: true
});
```

### Store value for the next request

```javascript
assert(response.statusCode, 200, "Expected login success");
vars.set("token", response.body.token);
```

### Fail fast in Before Request

```javascript
if (!globalContext.get("baseUrl")) {
  throw "Missing baseUrl in Global Context";
}
```

### Build form-data

```javascript
request.formData = [
  { name: "metadata", value: stringify({ id: uuid() }), enabled: true, file: false },
  { name: "file", value: "C:/tmp/report.pdf", enabled: true, file: true }
];
```

### Use generated values in payload without script variables

```json
{
  "id": "{{uuid()}}",
  "email": "{{randomEmail()}}",
  "createdAt": "{{currentIsoDate()}}"
}
```

Navigation: [Home](index.md) | [Previous: Response Viewer](response-viewer.md) | [Next: Debug Call](debug-call.md)
