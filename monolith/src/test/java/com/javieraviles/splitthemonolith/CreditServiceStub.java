package com.javieraviles.splitthemonolith;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.splitthemonolith.dto.CounterpartyDto;
import com.javieraviles.splitthemonolith.dto.CreditReservationDto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * In-memory test double of the credit service, so the monolith tests exercise
 * the saga over real HTTP without starting the other Spring Boot application.
 * The behaviour of the real endpoints is covered by the credit-service module
 * tests; this stub only reproduces the contract the monolith depends on.
 */
class CreditServiceStub {

	/**
	 * Port the tests point {@code creditservice.url} to.
	 */
	static final int PORT = 8089;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final Map<Long, CounterpartyDto> counterparties = new ConcurrentHashMap<>();
	private final Map<Long, CreditReservationDto> reservations = new ConcurrentHashMap<>();
	private final AtomicLong counterpartyIds = new AtomicLong();
	private final AtomicLong reservationIds = new AtomicLong();

	private HttpServer server;

	void start(final int port) throws IOException {
		server = HttpServer.create(new InetSocketAddress(port), 0);
		server.createContext("/counterparties", this::handle);
		server.start();
		addCounterparty("Acme Asset Management", "549300EXAMPLE12345678", new BigDecimal("50000000.00"));
	}

	void stop() {
		server.stop(0);
	}

	CounterpartyDto addCounterparty(final String name, final String lei, final BigDecimal creditLimit) {
		final long id = counterpartyIds.incrementAndGet();
		final CounterpartyDto counterparty = new CounterpartyDto(id, name, lei, creditLimit, creditLimit);
		counterparties.put(id, counterparty);
		return counterparty;
	}

	CounterpartyDto getCounterparty(final long id) {
		return counterparties.get(id);
	}

	CreditReservationDto latestReservation() {
		return reservations.get(reservationIds.get());
	}

	private void handle(final HttpExchange exchange) throws IOException {
		final String[] segments = exchange.getRequestURI().getPath().replaceAll("^/|/$", "").split("/");
		final String method = exchange.getRequestMethod();
		// The request body is drained up front so the connection stays reusable.
		final byte[] requestBody = exchange.getRequestBody().readAllBytes();
		try {
			if (segments.length == 1 && "GET".equals(method)) {
				respond(exchange, 200, new ArrayList<>(counterparties.values()));
			} else if (segments.length == 1 && "POST".equals(method)) {
				final CounterpartyDto request = MAPPER.readValue(requestBody, CounterpartyDto.class);
				respond(exchange, 201, addCounterparty(request.getName(), request.getLei(), request.getCreditLimit()));
			} else if (segments.length == 2 && "GET".equals(method)) {
				respondWithCounterparty(exchange, Long.parseLong(segments[1]));
			} else if (segments.length == 3 && "POST".equals(method)) {
				reserve(exchange, Long.parseLong(segments[1]), requestBody);
			} else if (segments.length == 4 && "DELETE".equals(method)) {
				release(exchange, Long.parseLong(segments[3]));
			} else {
				respond(exchange, 405, null);
			}
		} catch (final NumberFormatException e) {
			respond(exchange, 404, null);
		}
	}

	private void respondWithCounterparty(final HttpExchange exchange, final long counterpartyId) throws IOException {
		final CounterpartyDto counterparty = counterparties.get(counterpartyId);
		if (counterparty == null) {
			respond(exchange, 404, null);
			return;
		}
		respond(exchange, 200, counterparty);
	}

	private void reserve(final HttpExchange exchange, final long counterpartyId, final byte[] requestBody)
			throws IOException {
		final CounterpartyDto counterparty = counterparties.get(counterpartyId);
		if (counterparty == null) {
			respond(exchange, 404, null);
			return;
		}
		final CreditReservationDto request = MAPPER.readValue(requestBody, CreditReservationDto.class);
		if (request.getAmount().compareTo(counterparty.getAvailableCredit()) > 0) {
			respond(exchange, 409, null);
			return;
		}
		counterparty.setAvailableCredit(counterparty.getAvailableCredit().subtract(request.getAmount()));
		final CreditReservationDto reservation = new CreditReservationDto(request.getAmount());
		reservation.setId(reservationIds.incrementAndGet());
		reservation.setCounterpartyId(counterpartyId);
		reservation.setStatus("RESERVED");
		reservations.put(reservation.getId(), reservation);
		respond(exchange, 201, reservation);
	}

	private void release(final HttpExchange exchange, final long reservationId) throws IOException {
		final CreditReservationDto reservation = reservations.get(reservationId);
		if (reservation == null) {
			respond(exchange, 404, null);
			return;
		}
		if ("RESERVED".equals(reservation.getStatus())) {
			final CounterpartyDto counterparty = counterparties.get(reservation.getCounterpartyId());
			counterparty.setAvailableCredit(counterparty.getAvailableCredit().add(reservation.getAmount()));
			reservation.setStatus("RELEASED");
		}
		respond(exchange, 204, null);
	}

	private void respond(final HttpExchange exchange, final int status, final Object body) throws IOException {
		// Keep-alive is disabled so the stub never leaves a pooled connection in
		// a state the client would reuse.
		exchange.getResponseHeaders().add("Connection", "close");
		if (body == null && status == 204) {
			exchange.sendResponseHeaders(status, -1);
			exchange.close();
			return;
		}
		// Errors carry a body so the response is framed by Content-Length, as
		// the real service does.
		final byte[] payload = MAPPER.writeValueAsBytes(body == null ? Map.of("status", status) : body);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, payload.length);
		exchange.getResponseBody().write(payload);
		exchange.close();
	}
}
