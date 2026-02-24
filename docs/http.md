Language: English | [Ukrainian](uk/http.md)

# HTTP Requests

## Top bar
1. `Method` selects the HTTP method.
2. `Payload` selects body type: `Raw`, `Form Data`, `Binary`.
3. `URL` sets the request URL.
4. `Send` executes the request.
5. `Send and Download` executes and saves the response body to a file.
6. `Debug Call` starts step-by-step execution.

## Payload types
1. `Raw` sends a string body, usually JSON or plain text.
2. `Form Data` sends multipart with entries from the table.
3. `Binary` sends a file as `application/octet-stream`.

## Send and Download
1. After the request, a save dialog appears.
2. If the server provides `Content-Disposition: filename=...`, it becomes the suggested name.
3. If not, the default name is `download.bin`.
4. The response still appears in the UI like a normal `Send`.

## URL and params
1. `Params` are always appended to the URL.
2. Params and URL are template-resolved after `Before Request`.

Navigation: [Home](index.md) | [Previous: Quickstart](quickstart.md) | [Next: gRPC Requests](grpc.md)
