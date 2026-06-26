package com.intelli.webrunner.ui;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JTabbedPane;
import java.awt.Component;

/**
 * Non-modal, read-only help dialog describing Webrunner's features and scripting API.
 */
public final class WebrunnerInfoDialog {

	private WebrunnerInfoDialog() {
	}

	public static void show(Component parent) {
		JDialog dialog = new JDialog();
		dialog.setTitle("Webrunner Info");
		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Overview", createInfoTab(overviewText()));
		tabs.add("Before Request", createInfoTab(beforeRequestText()));
		tabs.add("After Request", createInfoTab(afterRequestText()));
		tabs.add("Chain", createInfoTab(chainText()));
		tabs.add("Scripting", createInfoTab(scriptingText()));
		tabs.add("Debug Call", createInfoTab(debugCallText()));
		tabs.add("Scripting API", createInfoTab(scriptingApiText()));
		dialog.getContentPane().add(tabs);
		dialog.setSize(900, 650);
		dialog.setLocationRelativeTo(parent);
		dialog.setModal(false);
		dialog.setVisible(true);
	}

	private static JComponent createInfoTab(String text) {
		JBTextArea area = new JBTextArea(text);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		return new JBScrollPane(area);
	}

	private static String overviewText() {
		return """
			Webrunner — інструмент для виконання HTTP та gRPC запитів прямо в IDE.

			Основні можливості:
			- Дерево запитів і папок: створення, перейменування, видалення, drag&drop.
			- HTTP запити: метод, URL, тіло, заголовки.
			- gRPC запити: target, service, method, автозавантаження сервісів через Reload.
			- Headers: таблиця керування заголовками/metadata (enabled, name, value).
			- Response: перегляд тіла відповіді, заголовків та логів.
			- Scripts: JS-скрипти до і після запиту.
			                - Import/export IntelliJ .http files for HTTP requests.
			""";
	}

	private static String beforeRequestText() {
		return """
			Before Request — скрипт, який виконується ДО відправки запиту.

			Типові сценарії:
			- Підготовка/обчислення даних для body або headers.
			- Створення тимчасових змінних через vars.
			- Логування або швидкі перевірки.

			Доступні об'єкти:
			- vars: сховище змінних між запитами/кроками.
			- request: поточний запит (body + headers), можна змінювати перед відправкою.
			- context: повний контекст скрипта (vars, request, response, helpers, log).

			Приклад:
			log("Before: preparing token");
			vars.add("token", "Bearer " + uuid());
			""";
	}

	private static String afterRequestText() {
		return """
			After Request — скрипт, який виконується ПІСЛЯ отримання відповіді.

			Типові сценарії:
			- Валідація відповіді (status, fields, business-правила).
			- Збереження значень у vars для наступних запитів.
			- Логування ключових подій.

			Доступні об'єкти:
			- response: відповідь (statusCode, headers, body; для gRPC також statusMessage).
			- vars: змінні для наступних кроків.
			- request: запит, який був відправлений.
			- context: повний контекст скрипта.

			Приклад:
			log("Status:", response.statusCode);
			assert(response.statusCode, 200, "Expected 200 OK");
			""";
	}

	private static String chainText() {
		return """
			Chain — режим ланцюжка, який виконує кілька запитів послідовно.

			Особливості:
			- Кожен запит виконується по черзі.
			- Спільне сховище vars дозволяє передавати дані між кроками.
			- Логи та поточний стан доступні у вкладках Chain.

			Типовий сценарій:
			1) Логін (отримати токен)
			2) Зберегти токен у vars
			3) Виконати наступний бізнес-запит з цим токеном
			""";
	}

	private static String scriptingText() {
		return """
			Scripting

			Скрипти виконуються в двох точках: Before Request та After Request. Обидва виконуються
			в одному й тому ж контексті VarsStore та логів, тому дані можуть передаватися між етапами.

			Before Request:
			- Запускається ДО шаблонізації (template engine).
			- Має доступ до `request` (ScriptRequest) і може змінювати body/headers/params/form data/binary path.
			- Має доступ до `rawRequest` (початковий стан до змін скриптом).
			- Будь-який виняток у скрипті зупиняє виконання запиту.
			- Після виконання скрипта формується snapshot `vars`, і саме по ньому виконується шаблонізація:
			  body, headers, params, form data, binary path, а також URL (з params).

			After Request:
			- Запускається ТІЛЬКИ якщо запит успішно відправлено і є відповідь.
			- Має доступ до `response` (HTTP або gRPC), до `request` (вже шаблонізований запит),
			  і до `rawRequest`.
			- Логи скрипта додаються в загальні logs.

			VarsStore:
			- `vars.add(key, value)` додає значення.
			- `vars.get(key)` читає значення.
			- Використовується для шаблонізації через `${...}` у body/headers/params/URL.

			Порядок виконання (звичайний Send):
			1) Before Request (скрипт)
			2) Шаблонізація даних і URL
			3) Відправка запиту
			4) After Request (скрипт)
			""";
	}

	private static String debugCallText() {
		return """
			Debug Call

			Debug Call дозволяє покроково виконати запит та побачити стан на кожному етапі.
			Кнопка `Next` переходить до наступного етапу.

			Етапи:
			1) Current Request
			   - Сніппет поточного стану: body, params, headers, form data, binary path.
			   - Метод/URL (HTTP) або target/service/method (gRPC).

			2) Sent Request
			   - Виконується Before Request (як у звичайному запуску).
			   - Будуються шаблонізовані body/headers/params/form data/binary path та URL.
			   - Показується запит, який буде відправлено, + логи Before Request.

			3) Response Received
			   - Фактична відправка запиту.
			   - Показується статус і відповідь (body + headers).

			4) After Request Logs
			   - Виконується After Request.
			   - Показуються логи After Request.

			5) Final State
			   - Фінальний snapshot запиту після всіх скриптів.
			   - Повні логи.

			Inline Script:
			- Можна виконувати короткі JS-скрипти в поточному контексті.
			- Контекст з’являється після першого `Next` (коли сформовані vars/logger/helpers).
			""";
	}

	private static String scriptingApiText() {
		return """
			Scripting API (JS):

			Глобальні функції:
			- log(...args): запис у Logs. Приймає кілька аргументів.
			- logAndReturn(value), logAndReturn(message, value): пише в Logs і повертає тільки value.
			- assert(actual, expected, message): проста перевірка, пише в лог при невідповідності.
			- uuid(): повертає випадковий UUID.
			- randomString(size), randomEmail(), randomNumber(from, to).
			- randomDouble(from, to), randomDouble(from, to, afterComma).
			- randomIsoDate(), randomRfcDate(), randomDateTime(), randomDate(), randomTime().
			- randomMillilsDate(), randomEpochSecondsDate().
			- currentIsoDate(), currentRfcDate(), currentDateTime(), currentDate(), currentTime().
			- currentMillilsDate(), currentEpochSecondsDate().

			Глобальні об'єкти:
			- vars: VarsStore для збереження значень між запитами.
			  Приклади:
			  vars.add("token", "abc");
			  vars.get("token");

			- request: ScriptRequest (body + headers).
			  Можна змінювати request перед відправкою.

			- response: доступний у After Request.
			  Містить statusCode, headers, body (для gRPC також statusMessage).

			- context: повний ScriptContext (vars, log, helpers, request, response).
			""";
	}
}
