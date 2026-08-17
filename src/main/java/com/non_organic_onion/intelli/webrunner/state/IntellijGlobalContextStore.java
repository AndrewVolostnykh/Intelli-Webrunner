package com.non_organic_onion.intelli.webrunner.state;

import com.non_organic_onion.webrunner.core.script.GlobalContextStore;
import com.non_organic_onion.webrunner.core.state.GlobalContextState;

import java.util.Map;

public final class IntellijGlobalContextStore implements GlobalContextStore {

	private final GlobalWebrunnerStateService stateService;

	public IntellijGlobalContextStore(GlobalWebrunnerStateService stateService) {
		this.stateService = stateService;
	}

	@Override
	public GlobalContextState getGlobalContext() {
		return stateService.getGlobalContext();
	}

	@Override
	public void saveGlobalContextVariables(Map<String, Object> values) {
		stateService.saveGlobalContextVariables(values);
	}
}
