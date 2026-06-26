# Import and Export

Webrunner supports several import/export paths. Use Webrunner JSON when you need a lossless backup
of the plugin state. Use cURL, `.http`, or OpenAPI when you need to move HTTP requests between tools.

## Where to find import/export actions

Tree context menu:

1. Right-click a folder or request in the request tree.
2. Use `Import .http`, `Export .http`, `Import OpenAPI`, or `Export OpenAPI`.

Top three-dot menu above the tree:

1. `Use cURL`
2. `Import Collections (JSON)`
3. `Export Collections (JSON)`
4. `Import .http`
5. `Export .http`

HTTP request actions menu:

1. Open an HTTP request.
2. Click the three-dot request actions button near the URL/send controls.
3. Use `Get cURL` to copy the current HTTP request as a cURL command.

## Webrunner JSON collections

Webrunner JSON is the internal project collection format. Use it for backups, moving Webrunner data
between IDE projects, or preserving data that generic HTTP formats cannot represent.

Export includes:

- Request tree and folders.
- HTTP, gRPC, Kafka, Kafka Listen, and Chain requests.
- Request bodies, headers, params, form data, binary file paths, and scripts.
- Global context variables and global context script.
- Webrunner-specific state that is not represented by `.http`, cURL, or OpenAPI.

Import restores the collection and global context data from the exported file.

## cURL

### Import cURL

Use `Use cURL` from the top three-dot menu. Paste a cURL command and optionally provide a request
name. Webrunner creates one HTTP request in the selected folder and opens it. If the name is empty,
the request name is based on `<METHOD> <URL>`.

Supported cURL input:

- Command must start with `curl`.
- URL as a positional argument or through `--url`.
- Method through `-X`, `-XPOST`, `--request`, or `--request=POST`.
- Headers through `-H`, `-HName: value`, `--header`, or `--header=...`.
- Raw body through `-d`, `--data`, `--data-raw`, `--data-ascii`, and `--data-urlencode`.
- Binary body from a file through `--data-binary @path`.
- Form data through `-F`, `--form`, and `--form=...`.
- Forced text form fields through `--form-string`.
- Query parameters from URL and from `-G`/`--get` with data options.
- Common shell quoting, quoted values, backslash line continuations, and Windows `^` line continuations.

Import behavior:

- If a method is explicit, Webrunner uses it.
- If `-G`/`--get` is present, Webrunner uses `GET` and moves data values into query params.
- If form data, raw body, or binary body is present and no method is explicit, Webrunner uses `POST`.
- URL query params are moved into the Params table.
- `--data-binary @path` sets Binary payload mode and stores the file path.
- `-F file=@path` creates a file form-data row.
- `--form-string name=@value` keeps `@value` as text instead of treating it as a file.

Some cURL options are intentionally ignored because they do not map to a persisted Webrunner request
yet, for example auth/user, user-agent shorthand, referer, cookie shorthand, output file, timeout,
and proxy flags.

### Export cURL

Use `Get cURL` from the HTTP request actions menu.

The generated cURL includes:

- HTTP method.
- URL with enabled query params applied.
- Enabled headers.
- Raw body as `--data-raw`.
- Form data as `-F`, including file rows as `name=@path`.
- Binary file payload as `--data-binary @path`.
- `Content-Type: application/octet-stream` for binary export when no Content-Type header is set.

Export does not execute Before Request scripts and does not resolve placeholders. Persisted values
are exported as they are currently saved, so values like `{{token}}` remain visible in the cURL.

## IntelliJ `.http`

Webrunner imports and exports a practical subset of IntelliJ HTTP files.

### Import `.http`

Use `Import .http` from the tree context menu or the top three-dot menu.

Supported import:

- Multiple requests separated by `###`.
- Request names from `### Request name`.
- Request line in the form `METHOD URL`.
- Headers written as `Name: value`.
- Raw body after the blank line following headers.
- Comments are skipped before the request line and inside the header block.

Import behavior:

- Each parsed request becomes a Webrunner HTTP request.
- Method and URL are copied from the request line.
- URL query params are moved into the Params table.
- Headers are copied into the Headers table and enabled.
- Body text is copied into the raw Body editor.
- Form-data rows are not generated from `.http` multipart syntax.
- Binary payload configuration is not inferred from `.http` file references.
- Before Request and After Request scripts are not imported from `.http`.
- Webrunner placeholders already present in text, such as `{{token}}`, are preserved as plain text.

### Export `.http`

Use `Export .http` from the tree context menu or the top three-dot menu.

Export behavior:

- Exports the current HTTP request or all HTTP requests under the selected tree folder.
- Writes request blocks separated with `### <request name>`.
- Writes `METHOD URL`, where enabled query params are applied to the URL.
- Writes enabled headers.
- Writes the raw body when it is not blank.
- Does not export form-data rows as multipart `.http`.
- Does not export binary file payloads.
- Does not execute scripts or resolve placeholders before export.
- Does not export gRPC, Kafka, Kafka Listen, or Chain requests to `.http`.

## OpenAPI

OpenAPI import/export is for HTTP API structure exchange. It is not as lossless as Webrunner JSON,
but Webrunner adds an `x-webrunner` vendor extension during export to preserve more request state
when the exported file is imported back into Webrunner.

### Import OpenAPI

Use `Import OpenAPI` from the tree context menu.

Supported import:

- OpenAPI JSON files.
- HTTP methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, and `OPTIONS`.
- URL from operation/path/document `servers` plus the OpenAPI path.
- Request name from `summary`, then `operationId`, then fallback `<METHOD> <URL>`.
- Query parameters from path-level and operation-level `parameters`.
- Header parameters from path-level and operation-level `parameters`.
- Parameter values from `example`, `schema.example`, or `schema.default` when present.
- Request body from the first media type example.
- Request body from the first named example value when `examples` is used.
- Webrunner metadata from `x-webrunner` when present.

When `x-webrunner` exists, Webrunner prefers it because it can contain the original request name,
method, URL, headers, params, raw body, Before Request script, and After Request script.

Current limitations:

- OpenAPI schemas are not expanded into generated example payloads when no `example` or `examples`
  value is present.
- Responses are not imported as saved response bodies.
- Auth/security schemes are not converted into headers automatically.
- gRPC, Kafka, Kafka Listen, Chain requests, and global context are not represented by OpenAPI import.

### Export OpenAPI

Use `Export OpenAPI` from the tree context menu.

Export behavior:

- Exports HTTP requests from the selected folder/request scope.
- Creates an OpenAPI 3 JSON document.
- Creates one operation per exported HTTP request.
- Builds paths and server URLs from request URLs when possible.
- Exports enabled query params and headers as OpenAPI parameters.
- Exports request body as a media type example.
- Uses the request `Content-Type` header as the media type when present, otherwise `text/plain`.
- Adds folder names as operation tags.
- Adds `operationId` from request name and id.
- Adds `x-webrunner` metadata with request id, name, method, URL, headers, params, body, and scripts.

Use Webrunner JSON instead of OpenAPI when you need to preserve non-HTTP requests, global context,
chain definitions, Kafka settings, saved UI state, or exact plugin-specific data.

Navigation: [Home](index.md) | [Previous: Chain Mode](chain.md) | [Next: FAQ](faq.md)
