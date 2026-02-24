Language: English | [Ukrainian](uk/debug-call.md)

# Debug Call

Debug Call runs a request step by step and shows the state at each stage.

## Stages
1. `Current Request`
2. Shows current request data from the editor.
3. For HTTP shows method and URL.
4. For gRPC shows target, service, method.

5. `Sent Request`
6. Runs `Before Request`.
7. Builds templated body, headers, params, formData, binaryFilePath and URL.
8. Shows the outgoing request and the `Before Request` logs.

9. `Response Received`
10. Sends the request.
11. Shows status and response.

12. `After Request Logs`
13. Runs `After Request`.
14. Shows `After Request` logs.

15. `Final State`
16. Shows the final request snapshot and full logs.

## Inline Script
1. The `Inline JS` field executes a short script in the current context.
2. The context is available after the first `Next`.

Navigation: [Home](index.md) | [Previous: Scripting](scripting.md) | [Next: Chain Mode](chain.md)
