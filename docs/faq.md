Language: English | [Ukrainian](uk/faq.md)

# FAQ

## The request does not run
1. Ensure URL or gRPC fields are filled in.
2. Check `Before Request` logs for errors.

## Template resolution does not work
1. Use `{{var}}` syntax.
2. Make sure the value exists in `vars`.

## After Request does not run
1. The request may have failed.
2. Use `Debug Call` to see the stage flow.

## How do I see the final outgoing request?
1. Use `Debug Call`.
2. The `Sent Request` stage shows the templated request.

Navigation: [Home](index.md) | [Previous: Import and Export](import-export.md) | [Next: Index](index.md)
