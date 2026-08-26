package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression coverage for the RFQ status lifecycle
 * (PENDING -> QUOTED -> EXECUTED / REJECTED) and its exposure through the API.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class RfqLifecycleTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Test
	public void givenPendingRfq_whenQuotedThenExecuted_thenStatusTransitionsArePersisted() throws Exception {
		final Rfq rfq = saveRfq(RfqStatus.PENDING);
		assertEquals(RfqStatus.PENDING, reload(rfq).getStatus());

		rfq.setStatus(RfqStatus.QUOTED);
		rfqRepository.save(rfq);
		assertEquals(RfqStatus.QUOTED, reload(rfq).getStatus());

		rfq.setStatus(RfqStatus.EXECUTED);
		rfqRepository.save(rfq);
		assertEquals(RfqStatus.EXECUTED, reload(rfq).getStatus());

		mvc.perform(get("/rfqs/" + rfq.getId()).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("EXECUTED")));
	}

	@Test
	public void givenQuotedRfq_whenRejected_thenStatusIsRejectedAndExposedByApi() throws Exception {
		final Rfq rfq = saveRfq(RfqStatus.QUOTED);

		rfq.setStatus(RfqStatus.REJECTED);
		rfqRepository.save(rfq);

		assertEquals(RfqStatus.REJECTED, reload(rfq).getStatus());
		mvc.perform(get("/rfqs/" + rfq.getId()).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", org.hamcrest.Matchers.is("REJECTED")))
				.andExpect(jsonPath("$.side", org.hamcrest.Matchers.is("BUY")));
	}

	@Test
	public void givenPersistedRfq_whenGetAll_thenRfqIsListed() throws Exception {
		final Rfq rfq = saveRfq(RfqStatus.EXECUTED);

		mvc.perform(get("/rfqs").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + rfq.getId() + ")].status",
						org.hamcrest.Matchers.contains("EXECUTED")));
	}

	@Test
	public void givenPersistedRfq_whenDeleted_thenNoLongerRetrievable() throws Exception {
		final Rfq rfq = saveRfq(RfqStatus.PENDING);
		assertTrue(rfqRepository.findById(rfq.getId()).isPresent());

		mvc.perform(delete("/rfqs/" + rfq.getId())).andExpect(status().isOk());

		assertFalse(rfqRepository.findById(rfq.getId()).isPresent());
		mvc.perform(get("/rfqs/" + rfq.getId()).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	public void givenUnknownRfqId_whenGetOne_thenNotFound() throws Exception {
		mvc.perform(get("/rfqs/424242").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	private Rfq saveRfq(final RfqStatus status) {
		final int seq = SEQUENCE.incrementAndGet();
		final Counterparty counterparty = counterpartyRepository.save(new Counterparty(
				"Lifecycle Counterparty " + seq, String.format("549300LIFE%010d", seq),
				new BigDecimal("10000000.00")));
		final Bond bond = bondRepository.save(new Bond(String.format("LIFE%08d", seq), "US Treasury",
				new BigDecimal("2.7500"), LocalDate.of(2031, 6, 30), new BigDecimal("20000000.00")));
		return rfqRepository.save(new Rfq(counterparty, bond, new BigDecimal("1000000.00"),
				Side.BUY, status, new BigDecimal("997500.00")));
	}

	private Rfq reload(final Rfq rfq) {
		return rfqRepository.findById(rfq.getId()).orElseThrow(IllegalStateException::new);
	}
}
