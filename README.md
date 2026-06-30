# Webrunner

Webrunner is an IntelliJ Platform plugin for building, running, debugging, importing, and exporting
API requests directly inside the IDE.

It provides a persistent request collection, an editor for HTTP/gRPC/Kafka workflows, scripting,
placeholders, response viewing, import/export tools, and small developer utilities.

## Documentation

- User docs start here: [docs/index.md](docs/index.md)
- Import/export details: [docs/import-export.md](docs/import-export.md)
- Scripting API: [docs/scripting.md](docs/scripting.md)
- Internal code map for contributors: [CODE.md](CODE.md)

## Main features

- Request tree with folders, search, drag-and-drop, rename, duplicate, move, and delete.
- HTTP requests with params, headers, raw body, form-data, binary body, scripts, debug mode, cURL export, and response download.
- HTTP Stress tab UI for request rate, duration, request count, workers, ramp-up, delay, and jitter range settings.
- gRPC unary calls with reflection, metadata, JSON payloads, scripts, chain execution, and debug mode.
- Kafka producer requests with configurable key/body types, headers, topic metadata refresh, scripts, and optional partition.
- Kafka Listen requests with `Latest`/`Earliest` offset strategy and live appended response history.
- Chain requests that run HTTP/gRPC children in order and share `vars` across the run.
- Global Context variables and script shared across requests.
- JavaScript scripting in Before Request and After Request.
- Placeholders in URLs, headers, params, bodies, form data, binary paths, gRPC fields, and Kafka fields.
- Placeholder function calls for predefined helpers, for example `{{uuid()}}` and `{{randomString(10)}}`.
- Import/export through Webrunner JSON, cURL, IntelliJ `.http`, and OpenAPI.
- Proto body generation for `.proto` message definitions.
- Response viewer with status, headers/metadata, body, logs, JSON formatting, timing, and separate response windows.
- Dev Tools: JWT, Base64, URL, JSON, Text, Hash, Compare, Generate UUID, and DateTime.

## Scripting quick example

```javascript
vars.set("requestId", uuid());
vars.set("email", randomEmail());
request.headers = [
  { name: "X-Request-Id", value: "{{requestId}}", enabled: true }
];
```

Payload placeholders can use variables or predefined functions:

```json
{
  "id": "{{uuid()}}",
  "email": "{{email}}",
  "createdAt": "{{currentIsoDate()}}"
}
```

See [docs/scripting.md](docs/scripting.md) for the full scripting API.

## Development

Requirements:

- JDK compatible with the IntelliJ Platform Gradle Plugin configuration.
- Gradle wrapper from this repository.
- IntelliJ IDEA for running/debugging the plugin sandbox.

Run tests:

```powershell
.\gradlew.bat test
```

Build:

```powershell
.\gradlew.bat build
```

Run in sandbox IDE:

```powershell
.\gradlew.bat runIde
```

Before changing code, read:

- [CODE.md](CODE.md) for project structure and implementation notes.
- [tempLocal/REQUIREMENTS_TO_CODE.md](tempLocal/REQUIREMENTS_TO_CODE.md) for local code requirements.
