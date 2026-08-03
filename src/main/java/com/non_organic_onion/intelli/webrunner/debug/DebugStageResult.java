package com.non_organic_onion.intelli.webrunner.debug;

import java.util.List;

/**
 * The rendered output of a single step in a {@link DebugCallSession}.
 */
final class DebugStageResult {

	final String stageName;
	final long durationNanos;
	final List<String> lines;
	final boolean hasNext;

	DebugStageResult(
		String stageName,
		long durationNanos,
		List<String> lines,
		boolean hasNext
	) {
		this.stageName = stageName;
		this.durationNanos = durationNanos;
		this.lines = lines;
		this.hasNext = hasNext;
	}
}
