# Intelli Webrunner Documentation

Intelli Webrunner is an IntelliJ plugin for creating, running, debugging, importing, and exporting
API requests inside the IDE. Use this documentation as the user guide for day-to-day work.

## Start here

1. [Quickstart](quickstart.md) - create your first request and run it.
2. [HTTP Requests](http.md) - HTTP editor, send/download, cURL, placeholders, and request actions.
3. [Headers, Params, Body](request-editor.md) - how request fields are edited and saved.
4. [Response Viewer](response-viewer.md) - body, headers/metadata, logs, formatting, timing, and separate windows.

## Common workflows

- Build HTTP requests with query params, headers, raw body, form-data, or binary body.
- Use Before Request and After Request scripts to prepare data, validate responses, and share variables.
- Use placeholders like `{{token}}` or predefined function calls like `{{uuid()}}` in request data.
- Debug requests step by step with editable script stages.
- Run chain requests where child requests share `vars`.
- Work with gRPC unary calls through reflection and JSON payloads.
- Send Kafka messages or listen to Kafka topics.
- Import/export requests through Webrunner JSON, cURL, IntelliJ `.http`, or OpenAPI.
- Use Dev Tools for JWT, Base64, URL, JSON, Text, Hash, Compare, UUID, and DateTime utilities.

## Feature guides

1. [Scripting](scripting.md) - available globals, helper functions, `vars`, `globalContext`, request/response objects.
2. [Debug Call](debug-call.md) - inspect Global Context, Before Request, placeholder resolution, transport, and After Request.
3. [Chain Mode](chain.md) - run multiple HTTP/gRPC requests in sequence.
4. [Import and Export](import-export.md) - Webrunner JSON, cURL, `.http`, and OpenAPI behavior and limitations.
5. [gRPC Requests](grpc.md) - service discovery, metadata, payloads, execution, and scripting.
6. [Dev Tools](dev-tools.md) - JWT, Base64, URL, JSON, Text, Hash, Compare, UUID, and DateTime utilities.
7. [FAQ](faq.md) - common problems and troubleshooting.

## What gets saved

Webrunner persists the request tree, request definitions, scripts, headers, params, bodies, global
context, and response data in the IDE project state. Use [Import and Export](import-export.md) when
you need to move requests between projects or external tools.

## Requirements

- IntelliJ IDEA 2024.2+ / platform build 242.
- For plugin development: JDK and Gradle wrapper from this repository.

Navigation: [README](../README.md) | [Code map](../CODE_MAP)
