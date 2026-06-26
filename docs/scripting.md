# Scripting

Webrunner runs JavaScript scripts with Rhino. Scripts are intended for request preparation,
response validation, logging, test-data generation, and passing values between requests.

Scripts are available in:

- HTTP Before Request.
- HTTP After Request.
- gRPC Before Request.
- gRPC After Request.
- Kafka request scripts where the editor exposes Before/After tabs.
- Global Context script.
- Debug Call script stages.
- Chain execution, where child requests share the same `vars` store.

## Execution order

Normal request execution follows this order:

1. Load enabled Global Context variables.
2. Run Global Context script.
3. Run Before Request script.
4. Resolve placeholders in URL, headers, params, body, form-data, binary path, and protocol-specific fields.
5. Send the request.
6. Run After Request script when a response exists.
7. Save response body, headers/metadata, logs, and timing.

If Before Request throws an exception, the request is not sent. If After Request throws an exception,
the response is still kept and the error is written to logs.

## Before Request

Before Request runs before placeholders are resolved. Use it to create values, mutate the outgoing
request, add headers, generate payloads, or stop execution with an error.

Available objects:

- `vars`
- `globalContext`
- `request`
- `rawRequest`
- `context`
- helper functions such as `uuid()`, `randomEmail()`, `log(...)`

Example: add a generated request id and auth header.

```javascript
var requestId = uuid();

vars.set("requestId", requestId);
request.headers = [
  { name: "X-Request-Id", value: "{{requestId}}", enabled: true },
  { name: "Authorization", value: "Bearer {{token}}", enabled: true }
];

log("Prepared request", requestId);
```

Example: generate JSON body directly.

```javascript
request.body = {
  id: uuid(),
  email: randomEmail(),
  createdAt: currentIsoDate(),
  score: randomNumber(1, 100),
  price: randomDouble(10.0, 20.0, 2)
};
```

When the script finishes, object/array bodies are serialized back to JSON.

## After Request

After Request runs after a response is received. Use it to validate responses, extract data, save
values for the next request, and log important information.

Available objects:

- `vars`
- `globalContext`
- `request` - the templated request that was sent.
- `rawRequest` - the original saved request before script changes.
- `response`
- `context`
- helper functions.

Example: validate status and store response data.

```javascript
log("Status:", response.statusCode);
assert(response.statusCode, 200, "Expected HTTP 200");

vars.set("userId", response.body.id);
vars.set("accessToken", response.body.token);
```

Example: parse a text response manually.

```javascript
var data = jsonify(response.body);
vars.set("createdId", data.id);
log("Created id", data.id);
```

## Global Context script

Global Context is project-level state. It is useful for base URLs, shared credentials, environment
values, and values that should be available to all requests.

Example:

```javascript
globalContext.set("baseUrl", "https://api.example.com");
globalContext.set("tenant", "demo");
```

Then use values in requests:

```text
{{baseUrl}}/users?tenant={{tenant}}
```

`vars` overrides `globalContext` during placeholder resolution. If both stores contain `token`,
`{{token}}` resolves to the value in `vars`.

## `vars` and `globalContext`

Both stores expose the same API:

```javascript
vars.set("name", "value");
vars.add("name", "value");   // alias for set
vars.get("name");
vars.all();

globalContext.set("baseUrl", "https://api.example.com");
globalContext.get("baseUrl");
globalContext.all();
```

Values can be strings, numbers, booleans, arrays, or objects.

```javascript
vars.set("profile", { id: 7, enabled: true });
vars.set("roles", ["admin", "user"]);
```

Use `stringify(value)` when you need JSON text:

```javascript
vars.set("payloadText", stringify({ id: 7, enabled: true }));
```

## `request`

`request` represents the mutable outgoing request.

Fields:

- `request.body`
- `request.headers`
- `request.params`
- `request.formData`
- `request.binaryFilePath`

`request.body` is parsed as a JavaScript object when it contains valid JSON. Otherwise it is a
string. Empty body becomes an empty object.

Header and param shape:

```javascript
{ name: "Header-Name", value: "value", enabled: true }
```

Form-data shape:

```javascript
{ name: "file", value: "C:/tmp/report.pdf", enabled: true, file: true }
```

Example: update an existing JSON body.

```javascript
request.body.traceId = uuid();
request.body.email = randomEmail();
```

Example: replace params.

```javascript
request.params = [
  { name: "page", value: 1, enabled: true },
  { name: "size", value: 50, enabled: true }
];
```

Example: send binary body from a generated path.

```javascript
request.binaryFilePath = "C:/tmp/body.bin";
```

## `rawRequest`

`rawRequest` is the original request snapshot before script mutations. Use it when you need to
compare saved data with the runtime request.

```javascript
log("Original body", rawRequest.body);
log("Runtime body", request.body);
```

Do not use `rawRequest` for mutations. Mutate `request` instead.

## `response`

`response` is available in After Request.

HTTP response fields:

- `response.statusCode`
- `response.headers`
- `response.body`

gRPC response fields:

- `response.statusCode`
- `response.statusMessage`
- `response.headers`
- `response.body`

`response.body` is parsed as a JavaScript object when it contains valid JSON. Otherwise it is a
string.

Example:

```javascript
if (response.statusCode >= 400) {
  log("Request failed", response.statusCode, response.body);
}
```

Headers are exposed as a map of header name to array of values:

```javascript
var contentType = response.headers["content-type"]
  ? response.headers["content-type"][0]
  : "";

log("Content-Type", contentType);
```

## `context`

`context` groups the same objects and helpers:

- `context.vars`
- `context.globalContext`
- `context.request`
- `context.rawRequest`
- `context.response`
- `context.log(...)`
- `context.logAndReturn(...)`
- `context.stringify(...)`
- `context.jsonify(...)`
- predefined random/date helper functions.

Example:

```javascript
context.vars.set("id", context.logAndReturn("Generated id", uuid()));
```

## Logging and assertions

`log(...args)` writes values to the Logs tab. Multiple arguments are joined with spaces. Objects and
arrays are logged as JSON.

```javascript
log("User", { id: 7, enabled: true });
```

`logAndReturn(value)` logs the value and returns the same value.

```javascript
vars.set("id", logAndReturn(uuid()));
```

`logAndReturn(message, value)` logs `message value` and returns only `value`.

```javascript
vars.set("token", logAndReturn("New token", response.body.token));
```

`assert(actual, expected, message)` logs an assertion failure when values do not match. It does not
throw; execution continues.

```javascript
assert(response.statusCode, 200, "Expected successful response");
assert(response.body.ok, true, "Expected ok=true");
```

## JSON helpers

`stringify(value)` converts a value to JSON text.

```javascript
var text = stringify({ a: 1, b: true });
```

`jsonify(value)` parses JSON text into an object when possible. If the input is already an object,
it returns the object.

```javascript
var body = jsonify(response.body);
vars.set("id", body.id);
```

## Random and date helpers

General helpers:

- `uuid()` - random UUID string.
- `randomString(size)` - alphanumeric random string.
- `randomEmail()` - random email-like string.
- `randomNumber(from, to)` - random integer, inclusive.
- `randomDouble(from, to)` - decimal string with 10 digits after the decimal point.
- `randomDouble(from, to, afterComma)` - decimal string with exactly `afterComma` digits.

Date/time helpers use UTC and the same formats as Dev Tools DateTime:

- `randomIsoDate()` - ISO 8601 offset date-time.
- `randomRfcDate()` - RFC 1123 date-time.
- `randomDateTime()` - `yyyy-MM-dd HH:mm:ss.SSS`.
- `randomDate()` - `yyyy-MM-dd`.
- `randomTime()` - `HH:mm:ss.SSS`.
- `randomMillilsDate()` - epoch milliseconds.
- `randomEpochSecondsDate()` - epoch seconds.
- `currentIsoDate()`
- `currentRfcDate()`
- `currentDateTime()`
- `currentDate()`
- `currentTime()`
- `currentMillilsDate()`
- `currentEpochSecondsDate()`

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

Function placeholders are whitelisted. They can call predefined helpers like `{{uuid()}}` or
`{{randomNumber(1, 5)}}`, but they do not execute arbitrary JavaScript.

## Chain mode

In Chain mode, all child requests share one `vars` store during the chain run.

Example:

First request After Request:

```javascript
vars.set("token", response.body.token);
vars.set("userId", response.body.user.id);
```

Second request URL/header/body:

```text
GET {{baseUrl}}/users/{{userId}}
Authorization: Bearer {{token}}
```

This is the main mechanism for passing data between chain steps.

## Debug Call

Debug Call lets you inspect each stage:

1. Current request.
2. Global Context script.
3. Before Request script.
4. Placeholder resolution.
5. Transport call.
6. After Request script.

Use Debug Call when a placeholder resolves incorrectly, a script changes the wrong field, or a
request works manually but fails in a chain.

## Common examples

### Add auth header from global context

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
