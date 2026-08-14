package com.non_organic_onion.intelli.webrunner.script;

import com.non_organic_onion.intelli.webrunner.state.GlobalContextState;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import com.non_organic_onion.intelli.webrunner.state.IntellijGlobalContextStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalContextRuntimeTest {

	@Test
	void loadAndRunParsesEnabledTableValuesRunsScriptAndPersistsChanges() {
		GlobalWebrunnerStateService stateService = new GlobalWebrunnerStateService();
		GlobalContextState state = new GlobalContextState();
		state.variables = new ArrayList<>(List.of(
			variable("text", "AAA", true),
			variable("intValue", "42", true),
			variable("doubleValue", "3.14", true),
			variable("disabled", "hidden", false)
		));
		state.script = """
			globalContext.add('fromScript', globalContext.get('text') + '-script');
			globalContext.add('intValue', globalContext.get('intValue') + 1);
			""";
		stateService.saveGlobalContext(state);
		List<String> logs = new ArrayList<>();

		VarsStore globalContext =
			new GlobalContextRuntime(new IntellijGlobalContextStore(stateService), new ScriptRuntime()).loadAndRun(logs::add);

		assertEquals("AAA", globalContext.get("text"));
		assertEquals(43.0, globalContext.get("intValue"));
		assertEquals(3.14, globalContext.get("doubleValue"));
		assertEquals("AAA-script", globalContext.get("fromScript"));
		assertEquals(null, globalContext.get("disabled"));

		GlobalContextState persisted = stateService.getGlobalContext();
		assertEquals("43.0", valueOf(persisted, "intValue"));
		assertEquals("AAA-script", valueOf(persisted, "fromScript"));
		assertEquals("hidden", valueOf(persisted, "disabled"));
	}

	@Test
	void mergeForTemplatesUsesGlobalContextAsFallbackAndVarsAsOverride() {
		VarsStore globalContext = new VarsStore();
		globalContext.add("shared", "global");
		globalContext.add("onlyGlobal", 7);
		VarsStore vars = new VarsStore();
		vars.add("shared", "local");

		Map<String, Object> merged =
			new GlobalContextRuntime(new IntellijGlobalContextStore(new GlobalWebrunnerStateService()), new ScriptRuntime())
			.mergeForTemplates(globalContext, vars);

		assertEquals("local", merged.get("shared"));
		assertEquals(7, merged.get("onlyGlobal"));
	}

	@Test
	void mergeForTemplatesUsesChainContextBetweenGlobalContextAndVars() {
		VarsStore globalContext = new VarsStore();
		globalContext.add("shared", "global");
		globalContext.add("onlyGlobal", 7);
		VarsStore chainContext = new VarsStore();
		chainContext.add("shared", "chain");
		chainContext.add("onlyChain", "chain-value");
		VarsStore vars = new VarsStore();
		vars.add("shared", "request");

		Map<String, Object> merged =
			new GlobalContextRuntime(new IntellijGlobalContextStore(new GlobalWebrunnerStateService()), new ScriptRuntime())
			.mergeForTemplates(globalContext, chainContext, vars);

		assertEquals("request", merged.get("shared"));
		assertEquals("chain-value", merged.get("onlyChain"));
		assertEquals(7, merged.get("onlyGlobal"));
	}

	private HeaderEntryState variable(String name, String value, boolean enabled) {
		HeaderEntryState entry = new HeaderEntryState();
		entry.name = name;
		entry.value = value;
		entry.enabled = enabled;
		return entry;
	}

	private String valueOf(GlobalContextState state, String name) {
		return state.variables.stream()
			.filter(entry -> name.equals(entry.name))
			.findFirst()
			.map(entry -> entry.value)
			.orElse(null);
	}
}
