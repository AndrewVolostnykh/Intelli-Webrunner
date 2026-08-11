# Dev Tools

Dev Tools are small utility windows for preparing, inspecting, converting, and comparing request
data. They are available from the external tools button in the left toolbar.

This page is organized as a documentation tree. Start with the overview, then open the subsection
for the tool you need.

## Documentation tree

- [Overview](#overview)
- [Opening Dev Tools](#opening-dev-tools)
- [JWT](#jwt)
- [Base64](#base64)
- [URL](#url)
- [JSON](#json)
- [Text](#text)
- [Hash](#hash)
- [Compare](#compare)
- [Generate UUID](#generate-uuid)
- [DateTime](#datetime)
- [Common workflows](#common-workflows)

## Overview

Dev Tools are independent from the selected request. They do not automatically mutate request
fields, response fields, variables, or Global Context. Use them as scratch utilities, then copy the
result into the request editor, scripts, tests, headers, or body where needed.

Each tool opens in its own window. On Windows these windows are shown in the taskbar, so they can be
accessed without minimizing IntelliJ IDEA.

## Opening Dev Tools

1. Open the Webrunner tool window.
2. Click the Dev Tools button in the left toolbar.
3. Choose one of the menu items:

| Menu item | Purpose |
| --- | --- |
| `JWT` | Decode JWTs, inspect expiration, and update HMAC-signed tokens. |
| `Base64` | Encode text to Base64 or decode Base64 to text. |
| `URL` | Encode and decode URL text. |
| `JSON` | Format, minify, search, remove, and replace JSON text. |
| `Text` | Minify, basic beautify, remove, and replace plain text. |
| `Hash` | Generate digest or HMAC hex hashes. |
| `Compare` | Compare two text blocks using the IntelliJ diff viewer. |
| `Generate UUID` | Generate and copy UUID values. |
| `DateTime` | Convert between epoch, ISO, RFC, date, and time formats. |

## JWT

The JWT tool decodes a token into JSON and can rebuild an HMAC-signed token after edits.

### Layout

| Area | Description |
| --- | --- |
| Expiration status | Shows `Expired`, `Not Expired`, or `Not exp field`. |
| JWT | Input token. A leading `Bearer` prefix is accepted and stripped before decoding. |
| Decoded JSON | Pretty JSON containing `header`, `payload`, and `signature`. |
| Secret | Secret used when updating an HMAC-signed token. |
| Update | Rebuilds the JWT from edited decoded JSON and the secret. |

Decoded structure:

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "123",
    "exp": 1893456000
  },
  "signature": "..."
}
```

### Expiration

The tool reads `payload.exp` as epoch seconds:

- If `exp` is missing or not numeric, status is `Not exp field`.
- If `exp` is less than or equal to current epoch seconds, status is `Expired`.
- Otherwise status is `Not Expired`.

### Updating tokens

Edit `header` or `payload` in Decoded JSON, enter a Secret, and click `Update`. Updating supports
HMAC algorithms:

- `HS256`
- `HS384`
- `HS512`

Unsupported algorithms or missing secrets are shown as an error in the Decoded JSON area.

## Base64

The Base64 tool converts text between Base64 and UTF-8 content.

### Modes

| Mode | Left side | Right side |
| --- | --- | --- |
| Decode | Base64 input | Decoded UTF-8 content |
| Encode | Plain content input | Base64 output |

Click the swap button between the labels to switch modes.

### Behavior

- Output updates as you type.
- Decode mode removes whitespace from the input before decoding.
- Invalid Base64 is reported as `Invalid Base64: ...`.
- Encode mode uses UTF-8 bytes and standard Base64 output.

## URL

The URL tool converts between readable URL text and percent-encoded URL text.

### Layout

| Field | Description |
| --- | --- |
| Encode | Readable input. Typing here updates Decode. |
| Decode | Encoded input. Typing here updates Encode. |

### Encoding behavior

Characters allowed by URL syntax are kept as-is:

```text
-._~:/?#[]@!$&'()*+,;=
```

Other characters are encoded as UTF-8 percent bytes.

### Decoding behavior

- Valid `%XX` byte sequences are decoded as UTF-8.
- `+` is decoded as a space.
- Invalid percent sequences are left as literal text.

## JSON

The JSON tool is a scratch editor for JSON formatting and text operations.

### Actions

| Action | Description |
| --- | --- |
| `Minify` | Parses JSON and writes compact JSON. |
| `Beautify` | Parses JSON and writes pretty-printed JSON. |
| More > `Remove` | Removes all exact occurrences of a text value. |
| More > `Replace` | Replaces all exact occurrences of one text value with another. |
| `Ctrl+F` / `Cmd+F` | Opens IntelliJ editor search in the JSON editor. |

### Validation

`Minify` and `Beautify` require valid JSON. If parsing fails, the status label shows
`Invalid JSON: ...` and the editor text is left unchanged.

Remove and Replace are text operations. They do not parse JSON and can produce invalid JSON if the
replacement breaks syntax.

## Text

The Text tool is a plain text scratch editor.

### Actions

| Action | Description |
| --- | --- |
| `Minify` | Replaces runs of whitespace with one space and trims the result. |
| `Beautify` | Inserts a newline after each period. |
| More > `Remove` | Removes all exact occurrences of a text value. |
| More > `Replace` | Replaces all exact occurrences of one text value with another. |

Text operations are literal. They do not use regex and do not parse JSON.

## Hash

The Hash tool generates hex digests or HMAC hashes from text input.

### Layout

| Area | Description |
| --- | --- |
| Input | Text to hash. |
| Hash | Hex output. |
| Algorithm | Digest algorithm. |
| HMAC Secret | Optional secret. When filled, the tool uses HMAC. |
| Hash button | Runs the hash operation. |

### Algorithms

Supported algorithms:

- `MD5`
- `SHA-1`
- `SHA-256`
- `SHA-384`
- `SHA-512`

When `HMAC Secret` is empty, the tool uses a normal message digest. When `HMAC Secret` has a value,
the tool uses the matching HMAC algorithm, for example `HmacSHA256` for `SHA-256`.

Output is lowercase hexadecimal.

## Compare

The Compare tool compares two text blocks through IntelliJ's diff viewer.

### Layout

| Field | Description |
| --- | --- |
| Left | First text block. |
| Right | Second text block. |
| Compare | Opens a separate diff window. |

Use it for response comparisons, generated payloads, request snapshots, or any two text values.

## Generate UUID

The Generate UUID tool creates random UUID strings.

### Actions

| Action | Description |
| --- | --- |
| `Generate` | Creates a new UUID and writes it to the field. |
| `Copy` | Copies the current UUID to the system clipboard and shows `Copied`. |

The window generates an initial UUID when it opens.

## DateTime

The DateTime tool displays current time and converts between common timestamp formats.

### Options

| Option | Description |
| --- | --- |
| `Local` | Uses the system default timezone. When disabled, UTC is used. |
| `Fix` | Freezes the Current column at the instant when the checkbox is enabled. |

### Columns

| Column | Description |
| --- | --- |
| Current | Live current time, or fixed time when `Fix` is enabled. |
| Input | Editable conversion area. |

### Supported fields

| Field | Format |
| --- | --- |
| `Millis` | Epoch milliseconds. |
| `Epoch seconds` | Epoch seconds. |
| `ISO 8601` | ISO offset date-time, or local ISO date-time for input. |
| `RFC 1123` | RFC 1123 date-time. |
| `Date time` | `yyyy-MM-dd HH:mm:ss.SSS`. |
| `Date` | `yyyy-MM-dd`. |
| `Time` | `HH:mm:ss.SSS`. |

### Conversion

Edit one field in the Input column and click `Convert`. Webrunner parses the last edited field and
updates every Input field to the same instant.

When parsing `Time`, the current date in the selected timezone is used. Invalid input shows
`Invalid <field>`.

## Common workflows

### Decode a bearer token

1. Open Dev Tools > `JWT`.
2. Paste `Bearer <token>` into JWT.
3. Read `header`, `payload`, `signature`, and expiration status.

### Generate a signed test token

1. Open Dev Tools > `JWT`.
2. Paste an existing HMAC JWT.
3. Edit `payload` in Decoded JSON.
4. Enter the secret.
5. Click `Update`.

### Prepare a JSON request body

1. Open Dev Tools > `JSON`.
2. Paste the payload.
3. Use `Beautify` while editing.
4. Use `Minify` if you need compact JSON.
5. Copy the result into the request body or a test body.

### Create an HMAC header value

1. Open Dev Tools > `Hash`.
2. Paste the signing input.
3. Select `SHA-256` or the required algorithm.
4. Enter `HMAC Secret`.
5. Click `Hash`.
6. Copy the hex output into a header or script variable.

### Convert epoch milliseconds to ISO

1. Open Dev Tools > `DateTime`.
2. Paste the epoch milliseconds into Input > `Millis`.
3. Choose UTC or Local.
4. Click `Convert`.
5. Read Input > `ISO 8601`.

Navigation: [Home](index.md) | [Previous: Import and Export](import-export.md) | [Next: FAQ](faq.md)
