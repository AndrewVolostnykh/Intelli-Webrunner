Мова: [English](../debug-call.md) | Українська

# Debug Call

Debug Call запускає запит покроково та показує стан на кожному етапі.

## Етапи
1. `Current Request`
2. Показує поточні дані запиту з редактора.
3. Для HTTP показує метод і URL.
4. Для gRPC показує target, service, method.

5. `Sent Request`
6. Виконує `Before Request`.
7. Будує шаблонізовані body, headers, params, formData, binaryFilePath та URL.
8. Показує запит, який буде відправлено, і логи `Before Request`.

9. `Response Received`
10. Відправляє запит.
11. Показує статус і відповідь.

12. `After Request Logs`
13. Виконує `After Request`.
14. Показує логи `After Request`.

15. `Final State`
16. Показує фінальний snapshot запиту та повні логи.

## Inline Script
1. Поле `Inline JS` виконує короткий скрипт у поточному контексті.
2. Контекст доступний після першого `Next`.

Навігація: [Головна](index.md) | [Попередня: Scripting](scripting.md) | [Наступна: Chain mode](chain.md)
