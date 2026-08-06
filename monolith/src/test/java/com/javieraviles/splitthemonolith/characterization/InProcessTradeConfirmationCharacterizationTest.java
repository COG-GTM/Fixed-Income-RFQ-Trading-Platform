package com.javieraviles.splitthemonolith.characterization;

import static com.javieraviles.splitthemonolith.characterization.CharacterizationSupport.bodyOf;
import static com.javieraviles.splitthemonolith.characterization.CharacterizationSupport.createCounterparty;
import static com.javieraviles.splitthemonolith.characterization.CharacterizationSupport.creditPatch;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

/**
 * Characterization tests for the {@code use.confirmation.service=false} branch:
 * trade confirmations are handled in-process and only produce a log line. The
 * confirmation microservice must not be contacted.
 */
@SpringBootTest(properties = { "use.confirmation.service=false",
		"spring.datasource.generate-unique-name=true" })
@AutoConfigureMockMvc
public class InProcessTradeConfirmationCharacterizationTest {

	private static final String CONFIRMATION_LOG_PREFIX = "Trade confirmation: counterparty ";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RestTemplate restTemplate;

	private ListAppender<ILoggingEvent> logAppender;

	private MockRestServiceServer confirmationMs;

	@BeforeEach
	public void captureLogsAndOutboundCalls() {
		logAppender = new ListAppender<>();
		logAppender.start();
		rootLogger().addAppender(logAppender);
		confirmationMs = MockRestServiceServer.bindTo(restTemplate).build();
	}

	@AfterEach
	public void releaseLogAppender() {
		rootLogger().detachAppender(logAppender);
		logAppender.stop();
	}

	@Test
	@DisplayName("Adding credit logs the confirmation in-process and never calls the confirmation microservice")
	public void addingCredit_confirmsInProcess() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "In Process Confirm Fund",
				"549300INPROCESS00001", "5000000.00");

		mvc.perform(patch("/counterparties/" + counterpartyId).content(creditPatch("ADD", "250000.00"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());

		assertTrue(loggedConfirmation("In Process Confirm Fund", "250000.00"),
				"expected an in-process confirmation log line, got: " + loggedConfirmations());
		confirmationMs.verify();
		assertAvailableCredit(counterpartyId, "5250000.00");
	}

	@Test
	@DisplayName("Deducting credit sends no confirmation at all")
	public void deductingCredit_sendsNoConfirmation() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "In Process Deduct Fund",
				"549300INPROCDEDUCT01", "5000000.00");

		mvc.perform(patch("/counterparties/" + counterpartyId).content(creditPatch("DEDUCT", "250000.00"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());

		assertEquals(0, loggedConfirmations().size(), "DEDUCT must not emit a confirmation");
		confirmationMs.verify();
		assertAvailableCredit(counterpartyId, "4750000.00");
	}

	@Test
	@DisplayName("Deducting more than the available credit is rejected with 400 and sends no confirmation")
	public void deductingBeyondAvailableCredit_isRejected() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "In Process Limit Fund",
				"549300INPROCLIMIT001", "100000.00");

		mvc.perform(patch("/counterparties/" + counterpartyId).content(creditPatch("DEDUCT", "250000.00"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient credit")));

		assertEquals(0, loggedConfirmations().size());
		assertAvailableCredit(counterpartyId, "100000.00");
	}

	@Test
	@DisplayName("An unknown operation is rejected with 'wrong operation' and sends no confirmation")
	public void unknownOperation_isRejected() throws Exception {
		final long counterpartyId = createCounterparty(mvc, "In Process Bad Op Fund",
				"549300INPROCBADOP001", "100000.00");

		mvc.perform(patch("/counterparties/" + counterpartyId).content(creditPatch("INCREASE", "250000.00"))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("wrong operation"));

		assertEquals(0, loggedConfirmations().size());
		assertAvailableCredit(counterpartyId, "100000.00");
	}

	private boolean loggedConfirmation(final String counterpartyName, final String amount) {
		final String expected = CONFIRMATION_LOG_PREFIX + counterpartyName + " credit updated, amount " + amount;
		return loggedConfirmations().stream().anyMatch(expected::equals);
	}

	private java.util.List<String> loggedConfirmations() {
		return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
				.filter(message -> message.startsWith(CONFIRMATION_LOG_PREFIX))
				.collect(java.util.stream.Collectors.toList());
	}

	private void assertAvailableCredit(final long counterpartyId, final String expected) throws Exception {
		final BigDecimal actual = bodyOf(mvc.perform(get("/counterparties/" + counterpartyId))
				.andExpect(status().isOk()).andReturn()).get("availableCredit").decimalValue();
		assertEquals(0, new BigDecimal(expected).compareTo(actual),
				() -> "availableCredit expected " + expected + " but was " + actual);
	}

	private static Logger rootLogger() {
		return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
	}
}
