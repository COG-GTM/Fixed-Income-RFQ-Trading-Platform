package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.InsufficientNotionalException;
import com.javieraviles.splitthemonolith.exception.InvalidAmountException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.saga.RFQExecutionSaga;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Edge-case coverage for {@link RFQExecutionSaga}. Test methods are intentionally
 * non-transactional so the saga runs in its own transaction; re-fetching entities
 * afterwards reflects committed (or rolled-back) state.
 *
 * <p>Shares the same context configuration as {@code IntegrationTest} so Spring
 * reuses a single cached application context and the seed {@code CommandLineRunner}
 * runs only once against the shared in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class RFQExecutionSagaTest {

	@Autowired
	private RFQExecutionSaga saga;

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	private Bond newBond(final String isin, final BigDecimal availableNotional) {
		return bondRepository.save(new Bond(isin, "US Treasury", new BigDecimal("2.5000"),
				LocalDate.of(2032, 5, 15), availableNotional));
	}

	private Counterparty newCounterparty(final String lei, final BigDecimal creditLimit) {
		return counterpartyRepository.save(new Counterparty("Test Fund", lei, creditLimit));
	}

	private RfqDto rfqDto(final long counterpartyId, final long bondId,
			final BigDecimal notional, final BigDecimal price) {
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(counterpartyId);
		dto.setBondId(bondId);
		dto.setNotionalAmount(notional);
		dto.setExecutionPrice(price);
		dto.setSide(Side.BUY);
		return dto;
	}

	@Test
	public void happyPath_deductsNotionalAndCreditAndPersistsRfq() {
		final Bond bond = newBond("US0000000H01", new BigDecimal("10000000.00"));
		final Counterparty cp = newCounterparty("549300HAPPYPATH0001", new BigDecimal("10000000.00"));

		final Rfq rfq = saga.executeRfq(rfqDto(cp.getId(), bond.getId(),
				new BigDecimal("1000000.00"), new BigDecimal("998750.00")));

		assertEquals(RfqStatus.EXECUTED, rfq.getStatus());
		assertEquals(0, bondRepository.findById(bond.getId()).get().getAvailableNotional()
				.compareTo(new BigDecimal("9000000.00")));
		assertEquals(0, counterpartyRepository.findById(cp.getId()).get().getAvailableCredit()
				.compareTo(new BigDecimal("9001250.00")));
	}

	@Test
	public void insufficientNotional_throwsAndLeavesStateUnchanged() {
		final Bond bond = newBond("US0000000N01", new BigDecimal("1000000.00"));
		final Counterparty cp = newCounterparty("549300NOTIONAL00001", new BigDecimal("50000000.00"));

		assertThrows(InsufficientNotionalException.class, () -> saga.executeRfq(rfqDto(
				cp.getId(), bond.getId(), new BigDecimal("2000000.00"), new BigDecimal("1999000.00"))));

		assertEquals(0, bondRepository.findById(bond.getId()).get().getAvailableNotional()
				.compareTo(new BigDecimal("1000000.00")));
		assertEquals(0, counterpartyRepository.findById(cp.getId()).get().getAvailableCredit()
				.compareTo(new BigDecimal("50000000.00")));
	}

	@Test
	public void insufficientCredit_throws() {
		final Bond bond = newBond("US0000000C01", new BigDecimal("50000000.00"));
		final Counterparty cp = newCounterparty("549300CREDIT0000001", new BigDecimal("500000.00"));

		assertThrows(InsufficientCreditException.class, () -> saga.executeRfq(rfqDto(
				cp.getId(), bond.getId(), new BigDecimal("1000000.00"), new BigDecimal("600000.00"))));
	}

	@Test
	public void rollbackOnFailure_notionalDeductionRolledBackWhenCreditFails() {
		// Bond has enough notional, but the counterparty cannot cover the price.
		final Bond bond = newBond("US0000000R01", new BigDecimal("5000000.00"));
		final Counterparty cp = newCounterparty("549300ROLLBACK00001", new BigDecimal("100000.00"));

		assertThrows(InsufficientCreditException.class, () -> saga.executeRfq(rfqDto(
				cp.getId(), bond.getId(), new BigDecimal("1000000.00"), new BigDecimal("200000.00"))));

		// The notional deduction that ran before the credit failure must be rolled back.
		assertEquals(0, bondRepository.findById(bond.getId()).get().getAvailableNotional()
				.compareTo(new BigDecimal("5000000.00")));
		assertEquals(0, counterpartyRepository.findById(cp.getId()).get().getAvailableCredit()
				.compareTo(new BigDecimal("100000.00")));
	}

	@Test
	public void monetaryMath_usesBigDecimalWithExplicitRounding() {
		final Bond bond = newBond("US0000000D01", new BigDecimal("1000000.00"));
		final Counterparty cp = newCounterparty("549300ROUNDING00001", new BigDecimal("1000000.00"));

		// Sub-cent inputs must be rounded HALF_UP to whole cents before settling.
		final Rfq rfq = saga.executeRfq(rfqDto(cp.getId(), bond.getId(),
				new BigDecimal("200.005"), new BigDecimal("100.125")));

		assertEquals(2, rfq.getExecutionPrice().scale());
		assertEquals(0, rfq.getExecutionPrice().compareTo(new BigDecimal("100.13")));
		assertEquals(0, rfq.getNotionalAmount().compareTo(new BigDecimal("200.01")));
		assertEquals(0, counterpartyRepository.findById(cp.getId()).get().getAvailableCredit()
				.compareTo(new BigDecimal("999899.87")));
		assertEquals(0, bondRepository.findById(bond.getId()).get().getAvailableNotional()
				.compareTo(new BigDecimal("999799.99")));
	}

	@Test
	public void zeroQuantity_throwsInvalidAmount() {
		final Bond bond = newBond("US0000000Z01", new BigDecimal("1000000.00"));
		final Counterparty cp = newCounterparty("549300ZEROQTY000001", new BigDecimal("1000000.00"));

		assertThrows(InvalidAmountException.class, () -> saga.executeRfq(rfqDto(
				cp.getId(), bond.getId(), BigDecimal.ZERO, new BigDecimal("100.00"))));
	}

	@Test
	public void negativeQuantity_throwsInvalidAmountAndDoesNotIncreaseInventory() {
		final Bond bond = newBond("US0000000Z02", new BigDecimal("1000000.00"));
		final Counterparty cp = newCounterparty("549300NEGQTY0000001", new BigDecimal("1000000.00"));

		assertThrows(InvalidAmountException.class, () -> saga.executeRfq(rfqDto(
				cp.getId(), bond.getId(), new BigDecimal("-1000.00"), new BigDecimal("100.00"))));

		// A negative quantity must never inflate available notional.
		assertEquals(0, bondRepository.findById(bond.getId()).get().getAvailableNotional()
				.compareTo(new BigDecimal("1000000.00")));
	}

	@Test
	public void unknownBond_throwsResourceNotFound() {
		final Counterparty cp = newCounterparty("549300UNKNOWNBOND01", new BigDecimal("1000000.00"));

		assertThrows(ResourceNotFoundException.class, () -> saga.executeRfq(rfqDto(
				cp.getId(), 999999L, new BigDecimal("1000.00"), new BigDecimal("100.00"))));
	}

	@Test
	public void unknownCounterparty_throwsResourceNotFound() {
		final Bond bond = newBond("US0000000U01", new BigDecimal("1000000.00"));

		assertThrows(ResourceNotFoundException.class, () -> saga.executeRfq(rfqDto(
				999999L, bond.getId(), new BigDecimal("1000.00"), new BigDecimal("100.00"))));
	}
}
