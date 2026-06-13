package com.intelli.webrunner.util;

import com.intelli.webrunner.state.FormEntryState;
import com.intelli.webrunner.state.HeaderEntryState;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep-copy helpers for request entry state, used to avoid sharing mutable
 * {@link HeaderEntryState}/{@link FormEntryState} instances between request executions.
 */
public final class StateCopyUtils {

	private StateCopyUtils() {
	}

	public static List<HeaderEntryState> cloneHeaders(List<HeaderEntryState> headers) {
		List<HeaderEntryState> copy = new ArrayList<>();
		if (headers == null) {
			return copy;
		}
		for (HeaderEntryState entry : headers) {
			HeaderEntryState clone = new HeaderEntryState();
			clone.id = entry.id;
			clone.name = entry.name;
			clone.value = entry.value;
			clone.enabled = entry.enabled;
			copy.add(clone);
		}
		return copy;
	}

	public static List<FormEntryState> cloneFormData(List<FormEntryState> entries) {
		List<FormEntryState> copy = new ArrayList<>();
		if (entries == null) {
			return copy;
		}
		for (FormEntryState entry : entries) {
			FormEntryState clone = new FormEntryState();
			clone.id = entry.id;
			clone.name = entry.name;
			clone.value = entry.value;
			clone.enabled = entry.enabled;
			clone.file = entry.file;
			copy.add(clone);
		}
		return copy;
	}
}
