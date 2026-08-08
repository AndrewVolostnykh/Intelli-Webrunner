# Headers, Params, Body

The request editor has these tabs:
1. `Body`
2. `Params`
3. `Headers`
4. `Before Request`
5. `After Request`
6. `Stress`

## Body
1. `Raw` shows a text editor.
2. `Form Data` shows a table with `name`, `value`, `type`, `enabled`.
3. `x-www-form-urlencoded` uses the same table and sends enabled rows as URL-encoded form fields.
4. `Binary` lets you choose a file to send as bytes.

## Params
1. Params are added to the URL on execution.
2. Params are template-resolved.

## Headers
1. Headers are defined in a table with `name`, `value`, `enabled`.
2. Disabled rows are not sent.
3. Headers are template-resolved.

## Stress
1. HTTP requests show an `Enabled` checkbox and stress configuration fields.
2. The available fields are `requests per second`, `total duration`, `delay between requests`, `ramp-up time`, `number of requests`, `number of parallel workers`, and `jitter (sec)`.
3. `Total duration`, `delay between requests`, and `ramp-up time` have unit dropdowns: `mills`, `sec`, `min`.
4. Jitter is configured with `from` and `to` dropdowns.
5. When `Enabled` is selected for HTTP, Send runs a background stress test using these settings and writes JSON metrics to `Response`.
6. gRPC and Kafka requests currently show `Not implemented`.

## Auto-save
All fields save automatically while you edit.

Navigation: [Home](index.md) | [Previous: gRPC Requests](grpc.md) | [Next: Response Viewer](response-viewer.md)
