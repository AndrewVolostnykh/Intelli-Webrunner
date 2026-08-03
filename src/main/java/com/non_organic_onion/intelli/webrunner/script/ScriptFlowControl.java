package com.non_organic_onion.intelli.webrunner.script;

public final class ScriptFlowControl {
	public enum Action {
		PROCEED,
		SKIP,
		INTERRUPT
	}

	private Action action = Action.PROCEED;
	private String message = "";

	public Action action() {
		return action;
	}

	public String message() {
		return message;
	}

	public void proceed() {
		action = Action.PROCEED;
		message = "";
	}

	public void skip(String message) {
		action = Action.SKIP;
		this.message = message == null ? "" : message;
	}

	public void interrupt(String message) {
		action = Action.INTERRUPT;
		this.message = message == null ? "" : message;
	}

	public boolean skipped() {
		return action == Action.SKIP;
	}

	public boolean interrupted() {
		return action == Action.INTERRUPT;
	}
}
