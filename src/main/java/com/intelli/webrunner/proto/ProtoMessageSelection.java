package com.intelli.webrunner.proto;

/**
 * A selectable proto message together with the registry needed to expand it into a sample body.
 * The display label is the only piece of state the UI layer needs to read.
 */
public final class ProtoMessageSelection {

	final String display;
	final ProtoRegistry registry;
	final ProtoMessage message;
	final String qualifiedName;

	ProtoMessageSelection(
		String display,
		ProtoRegistry registry,
		ProtoMessage message
	) {
		this.display = display;
		this.registry = registry;
		this.message = message;
		this.qualifiedName = message == null ? null : message.fullName;
	}

	public String getDisplay() {
		return display;
	}

	@Override
	public String toString() {
		return display;
	}
}
