package com.javieraviles.splitthemonolith.saga;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * Exercises the saga against the real JPA/H2 stack so the @Transactional
 * rollback (no partial notional or credit deduction on rejection) is verified.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:rfq-saga-regression;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RFQExecutionSagaIntegrationTest {

	private static final BigDecimal CREDIT_LIMIT = new BigDecimal("1000000.00");
	private static final BigDecimal INVENTORY = new BigDecimal("5000000.00");
	private static final long UNKNOWN_ID = 987654321L;
	private static final AtomicInteger SEQ = new AtomicInteger();
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	@Autowired
	private RFQExecutionSaga saga;

	@Autowired
	private RfqRepository rfqRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private MockMvc mvc;

	private Counterparty counterparty;
	private Bond bond;

	@BeforeEach
	void setUp() {
		final int n = SEQ.incrementAndGet();
		counterparty = counterpartyRepository.save(
				new Counterparty("Regression Fund " + n, String.format("549300REGR%010d", n), CREDIT_LIMIT));
		bond = bondRepository.save(new Bond(String.format("XS%010d", n), "Regression Issuer",
				new BigDecimal("4.2500"), LocalDate.of(2035, 6, 30), INVENTORY));
	}

	@ParameterizedTest
	@EnumSource(Side.class)
	void executedRfqIsPersisted_andBalancesAreCommitted(final Side side) {
		final long before = rfqRepository.count();

		final Rfq executed = saga.executeRfq(rfq(counterparty.getId(), bond.getId(), side, "2500000.00", "750000.00"));

		final Rfq stored = rfqRepository.findById(executed.getId()).orElseThrow(AssertionError::new);
		assertAll(
				() -> assertEquals(before + 1, rfqRepository.count()),
				() -> assertEquals(RfqStatus.EXECUTED, stored.getStatus()),
				() -> assertEquals(side, stored.getSide()),
				() -> assertEquals(counterparty.getId(), stored.getCounterparty().getId()),
				() -> assertEquals(bond.getId(), stored.getBond().getId()),
				() -> assertEquals(0, new BigDecimal("2500000.00").compareTo(stored.getNotionalAmount())),
				() -> assertEquals(0, new BigDecimal("750000.00").compareTo(stored.getExecutionPrice())),
				() -> assertEquals(0, new BigDecimal("2500000.00").compareTo(availableNotional())),
				() -> assertEquals(0, new BigDecimal("250000.00").compareTo(availableCredit())));
	}

	@ParameterizedTest
	@EnumSource(Side.class)
	void postRfq_returnsCreatedExecutedRfq_retrievableById(final Side side) throws Exception {
		final String body = mvc
				.perform(post("/rfqs").contentType(MediaType.APPLICATION_JSON)
						.content(json(rfq(counterparty.getId(), bond.getId(), side, "1000000.00", "990000.00"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")))
				.andExpect(jsonPath("$.side", is(side.name())))
				.andReturn().getResponse().getContentAsString();
		final long id = MAPPER.readTree(body).get("id").asLong();

		mvc.perform(get("/rfqs/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.counterpartyId", is((int) counterparty.getId())))
				.andExpect(jsonPath("$.bondId", is((int) bond.getId())))
				.andExpect(jsonPath("$.status", is("EXECUTED")));
	}

	static Stream<Arguments> rejectedQuotes() {
		return Stream.of(
				Arguments.of("insufficient notional", Target.KNOWN, Target.KNOWN, "5000000.01", "100000.00",
						InsufficientNotionalException.class, status().isBadRequest()),
				Arguments.of("insufficient credit", Target.KNOWN, Target.KNOWN, "1000000.00", "1000000.01",
						InsufficientCreditException.class, status().isBadRequest()),
				Arguments.of("unknown counterparty", Target.UNKNOWN, Target.KNOWN, "1000000.00", "100000.00",
						ResourceNotFoundException.class, status().isNotFound()),
				Arguments.of("unknown bond", Target.KNOWN, Target.UNKNOWN, "1000000.00", "100000.00",
						ResourceNotFoundException.class, status().isNotFound()));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("rejectedQuotes")
	void rejectedQuote_throws_andLeavesNoRfqOrPartialBalanceChange(final String reason, final Target cp,
			final Target bd, final String notional, final String price,
			final Class<? extends RuntimeException> expected, final ResultMatcher httpStatus) {
		final long before = rfqRepository.count();

		assertThrows(expected, () -> saga.executeRfq(rfq(cp.id(counterparty.getId()), bd.id(bond.getId()),
				Side.BUY, notional, price)));

		assertNoSideEffects(before);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("rejectedQuotes")
	void rejectedQuote_overHttp_returnsErrorStatus_andLeavesNoSideEffects(final String reason, final Target cp,
			final Target bd, final String notional, final String price,
			final Class<? extends RuntimeException> expected, final ResultMatcher httpStatus) throws Exception {
		final long before = rfqRepository.count();

		mvc.perform(post("/rfqs").contentType(MediaType.APPLICATION_JSON)
				.content(json(rfq(cp.id(counterparty.getId()), bd.id(bond.getId()), Side.SELL, notional, price))))
				.andExpect(httpStatus);

		assertNoSideEffects(before);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("rejectedQuotes")
	void counterpartyCanStillTradeAfterRejectedQuote(final String reason, final Target cp, final Target bd,
			final String notional, final String price) {
		assertThrows(RuntimeException.class, () -> saga.executeRfq(
				rfq(cp.id(counterparty.getId()), bd.id(bond.getId()), Side.BUY, notional, price)));

		final Rfq executed = saga.executeRfq(rfq(counterparty.getId(), bond.getId(), Side.BUY, "5000000.00", "1000000.00"));

		assertEquals(RfqStatus.EXECUTED, executed.getStatus());
		assertEquals(0, BigDecimal.ZERO.compareTo(availableNotional()));
		assertEquals(0, BigDecimal.ZERO.compareTo(availableCredit()));
	}

	private void assertNoSideEffects(final long rfqCountBefore) {
		assertAll(
				() -> assertEquals(rfqCountBefore, rfqRepository.count(), "no RFQ row may be written"),
				() -> assertEquals(0, INVENTORY.compareTo(availableNotional()), "notional must be rolled back"),
				() -> assertEquals(0, CREDIT_LIMIT.compareTo(availableCredit()), "credit must be untouched"));
	}

	private BigDecimal availableNotional() {
		return bondRepository.findById(bond.getId()).orElseThrow(AssertionError::new).getAvailableNotional();
	}

	private BigDecimal availableCredit() {
		return counterpartyRepository.findById(counterparty.getId()).orElseThrow(AssertionError::new)
				.getAvailableCredit();
	}

	private static RfqDto rfq(final long cpId, final long bondId, final Side side, final String notional,
			final String price) {
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(cpId);
		dto.setBondId(bondId);
		dto.setSide(side);
		dto.setNotionalAmount(new BigDecimal(notional));
		dto.setExecutionPrice(new BigDecimal(price));
		return dto;
	}

	private static String json(final Object o) throws Exception {
		return MAPPER.writeValueAsString(o);
	}

	enum Target {
		KNOWN, UNKNOWN;

		long id(final long knownId) {
			return this == KNOWN ? knownId : UNKNOWN_ID;
		}
	}
}
