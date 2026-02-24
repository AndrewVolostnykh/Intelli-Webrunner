Мова: [English](../http.md) | Українська

# HTTP запити

## Верхня панель
1. `Method` обирає HTTP метод.
2. `Payload` обирає тип тіла: `Raw`, `Form Data`, `Binary`.
3. `URL` задає адресу запиту.
4. `Send` виконує запит.
5. `Send and Download` виконує запит і пропонує зберегти тіло відповіді у файл.
6. `Debug Call` запускає покрокове виконання.

## Типи payload
1. `Raw` надсилає текстове тіло, зазвичай JSON або plain text.
2. `Form Data` надсилає multipart з таблиці.
3. `Binary` надсилає файл як `application/octet-stream`.

## Send and Download
1. Після виконання відкривається діалог збереження.
2. Якщо сервер повертає `Content-Disposition: filename=...`, імʼя підставляється автоматично.
3. Якщо заголовка немає, використовується `download.bin`.
4. Відповідь все одно показується в UI як звичайний `Send`.

## URL і params
1. `Params` завжди додаються до URL.
2. Params і URL шаблонізуються після `Before Request`.

Навігація: [Головна](index.md) | [Попередня: Швидкий старт](quickstart.md) | [Наступна: gRPC запити](grpc.md)
