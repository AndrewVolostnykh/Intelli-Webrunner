# HTTP Requests

Webrunner provides a complete HTTP request workflow inside the IntelliJ tool window. An HTTP request
stores its method, URL, query parameters, headers, body configuration, scripts, and latest response.
Changes are saved automatically.

## Creating an HTTP request

Click the Add Request button above the request tree, enter a name, select `HTTP`, and confirm.

The request is created inside the currently selected folder. If a request is selected, its parent
folder is used.

You can also create requests by:

- Importing a cURL command with `Use cURL`.
- Importing an IntelliJ `.http` file.
- Importing an OpenAPI document.
- Importing a Webrunner collection.

## HTTP editor overview

The top toolbar contains:

1. HTTP method.
2. Payload type.
3. URL.
4. Send.
5. Send and Download.
6. Stop.
7. Debug Call.
8. Global Context.
9. Three-dot request actions menu.

The editor contains `Body`, `Params`, `Headers`, `Before Request`, `After Request`, and `Stress` tabs. The
response viewer is displayed below them.

## HTTP methods

The method selector supports `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, and `OPTIONS`.

Example:

```text
GET https://api.example.com/users
```

When cURL import contains request data without an explicit method, it infers `POST`. Requests without
data default to `GET`.

## URL

Enter an absolute URL or a host without a protocol:

```text
https://api.example.com/users
localhost:8080/health
api.example.com/v1/orders
```

When no protocol is present, `localhost` uses `http://` and other hosts use `https://`.

The URL can contain query parameters and placeholders:

```text
https://api.example.com/users/{{userId}}?include=roles
```

## Query parameters

Use the `Params` tab to manage enabled parameter name/value rows.

| Enabled | Name | Value |
|---|---|---|
| Yes | page | 1 |
| Yes | size | 25 |
| No | debug | true |

The resulting URL is:

```text
https://api.example.com/users?page=1&size=25
```

The URL and Params table are synchronized:

- Editing Params updates the URL query string.
- Editing the URL and leaving the field updates matching Params rows.
- Existing identical name/value pairs are not appended twice during execution.
- Names and values from the table are URL-encoded.
- Disabled or unnamed rows are ignored.

## Headers

Use the `Headers` tab to define enabled header name/value rows.

| Enabled | Name | Value |
|---|---|---|
| Yes | Accept | application/json |
| Yes | Authorization | Bearer {{accessToken}} |
| Yes | X-Request-ID | {{requestId}} |

Disabled or unnamed headers are ignored. Names and values support placeholders. The header-name
editor suggests common HTTP names and configured header presets.

## Body types

The payload selector supports `Raw`, `Form Data`, and `Binary`.

### Raw

Raw sends the Body editor contents as text. It is suitable for JSON, XML, GraphQL, or plain text.
A blank body is sent as no body. Add the required Content-Type header explicitly.

```text
Content-Type: application/json
```

Example body:

```json
{
  "name": "{{userName}}",
  "enabled": {{isEnabled}},
  "roles": {{roles}}
}
```

With:

```javascript
vars.set("userName", "Alice");
vars.set("isEnabled", true);
vars.set("roles", ["admin", "editor"]);
```

the outgoing JSON is:

```json
{
  "name": "Alice",
  "enabled": true,
  "roles": ["admin", "editor"]
}
```

### Form Data

Form Data sends `multipart/form-data`. Each row has an enabled state, field name, Text/File type,
and value or path.

| Enabled | Name | Type | Value |
|---|---|---|---|
| Yes | description | Text | Profile image |
| Yes | image | File | `/home/user/avatar.png` |

Webrunner generates the multipart boundary. If Content-Type is absent, it adds:

```text
Content-Type: multipart/form-data; boundary=...
```

Names, values, and file paths support placeholders.

### Binary

Binary sends the selected file as the complete body. Use Browse to select it.

```text
/home/user/archive.zip
```

If Content-Type is absent, Webrunner adds:

```text
Content-Type: application/octet-stream
```

The path supports placeholders:

```text
{{fixturesDirectory}}/document.pdf
```

## Placeholders

Placeholders use `{{name}}` and are resolved before the transport call.

Supported locations:

- URL.
- Query parameter names and values.
- Header names and values.
- Raw body.
- Form-data names, values, and file paths.
- Binary file path.

Example:

```text
URL: https://{{host}}/users/{{userId}}
Authorization: Bearer {{accessToken}}
```

`vars` values override `globalContext` values. Placeholders can also call whitelisted predefined
functions, for example `{{uuid()}}` or `{{randomString(10)}}`. Missing placeholders inside JSON
strings remain unchanged; missing bare JSON placeholders become `null`.

See [Scripting](scripting.md) for the full variable and helper API.

## Before Request

The `Before Request` JavaScript runs before placeholders are resolved. It can generate variables,
modify body/headers/params/form-data/binary path, log data, and reject invalid input.

```javascript
vars.set("requestId", uuid());
vars.set("accessToken", globalContext.get("accessToken"));

request.headers = [
  { name: "Authorization", value: "Bearer {{accessToken}}", enabled: true },
  { name: "X-Request-ID", value: "{{requestId}}", enabled: true }
];
```

Updating a JSON body:

```javascript
request.body.sentAt = new Date().toISOString();
```

If this script throws, the HTTP call is skipped and the error appears in the log.

## After Request

The `After Request` JavaScript runs after a response is received. Use it to validate the response,
extract values, update global context, or write logs.

```javascript
assert(response.statusCode, 201, "Expected HTTP 201");

var result = jsonify(response.body);
vars.set("createdUserId", result.id);
log("Created user: " + result.id);
```

Persisting a token:

```javascript
var result = jsonify(response.body);
globalContext.set("accessToken", result.accessToken);
```

After-script errors are logged while the received response remains available.

## Global Context

The Global Context button opens project-level variables and JavaScript shared by requests.

| Enabled | Name | Value |
|---|---|---|
| Yes | host | api.example.com |
| Yes | timeout | 5000 |

Example:

```text
https://{{host}}/users
```

The global script runs before the request's Before Request script.

## Sending requests

Send runs this pipeline:

1. Load and execute Global Context.
2. Create the request script object.
3. Run Before Request.
4. Resolve placeholders.
5. Normalize the URL and append enabled params.
6. Build the selected body type.
7. Send the HTTP request.
8. Run After Request.
9. Persist changed Global Context values.
10. Display response and logs.

The HTTP client uses a 10-second connection timeout and a 30-second request timeout.
While a request is running, Send is disabled and Stop is enabled. Stop cancels the active background
request task.

## Send and Download

Send and Download uses the normal pipeline but preserves response bytes and opens a save dialog.
`Content-Disposition` supplies the suggested filename when available; otherwise the default is
`download.bin`. The response is still displayed normally.

## Debug Call

Debug Call opens step-by-step execution for Global Context, Before Request, placeholder resolution,
the HTTP call, and After Request. You can inspect request data, variables, response, and logs between
stages.

See [Debug Call](debug-call.md).

## Response viewer

The response area displays:

- HTTP status.
- Headers.
- Body.
- Duration.
- Script and execution logs.

JSON can be formatted, and the response can be opened in a separate window. See
[Response Viewer](response-viewer.md).

## Get cURL

Open the three-dot menu beside the URL and run buttons, then choose `Get cURL`. The command is copied
to the clipboard and includes method, URL, enabled params and headers, and the selected body type.

Raw example:

```bash
curl -X 'POST' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer {{accessToken}}' \
  --data-raw '{"name":"Alice"}' \
  'https://api.example.com/users'
```

Form-data example:

```bash
curl -X 'POST' \
  -F 'description=Profile image' \
  -F 'image=@/home/user/avatar.png' \
  'https://api.example.com/upload'
```

Binary example:

```bash
curl -X 'PUT' \
  -H 'Content-Type: application/octet-stream' \
  --data-binary '@/home/user/archive.zip' \
  'https://api.example.com/files/archive.zip'
```

Export does not execute Before Request or resolve runtime variables, so placeholders remain visible.

## Use cURL

Open the three-dot menu above the request tree and choose `Use cURL`. The dialog contains an optional
request name and multiline cURL input. A blank name becomes `<METHOD> <URL>`.

Supported options include:

- `-X`, `--request`
- `-H`, `--header`
- `-d`, `--data`, `--data-raw`, `--data-ascii`, `--data-urlencode`
- `-F`, `--form`, `--form-string`
- `--data-binary @file`
- `--url`
- `-G`, `--get`
- Single and double quotes.
- Backslash and Windows caret multiline continuation.

Example:

```bash
curl 'https://api.example.com/users?active=true' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer token' \
  --data-raw '{"name":"Alice"}'
```

Webrunner creates the request in the selected folder, fills its method, URL, params, headers, body,
form-data, or binary path, and opens it.

## Request actions menu

The HTTP request menu contains:

- `Get cURL`
- `Open Request`
- `Open Response`
- `Proto body`

Open Request opens Body, Before Request, and After Request in a separate window. Open Response opens
the response separately. Proto body generates sample bodies from Protobuf definitions.

## Formatting

The editor formatting action formats:

- JSON request bodies.
- Response JSON and response headers.
- Before Request JavaScript.
- After Request JavaScript.

Use the configured Webrunner format shortcut. The default tool-window bindings include
`Ctrl+Shift+L` and `Ctrl+Alt+L`.

## Import and export

HTTP requests can also be exchanged through:

- Webrunner collection JSON.
- IntelliJ `.http` files.
- OpenAPI documents.

The `.http` importer supports names, methods, URLs, headers, and bodies. OpenAPI import creates
requests from operations; export writes supported request data and Webrunner metadata.

See [Import and Export](import-export.md).

## Auto-save

Webrunner saves method, payload type, URL, params, headers, body, form-data, binary path, scripts,
latest response, response headers, and logs automatically.

## Complete examples

### GET with params and authorization

```text
Method: GET
URL: https://api.example.com/users
```

Params:

| Enabled | Name | Value |
|---|---|---|
| Yes | page | 1 |
| Yes | size | 20 |

Headers:

| Enabled | Name | Value |
|---|---|---|
| Yes | Accept | application/json |
| Yes | Authorization | Bearer {{accessToken}} |

Result:

```http
GET https://api.example.com/users?page=1&size=20
Accept: application/json
Authorization: Bearer <resolved token>
```

### POST JSON and extract an ID

```text
Method: POST
Payload: Raw
URL: https://api.example.com/users
```

Headers:

```text
Content-Type: application/json
Accept: application/json
```

Body:

```json
{
  "name": "{{userName}}",
  "email": "{{userEmail}}"
}
```

Before Request:

```javascript
vars.set("userName", "Alice");
vars.set("userEmail", "alice@example.com");
```

After Request:

```javascript
assert(response.statusCode, 201, "User was not created");
var user = jsonify(response.body);
globalContext.set("lastCreatedUserId", user.id);
```

### Multipart upload

```text
Method: POST
Payload: Form Data
URL: https://api.example.com/files
```

| Enabled | Name | Type | Value |
|---|---|---|---|
| Yes | category | Text | invoices |
| Yes | file | File | `{{fixturesDirectory}}/invoice.pdf` |

### Binary download

```text
Method: GET
URL: https://api.example.com/reports/{{reportId}}/pdf
```

Use Send and Download to choose where to save the response.

## Troubleshooting

### Missing or invalid URL

The request is not sent when URL is empty. Check spaces, protocols, and unresolved placeholders.

### Duplicate params

Exact existing name/value pairs are deduplicated. Different values with the same name are valid and
may appear multiple times.

### Missing Content-Type

Raw does not add Content-Type automatically. Form Data and Binary add defaults only when a matching
header is absent.

### Script failure

Review the log. A Before Request error prevents transport; an After Request error does not discard
the response.

### File failure

Verify the resolved path exists and IntelliJ can read it.

### cURL import failure

The command must start with `curl`, contain a URL, and use balanced quotes. Specialized options that
do not map to Webrunner fields may be ignored.

Navigation: [Home](index.md) | [Previous: Quickstart](quickstart.md) | [Next: gRPC Requests](grpc.md)
