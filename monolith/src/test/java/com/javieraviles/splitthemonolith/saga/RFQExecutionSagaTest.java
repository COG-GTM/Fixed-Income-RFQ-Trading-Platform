package com.javieraviles.splitthemonolith.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Regression coverage for the RFQ execution saga: happy path execution on both
 * sides, credit and inventory rejections, and transactional atomicity of the
 * inventory and credit legs.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:sagadb;DB_CLOSE_DELAY=-1")
public class RFQExecutionSagaTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private RFQExecutionSaga saga;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private RfqRepository rfqRepository;

	@Test
	public void givenSufficientCreditAndNotional_whenExecuteBuy_thenRfqExecutedAndBalancesDeducted() {
		final Counterparty counterparty = saveCounterparty("Vanguard Fixed Income", "25000000.00");
		final Bond bond = saveBond("40000000.00");

		final Rfq rfq = saga.executeRfq(dto(counterparty, bond, "1000000.00", "998750.00", Side.BUY));

		assertEquals(RfqStatus.EXECUTED, rfq.getStatus());
		assertEquals(Side.BUY, rfq.getSide());
		assertNotNull(rfq.getCreatedAt());
		assertEquals(0, new BigDecimal("39000000.00").compareTo(reload(bond).getAvailableNotional()));
		assertEquals(0, new BigDecimal("24001250.00").compareTo(reload(counterparty).getAvailableCredit()));
		assertTrue(rfqRepository.findById(rfq.getId()).isPresent());
	}

	@Test
	public void givenSufficientCreditAndNotional_whenExecuteSell_thenRfqExecutedAndBalancesDeducted() {
		final Counterparty counterparty = saveCounterparty("PIMCO Total Return", "12000000.00");
		final Bond bond = saveBond("8000000.00");

		final Rfq rfq = saga.executeRfq(dto(counterparty, bond, "2500000.00", "2493750.00", Side.SELL));

		assertEquals(RfqStatus.EXECUTED, rfq.getStatus());
		assertEquals(Side.SELL, rfq.getSide());
		assertEquals(0, new BigDecimal("5500000.00").compareTo(reload(bond).getAvailableNotional()));
		assertEquals(0, new BigDecimal("9506250.00").compareTo(reload(counterparty).getAvailableCredit()));
	}

	@Test
	public void givenExecutionPriceAboveCreditLimit_whenExecute_thenRejectedAndNothingPersisted() {
		final Counterparty counterparty = saveCounterparty("Thin Credit Partners", "1000000.00");
		final Bond bond = saveBond("50000000.00");
		final long rfqCountBefore = rfqRepository.count();

		assertThrows(InsufficientCreditException.class,
				() -> saga.executeRfq(dto(counterparty, bond, "5000000.00", "4993750.00", Side.BUY)));

		assertEquals(rfqCountBefore, rfqRepository.count());
		assertEquals(0, new BigDecimal("1000000.00").compareTo(reload(counterparty).getAvailableCredit()));
	}

	@Test
	public void givenNotionalAboveInventory_whenExecute_thenRejectedAndCreditUntouched() {
		final Counterparty counterparty = saveCounterparty("Deep Credit Advisors", "80000000.00");
		final Bond bond = saveBond("1500000.00");
		final long rfqCountBefore = rfqRepository.count();

		assertThrows(InsufficientNotionalException.class,
				() -> saga.executeRfq(dto(counterparty, bond, "2000000.00", "1996000.00", Side.BUY)));

		assertEquals(rfqCountBefore, rfqRepository.count());
		assertEquals(0, new BigDecimal("1500000.00").compareTo(reload(bond).getAvailableNotional()));
		assertEquals(0, new BigDecimal("80000000.00").compareTo(reload(counterparty).getAvailableCredit()));
	}

	/**
	 * The inventory leg succeeds before the credit leg fails; the surrounding
	 * transaction must roll the notional deduction back.
	 */
	@Test
	public void givenCreditLegFailsAfterInventoryLeg_whenExecute_thenInventoryRolledBack() {
		final Counterparty counterparty = saveCounterparty("Rollback Capital", "750000.00");
		final Bond bond = saveBond("10000000.00");

		assertThrows(InsufficientCreditException.class,
				() -> saga.executeRfq(dto(counterparty, bond, "3000000.00", "2995000.00", Side.BUY)));

		assertEquals(0, new BigDecimal("10000000.00").compareTo(reload(bond).getAvailableNotional()));
		assertEquals(0, new BigDecimal("750000.00").compareTo(reload(counterparty).getAvailableCredit()));
	}

	@Test
	public void givenExactlyAvailableNotionalAndCredit_whenExecute_thenExecutedAndBalancesZeroed() {
		final Counterparty counterparty = saveCounterparty("Exact Fill Fund", "4987500.00");
		final Bond bond = saveBond("5000000.00");

		final Rfq rfq = saga.executeRfq(dto(counterparty, bond, "5000000.00", "4987500.00", Side.BUY));

		assertEquals(RfqStatus.EXECUTED, rfq.getStatus());
		assertEquals(0, BigDecimal.ZERO.compareTo(reload(bond).getAvailableNotional()));
		assertEquals(0, BigDecimal.ZERO.compareTo(reload(counterparty).getAvailableCredit()));
	}

	@Test
	public void givenSequentialExecutions_whenSecondExceedsRemainingInventory_thenOnlyFirstPersists() {
		final Counterparty counterparty = saveCounterparty("Sequential Trading Co", "60000000.00");
		final Bond bond = saveBond("6000000.00");

		saga.executeRfq(dto(counterparty, bond, "4000000.00", "3990000.00", Side.BUY));
		final long rfqCountAfterFirst = rfqRepository.count();

		assertThrows(InsufficientNotionalException.class,
				() -> saga.executeRfq(dto(counterparty, bond, "3000000.00", "2992500.00", Side.SELL)));

		assertEquals(rfqCountAfterFirst, rfqRepository.count());
		assertEquals(0, new BigDecimal("2000000.00").compareTo(reload(bond).getAvailableNotional()));
		assertEquals(0, new BigDecimal("56010000.00").compareTo(reload(counterparty).getAvailableCredit()));
	}

	@Test
	public void givenUnknownCounterparty_whenExecute_thenResourceNotFound() {
		final Bond bond = saveBond("1000000.00");
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(987654L);
		dto.setBondId(bond.getId());
		dto.setNotionalAmount(new BigDecimal("100000.00"));
		dto.setExecutionPrice(new BigDecimal("99875.00"));
		dto.setSide(Side.BUY);

		assertThrows(ResourceNotFoundException.class, () -> saga.executeRfq(dto));
	}

	@Test
	public void givenUnknownBond_whenExecute_thenResourceNotFound() {
		final Counterparty counterparty = saveCounterparty("Ghost Bond Buyer", "1000000.00");
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(counterparty.getId());
		dto.setBondId(987654L);
		dto.setNotionalAmount(new BigDecimal("100000.00"));
		dto.setExecutionPrice(new BigDecimal("99875.00"));
		dto.setSide(Side.BUY);

		assertThrows(ResourceNotFoundException.class, () -> saga.executeRfq(dto));
	}

	private RfqDto dto(final Counterparty counterparty, final Bond bond, final String notional,
			final String price, final Side side) {
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(counterparty.getId());
		dto.setBondId(bond.getId());
		dto.setNotionalAmount(new BigDecimal(notional));
		dto.setExecutionPrice(new BigDecimal(price));
		dto.setSide(side);
		return dto;
	}

	private Counterparty saveCounterparty(final String name, final String creditLimit) {
		return counterpartyRepository.save(new Counterparty(name, lei(), new BigDecimal(creditLimit)));
	}

	private Bond saveBond(final String availableNotional) {
		return bondRepository.save(new Bond(isin(), "US Treasury", new BigDecimal("2.5000"),
				LocalDate.of(2032, 3, 31), new BigDecimal(availableNotional)));
	}

	private Bond reload(final Bond bond) {
		return bondRepository.findById(bond.getId()).orElseThrow(ResourceNotFoundException::new);
	}

	private Counterparty reload(final Counterparty counterparty) {
		return counterpartyRepository.findById(counterparty.getId())
				.orElseThrow(ResourceNotFoundException::new);
	}

	private static String isin() {
		return String.format("SAGA%08d", SEQUENCE.incrementAndGet());
	}

	private static String lei() {
		return String.format("549300SAGA%010d", SEQUENCE.incrementAndGet());
	}
}
