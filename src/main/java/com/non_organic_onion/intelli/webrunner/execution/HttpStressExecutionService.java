package com.non_organic_onion.intelli.webrunner.execution;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class HttpStressExecutionService {

	private final RequestExecutionService executionService;
	private final ObjectMapper mapper = new ObjectMapper();

	public HttpStressExecutionService(RequestExecutionService executionService) {
		this.executionService = executionService;
	}

	public ExecutionResult execute(
		HttpStressRequest request,
		HttpStressConfig config
	) {
		long startedAt = System.nanoTime();
		int workers = Math.max(1, config.parallelWorkers());
		int limit = config.numberOfRequests() > 0 ? config.numberOfRequests() : Integer.MAX_VALUE;
		long deadline = config.totalDurationMillis() > 0
			? System.currentTimeMillis() + config.totalDurationMillis()
			: Long.MAX_VALUE;
		ExecutorService executor = Executors.newFixedThreadPool(workers);
		CompletionService<ExecutionResult> completionService = new ExecutorCompletionService<>(executor);
		StressCounters counters = new StressCounters();

		try {
			long nextStartAt = System.currentTimeMillis();
			while (!Thread.currentThread().isInterrupted() && counters.flowStatus.isBlank() &&
				counters.scheduled.get() < limit && System.currentTimeMillis() < deadline) {
				long now = System.currentTimeMillis();
				if (now < nextStartAt) {
					sleepInterruptibly(Math.min(nextStartAt - now, 100));
					continue;
				}
				int requestIndex = counters.scheduled.incrementAndGet();
				long rampDelay = rampDelayMillis(config.rampUpMillis(), requestIndex, workers);
				completionService.submit(() -> executeOne(request, rampDelay));
				nextStartAt = now + intervalMillis(config) + jitterMillis(config);
				drainCompleted(completionService, counters);
			}
			while (!Thread.currentThread().isInterrupted() && counters.flowStatus.isBlank() &&
				counters.completed.get() < counters.scheduled.get()) {
				Future<ExecutionResult> future = completionService.poll(100, TimeUnit.MILLISECONDS);
				if (future == null) {
					continue;
				}
				collectStressResult(future, counters);
			}
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
		} finally {
			executor.shutdownNow();
		}

		if (Thread.currentThread().isInterrupted()) {
			return null;
		}
		long durationMillis = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
		return buildStressResult(counters, durationMillis);
	}

	private ExecutionResult executeOne(
		HttpStressRequest request,
		long rampDelay
	) throws InterruptedException {
		if (rampDelay > 0) {
			sleepInterruptibly(rampDelay);
		}
		return executionService.executeWithScripts(
			request.method(),
			request.url(),
			request.headers(),
			request.params(),
			request.body(),
			request.before(),
			request.after(),
			false,
			null,
			request.chainContext(),
			request.chainRequests(),
			request.payloadType(),
			request.formData(),
			request.binaryFilePath(),
			request.timeoutMillis()
		);
	}

	private void drainCompleted(
		CompletionService<ExecutionResult> completionService,
		StressCounters counters
	) {
		while (true) {
			Future<ExecutionResult> future = completionService.poll();
			if (future == null) {
				return;
			}
			collectStressResult(future, counters);
		}
	}

	private ExecutionResult collectStressResult(
		Future<ExecutionResult> future,
		StressCounters counters
	) {
		counters.completed.incrementAndGet();
		try {
			ExecutionResult result = future.get();
			if (result.flowStatus != null && !result.flowStatus.isBlank()) {
				counters.flowStatus = result.flowStatus;
			}
			if (result.statusCode >= 200 && result.statusCode < 400) {
				counters.successful.incrementAndGet();
			} else {
				counters.failed.incrementAndGet();
			}
			counters.statusCounts.merge(result.statusCode, 1, Integer::sum);
			if (result.durationMillis >= 0) {
				counters.minDuration.accumulateAndGet(result.durationMillis, Math::min);
				counters.maxDuration.accumulateAndGet(result.durationMillis, Math::max);
				counters.totalDuration.addAndGet(result.durationMillis);
			}
			return result;
		} catch (Exception error) {
			counters.failed.incrementAndGet();
			return null;
		}
	}

	private ExecutionResult buildStressResult(
		StressCounters counters,
		long wallClockDuration
	) {
		int completed = counters.completed.get();
		int failed = counters.failed.get();
		long averageDuration = completed == 0 ? 0 : counters.totalDuration.get() / completed;
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("testDurationMillis", wallClockDuration);
		body.put("totalSent", counters.scheduled.get());
		body.put("totalCompleted", completed);
		body.put("statusCounts", new TreeMap<>(counters.statusCounts));
		body.put("avgLatencyMillis", averageDuration);
		body.put("minLatencyMillis", counters.minDuration.get() == Long.MAX_VALUE ? 0 : counters.minDuration.get());
		body.put("maxLatencyMillis", counters.maxDuration.get());
		body.put("successfulRequests", counters.successful.get());
		body.put("failedRequests", failed);
		String logs = "Stress test finished. Completed: " + completed + ", successful: " +
			counters.successful.get() + ", failed: " + failed;
		return new ExecutionResult(
			failed == 0 ? 200 : 0,
			failed == 0 ? "OK" : "Stress failures",
			toPrettyJson(body),
			"{}",
			"",
			logs,
			wallClockDuration,
			"",
			"",
			"",
			counters.flowStatus
		);
	}

	private long intervalMillis(HttpStressConfig config) {
		if (config.delayBetweenRequestsMillis() > 0) {
			return config.delayBetweenRequestsMillis();
		}
		if (config.requestsPerSecond() > 0) {
			return Math.max(1, Math.round(1000.0 / config.requestsPerSecond()));
		}
		return 0;
	}

	private long jitterMillis(HttpStressConfig config) {
		double from = Math.min(config.jitterFromSeconds(), config.jitterToSeconds());
		double to = Math.max(config.jitterFromSeconds(), config.jitterToSeconds());
		if (to <= 0) {
			return 0;
		}
		return Math.round(ThreadLocalRandom.current().nextDouble(from, to + 0.000_001) * 1000);
	}

	private long rampDelayMillis(
		long rampUpMillis,
		int requestIndex,
		int workers
	) {
		if (rampUpMillis <= 0 || workers <= 1) {
			return 0;
		}
		int workerSlot = Math.floorMod(requestIndex - 1, workers);
		return Math.round((rampUpMillis / (double) workers) * workerSlot);
	}

	private void sleepInterruptibly(long millis) throws InterruptedException {
		if (millis > 0) {
			Thread.sleep(millis);
		}
	}

	private String toPrettyJson(Object value) {
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
		} catch (Exception error) {
			return String.valueOf(value);
		}
	}

	private static final class StressCounters {
		private final AtomicInteger scheduled = new AtomicInteger();
		private final AtomicInteger completed = new AtomicInteger();
		private final AtomicInteger successful = new AtomicInteger();
		private final AtomicInteger failed = new AtomicInteger();
		private final AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);
		private final AtomicLong maxDuration = new AtomicLong();
		private final AtomicLong totalDuration = new AtomicLong();
		private final Map<Integer, Integer> statusCounts = new LinkedHashMap<>();
		private volatile String flowStatus = "";
	}
}
