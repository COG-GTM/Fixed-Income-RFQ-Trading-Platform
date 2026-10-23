package com.javieraviles.splitthemonolith.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.InsufficientNotionalException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

/**
 * Runs the saga against the real H2 database and transaction manager, so a
 * rejected quote must leave no RFQ behind and roll back any partial deduction.
 * Uses its own in-memory database so the seed data from the application
 * runner does not collide with other test contexts.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:rfq-saga-it;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RFQExecutionSagaIntegrationTest {

	private static final long UNKNOWN_ID = 999_999L;
	private static final AtomicInteger ISIN_SEQUENCE = new AtomicInteger();

	@Autowired
	private RFQExecutionSaga saga;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	private Counterparty counterparty;
	private Bond bond;
	private long rfqCountBefore;

	@BeforeEach
	void setUp() {
		counterparty = counterpartyRepository
				.save(new Counterparty("Harbor Point Advisors", "549300HARBORPOINT0001", new BigDecimal("3000000.00")));
		bond = bondRepository.save(new Bond(nextIsin(), "US Treasury", new BigDecimal("3.2500"),
				LocalDate.of(2034, 2, 15), new BigDecimal("4000000.00")));
		rfqCountBefore = rfqRepository.count();
	}

	@ParameterizedTest
	@EnumSource(Side.class)
	void executedRfqIsPersistedAndBalancesAreUpdated(final Side side) {
		final Rfq result = saga.executeRfq(rfq(counterparty.getId(), bond.getId(), side, "1500000.00", "1497000.00"));

		final Rfq stored = rfqRepository.findById(result.getId()).orElseThrow(AssertionError::new);
		assertThat(stored.getStatus()).isEqualTo(RfqStatus.EXECUTED);
		assertThat(stored.getSide()).isEqualTo(side);
		assertThat(stored.getCreatedAt()).isNotNull();
		assertThat(stored.getCounterparty().getId()).isEqualTo(counterparty.getId());
		assertThat(stored.getBond().getId()).isEqualTo(bond.getId());
		assertThat(rfqRepository.count()).isEqualTo(rfqCountBefore + 1);
		assertBalances("2500000.00", "1503000.00");
	}

	@Test
	void rejectedQuoteForInsufficientCredit_rollsBackNotionalDeduction() {
		assertThatThrownBy(() -> saga
				.executeRfq(rfq(counterparty.getId(), bond.getId(), Side.BUY, "1000000.00", "3000000.01")))
				.isInstanceOf(InsufficientCreditException.class);

		assertNoRfqPersisted();
		assertBalances("4000000.00", "3000000.00");
	}

	@Test
	void rejectedQuoteForInsufficientNotional_leavesBalancesUntouched() {
		assertThatThrownBy(() -> saga
				.executeRfq(rfq(counterparty.getId(), bond.getId(), Side.SELL, "4000000.01", "100000.00")))
				.isInstanceOf(InsufficientNotionalException.class);

		assertNoRfqPersisted();
		assertBalances("4000000.00", "3000000.00");
	}

	@Test
	void rejectedQuoteForUnknownCounterparty_leavesBondUntouched() {
		assertThatThrownBy(() -> saga.executeRfq(rfq(UNKNOWN_ID, bond.getId(), Side.BUY, "1000000.00", "998000.00")))
				.isInstanceOf(ResourceNotFoundException.class);

		assertNoRfqPersisted();
		assertBalances("4000000.00", "3000000.00");
	}

	@Test
	void rejectedQuoteForUnknownBond_leavesCounterpartyUntouched() {
		assertThatThrownBy(
				() -> saga.executeRfq(rfq(counterparty.getId(), UNKNOWN_ID, Side.BUY, "1000000.00", "998000.00")))
				.isInstanceOf(ResourceNotFoundException.class);

		assertNoRfqPersisted();
		assertBalances("4000000.00", "3000000.00");
	}

	@Test
	void rejectedQuoteDoesNotBlockNextValidQuote() {
		assertThatThrownBy(() -> saga
				.executeRfq(rfq(counterparty.getId(), bond.getId(), Side.BUY, "1000000.00", "3000000.01")))
				.isInstanceOf(InsufficientCreditException.class);

		saga.executeRfq(rfq(counterparty.getId(), bond.getId(), Side.BUY, "1000000.00", "999000.00"));

		assertThat(rfqRepository.count()).isEqualTo(rfqCountBefore + 1);
		assertBalances("3000000.00", "2001000.00");
	}

	@Test
	void counterpartyCreatedWithOnlyCreditLimit_canTradeAgainstThatLimit() {
		final Counterparty limitOnly = new Counterparty();
		limitOnly.setName("Bayview Pension Trust");
		limitOnly.setLei("549300BAYVIEW000000001");
		limitOnly.setCreditLimit(new BigDecimal("2000000.00"));
		final long limitOnlyId = counterpartyRepository.save(limitOnly).getId();

		saga.executeRfq(rfq(limitOnlyId, bond.getId(), Side.BUY, "500000.00", "499000.00"));

		assertThat(counterpartyRepository.findById(limitOnlyId).orElseThrow(AssertionError::new).getAvailableCredit())
				.isEqualByComparingTo("1501000.00");
	}

	@Test
	void executedRfqIsReadableAndDeletableThroughTheApi() throws Exception {
		final RfqDto request = rfq(counterparty.getId(), bond.getId(), Side.SELL, "750000.00", "748500.00");
		final String body = mvc
				.perform(post("/rfqs").content(objectMapper.writeValueAsString(request))
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status", is("EXECUTED")))
				.andReturn().getResponse().getContentAsString();
		final long rfqId = objectMapper.readTree(body).get("id").asLong();

		mvc.perform(get("/rfqs")).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem((int) rfqId)));
		mvc.perform(get("/rfqs/{id}", rfqId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.side", is("SELL")))
				.andExpect(jsonPath("$.counterpartyId", is((int) counterparty.getId())))
				.andExpect(jsonPath("$.bondId", is((int) bond.getId())));

		mvc.perform(delete("/rfqs/{id}", rfqId)).andExpect(status().isOk());
		mvc.perform(get("/rfqs/{id}", rfqId)).andExpect(status().isNotFound());
		assertNoRfqPersisted();
	}

	@Test
	void rejectedQuoteThroughTheApi_returnsBadRequestAndPersistsNothing() throws Exception {
		final RfqDto request = rfq(counterparty.getId(), bond.getId(), Side.BUY, "1000000.00", "3000000.01");

		mvc.perform(post("/rfqs").content(objectMapper.writeValueAsString(request))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());

		assertNoRfqPersisted();
		assertBalances("4000000.00", "3000000.00");
	}

	private void assertNoRfqPersisted() {
		assertThat(rfqRepository.count()).isEqualTo(rfqCountBefore);
	}

	private void assertBalances(final String expectedNotional, final String expectedCredit) {
		assertThat(bondRepository.findById(bond.getId()).orElseThrow(AssertionError::new).getAvailableNotional())
				.isEqualByComparingTo(expectedNotional);
		assertThat(counterpartyRepository.findById(counterparty.getId()).orElseThrow(AssertionError::new)
				.getAvailableCredit()).isEqualByComparingTo(expectedCredit);
	}

	private static String nextIsin() {
		return String.format("XS%010d", ISIN_SEQUENCE.incrementAndGet());
	}

	private static RfqDto rfq(final long counterpartyId, final long bondId, final Side side, final String notional,
			final String executionPrice) {
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(counterpartyId);
		dto.setBondId(bondId);
		dto.setSide(side);
		dto.setNotionalAmount(new BigDecimal(notional));
		dto.setExecutionPrice(new BigDecimal(executionPrice));
		return dto;
	}
}
