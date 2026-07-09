package com.javieraviles.splitthemonolith.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Lightweight Server-Sent-Events fallback endpoint used by the demo page to
 * visualise live price movement without depending on the WebSocket/STOMP
 * tickets. This controller is entirely self-contained: it drives its own
 * simulated price ticker so the demo shows movement immediately.
 */
@RestController
class StreamSseController {

	private static final long NO_TIMEOUT = 0L;

	private static final String[] ISINS = {
			"US912828U816", "DE0001102408", "FR0013451507", "GB00BMBL1D50", "IT0005425233"
	};

	private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "sse-price-ticker");
		thread.setDaemon(true);
		return thread;
	});

	@GetMapping(value = "/stream/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter stream() {
		final SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> {
			emitter.complete();
			emitters.remove(emitter);
		});
		emitter.onError(throwable -> emitters.remove(emitter));
		emitters.add(emitter);
		return emitter;
	}

	@PostConstruct
	void startTicker() {
		scheduler.scheduleAtFixedRate(this::broadcastTick, 0L, 2L, TimeUnit.SECONDS);
	}

	@PreDestroy
	void stopTicker() {
		scheduler.shutdownNow();
	}

	private void broadcastTick() {
		if (emitters.isEmpty()) {
			return;
		}
		final String payload = buildTick();
		for (final SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name("price").data(payload, MediaType.APPLICATION_JSON));
			} catch (final IOException | RuntimeException e) {
				emitter.complete();
				emitters.remove(emitter);
			}
		}
	}

	private String buildTick() {
		final String isin = ISINS[ThreadLocalRandom.current().nextInt(ISINS.length)];
		final BigDecimal price = BigDecimal.valueOf(90d + ThreadLocalRandom.current().nextDouble() * 20d)
				.setScale(3, RoundingMode.HALF_UP);
		final BigDecimal change = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble() * 0.5d - 0.25d)
				.setScale(3, RoundingMode.HALF_UP);
		return String.format(
				"{\"isin\":\"%s\",\"price\":%s,\"change\":%s,\"timestamp\":%d}",
				isin, price.toPlainString(), change.toPlainString(), System.currentTimeMillis());
	}
}
