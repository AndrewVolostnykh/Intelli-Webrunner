Мова: [English](../grpc.md) | Українська

# gRPC запити

## Верхня панель
1. `Target` — адреса gRPC сервера.
2. `Service` — сервіс з `.proto`.
3. `Method` — метод сервісу.
4. `Reload` завантажує список сервісів через server reflection.
5. `Send` виконує запит.
6. `Debug Call` запускає покрокове виконання.

## Body
1. Вкладка `Body` містить payload для RPC.
2. Шаблонізація застосовується після `Before Request`.

## Headers
1. gRPC metadata задається у `Headers`.
2. Вимкнені рядки не відправляються.

Навігація: [Головна](index.md) | [Попередня: HTTP запити](http.md) | [Наступна: Headers, Params, Body](request-editor.md)
