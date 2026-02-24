Мова: [English](../scripting.md) | Українська

# Scripting

Скрипти виконуються на JavaScript (Rhino) у двох точках: `Before Request` і `After Request`.
Обидва етапи працюють зі спільним `vars` і логами, тому дані можуть передаватися між ними.

## Before Request
1. Виконується перед шаблонізацією.
2. Може змінювати `request`: body, headers, params, formData, binaryFilePath.
3. Може читати `rawRequest` — початковий стан запиту.
4. Будь-який виняток зупиняє виконання запиту.
5. Після виконання формується snapshot `vars`, який використовується для шаблонізації.

## After Request
1. Виконується лише якщо запит відправлено і є відповідь.
2. Має доступ до `response`, `request` (шаблонізований) і `rawRequest`.
3. Логи додаються у спільний список.

## Шаблонізація
1. Синтаксис `{{var}}`.
2. Працює для URL, body, headers, params, formData, binaryFilePath.
3. Якщо body — JSON, шаблони можуть замінювати значення як числа, booleans, обʼєкти або масиви.
4. Якщо body не JSON, шаблони замінюються як текст.

## Контекстні обʼєкти
1. `vars` — сховище значень з `get`, `set`, `add`, `all`.
2. `request` — ScriptRequest, який можна змінювати.
3. `rawRequest` — оригінальний ScriptRequest до змін.
4. `response` — HTTP або gRPC відповідь.
5. `context` — обʼєкт з `vars`, `request`, `response`, `rawRequest`, `helpers`, `log`.

## Структура request
1. `request.body` — рядок або JSON-обʼєкт.
2. `request.headers` — масив `{ name, value, enabled }`.
3. `request.params` — масив `{ name, value, enabled }`.
4. `request.formData` — масив `{ name, value, enabled, file }`.
5. `request.binaryFilePath` — шлях до файлу.

## Структура response
1. HTTP: `response.statusCode`, `response.headers`, `response.body`.
2. gRPC: `response.statusCode`, `response.statusMessage`, `response.headers`, `response.body`.
3. `response.body` парситься як JSON, якщо це валідний JSON.

## Helpers і функції
1. `log(...)` логує значення як текст або JSON.
2. `assert(actual, expected, message)` логує помилки перевірки.
3. `uuid()` генерує UUID.
4. `stringify(value)` перетворює значення в JSON-рядок.
5. `jsonify(value)` перетворює JSON-рядок у JS-обʼєкт.

## Приклади

```javascript
log("Before: prepare token");
vars.add("token", "Bearer " + uuid());
request.headers = [
  { name: "Authorization", value: "{{token}}", enabled: true }
];
```

```javascript
log("Status:", response.statusCode);
assert(response.statusCode, 200, "Expected 200 OK");
```

Навігація: [Головна](index.md) | [Попередня: Відповідь і логи](response-viewer.md) | [Наступна: Debug Call](debug-call.md)
