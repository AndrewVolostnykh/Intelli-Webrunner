# Plugin Development Requirements

## Commit Messages

This project follows the Conventional Commits specification.

Format:

`type(scope): description`

Examples:

- `feat(chaining): add scripting data for sent requests`
- `fix(parser): handle empty response body`
- `refactor(http): simplify request execution`
- `docs(readme): add installation instructions`
- `test(chaining): add request scripting tests`
- `build(gradle): migrate to IntelliJ Platform Gradle Plugin 2.x`

### Allowed Types

- `feat` — new functionality
- `fix` — bug fix
- `refactor` — code changes without changing behavior
- `docs` — documentation
- `test` — tests
- `build` — build system or dependencies
- `ci` — CI/CD configuration
- `chore` — maintenance tasks
- `perf` — performance improvements

## 1. Architecture Requirements

The plugin must be divided into logical layers: UI, business logic, IntelliJ Platform API integration, services, data models, and utilities.

The ToolWindow must be responsible only for creating and displaying the main plugin interface.

The ToolWindow must not contain business logic, data processing logic, HTTP/Kafka requests, file operations, or complex interaction scenarios.

All UI components must be implemented as separate classes.

Business logic must be placed in separate services or managers.

Classes must have clear responsibilities and must not combine several unrelated tasks at the same time.

The code must be structured in a way that allows new features to be added without significant changes to existing components.

Reusable logic must not be directly tied to the UI.

## 2. UI Requirements

UI components must use standard IntelliJ Platform components or Swing components recommended for plugin development.

For long-running operations, the plugin must display progress status or loading messages.

The UI must not be blocked during long-running operations, such as HTTP requests, Kafka requests, or large data processing.

Visual components must not directly execute business operations. Instead, they must call the appropriate services.

## 3. Business Logic Requirements

Business logic must be isolated from the UI.

Logic related to HTTP, Kafka, files, configuration, IntelliJ API, and other external interactions must be separated into dedicated classes.

Each plugin feature must have a clear execution flow: input data, processing, result, and error handling.

The same logic must not be duplicated in different parts of the plugin.

All external dependencies must be hidden behind services or adapters.

For complex operations, separate request, response, and state models should be used.

The logic must be written in a way that allows it to be tested without launching the full plugin UI.

## 4. Code Structure Requirements

Classes must not be too large. If necessary, logic should be split into smaller components.

Methods must perform one specific task.

Code duplication must be avoided.

Constants, settings keys, and other technical values must be moved to separate configuration classes or files.

The code must be formatted consistently across the entire project.

Unused code, temporary debug output, or unnecessary commented-out blocks must not be left in the codebase.

A centralized logging approach must be used instead of random println statements.

## 5. IntelliJ Platform API Requirements

The plugin must use official IntelliJ Platform mechanisms for ToolWindow, Actions, Services, Notifications, and settings.

Access to project files, the editor API, PSI, or other internal IntelliJ structures must be isolated in dedicated classes.

Operations that modify files or the project structure must be executed through the appropriate IntelliJ Platform mechanisms.

Long-running operations must not be executed on the UI thread.

Operation results must be shown using standard IntelliJ notification mechanisms.

The plugin must work correctly when a project is opened, closed, or changed.

## 6. Testing Requirements

All core business logic must be covered by tests.

Unit tests must be written for services, utilities, parsers, validators, and data handlers.

Integration tests must be written for scenarios involving interaction between multiple components.

Tests must cover not only successful scenarios, but also errors, invalid data, and missing responses from external services.

Tests must not depend on real external services unless it is a separately defined integration test.

Mocks must be used for external APIs, Kafka, or HTTP requests.

Before adding a new feature, the corresponding tests must be added or updated.

Changes to existing logic must not break already written tests.

## 7. Error Handling Requirements

All errors must be handled centrally or through a consistent mechanism.

The plugin must not crash because of unhandled exceptions.

The user must see a clear and understandable message about the problem.

Technical error details must be written to logs when they are needed for diagnostics.

For external requests, the plugin must handle timeouts, connection loss, invalid responses, and authorization errors.

Errors related to requests must be written to the Log tab in a readable format.

## 8. Performance Requirements

The plugin must not slow down IntelliJ IDEA startup.

Heavy operations must be executed asynchronously or in the background.

The UI must remain responsive while requests are being executed.

The plugin must not perform unnecessary requests or constant checks without a valid reason.

Memory leaks must be avoided, especially in components that live during the entire project lifecycle.

## 9. Documentation Requirements

The project must maintain an up-to-date README.md.

README.md must include a plugin description, main features, launch instructions, build instructions, and usage examples.

GitHub Pages (directory /docs) documentation must be updated when plugin functionality changes.

The project must include a CODE.md file describing the code structure and the responsibilities of the main classes.

CODE.md must explain which module or class is responsible for which part of the plugin.

Complex parts of the logic must be described in documentation or comments.

Code comments must explain non-obvious decisions instead of simply duplicating method names.