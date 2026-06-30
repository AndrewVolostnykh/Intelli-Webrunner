package com.intelli.webrunner.execution;

public record HttpStressConfig(
	boolean enabled,
	double requestsPerSecond,
	long totalDurationMillis,
	int numberOfRequests,
	int parallelWorkers,
	long rampUpMillis,
	long delayBetweenRequestsMillis,
	double jitterFromSeconds,
	double jitterToSeconds
) {
	public static HttpStressConfig disabled() {
		return new HttpStressConfig(false, 0, 0, 0, 1, 0, 0, 0, 0);
	}

	public boolean hasLimit() {
		return totalDurationMillis > 0 || numberOfRequests > 0;
	}
}
