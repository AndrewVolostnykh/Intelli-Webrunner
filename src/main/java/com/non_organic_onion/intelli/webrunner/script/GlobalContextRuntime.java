package com.non_organic_onion.intelli.webrunner.script;

import com.non_organic_onion.intelli.webrunner.state.GlobalContextState;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads, executes, and persists the project-wide global context used as a fallback
 * source for template placeholders.
 */
public final class GlobalContextRuntime {

	private final GlobalContextStore globalContextStore;
	private final ScriptRuntime scriptRuntime;

	public GlobalContextRuntime(
		GlobalContextStore globalContextStore,
		ScriptRuntime scriptRuntime
	) {
		this.globalContextStore = globalContextStore;
		this.scriptRuntime = scriptRuntime;
	}

	public VarsStore loadAndRun(ScriptLogger logger) {
		GlobalContextState state = globalContextStore.getGlobalContext();
		VarsStore globalContext = toVarsStore(state.variables);
		String script = state.script == null ? "" : state.script;
		if (!script.isBlank()) {
			ScriptLogger safeLogger = logger == null ? message -> {
			} : logger;
			ScriptHelpers helpers = new ScriptHelpers(safeLogger);
			ScriptRequest emptyRequest = new ScriptRequest("", List.of(), List.of());
			scriptRuntime.runScript(
				script,
				new ScriptContext(
					new VarsStore(),
					safeLogger,
					helpers,
					emptyRequest,
					emptyRequest,
					null,
					globalContext
				)
			);
		}
		globalContextStore.saveGlobalContextVariables(globalContext.entries());
		return globalContext;
	}

	public Map<String, Object> mergeForTemplates(
		VarsStore globalContext,
		VarsStore vars
	) {
		return mergeForTemplates(globalContext, null, vars);
	}

	public Map<String, Object> mergeForTemplates(
		VarsStore globalContext,
		VarsStore chainContext,
		VarsStore vars
	) {
		Map<String, Object> merged = new LinkedHashMap<>();
		if (globalContext != null) {
			merged.putAll(globalContext.entries());
		}
		if (chainContext != null) {
			merged.putAll(chainContext.entries());
		}
		if (vars != null) {
			merged.putAll(vars.entries());
		}
		return merged;
	}

	public void persist(VarsStore globalContext) {
		if (globalContext != null) {
			globalContextStore.saveGlobalContextVariables(globalContext.entries());
		}
	}

	private VarsStore toVarsStore(List<HeaderEntryState> variables) {
		VarsStore store = new VarsStore();
		if (variables == null) {
			return store;
		}
		for (HeaderEntryState variable : variables) {
			if (variable == null || !variable.enabled || variable.name == null || variable.name.isBlank()) {
				continue;
			}
			store.add(variable.name, parseValue(variable.value));
		}
		return store;
	}

	private Object parseValue(String value) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		if (trimmed.matches("-?\\d+")) {
			try {
				return Long.parseLong(trimmed);
			} catch (NumberFormatException ignored) {
				return value;
			}
		}
		if (trimmed.matches("-?\\d+\\.\\d+")) {
			try {
				return Double.parseDouble(trimmed);
			} catch (NumberFormatException ignored) {
				return value;
			}
		}
		return value;
	}
}
