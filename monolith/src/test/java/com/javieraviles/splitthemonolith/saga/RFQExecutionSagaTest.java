package com.javieraviles.splitthemonolith.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:rfqdb-saga;DB_CLOSE_DELAY=-1")
class RFQExecutionSagaTest {

	@Autowired
	private RFQExecutionSaga saga;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private RfqRepository rfqRepository;

	@Test
	void whenExecuteRfq_thenNotionalAndCreditAreAdjusted() {
		final Counterparty cp = counterpartyRepository.save(new Counterparty("Saga Capital One",
				"549300SAGACAP00001", new BigDecimal("10000000.00")));
		final Bond bond = bondRepository.save(new Bond("US912828SG01", "US Treasury",
				new BigDecimal("2.0000"), LocalDate.of(2030, 1, 15), new BigDecimal("20000000.00")));

		final Rfq executed = saga.executeRfq(rfqDto(cp.getId(), bond.getId(),
				new BigDecimal("3000000.00"), new BigDecimal("2985000.00")));

		assertEquals(RfqStatus.EXECUTED, executed.getStatus());
		assertNotNull(executed.getCreatedAt());
		assertEquals(0, new BigDecimal("17000000.00")
				.compareTo(bondRepository.findById(bond.getId()).orElseThrow().getAvailableNotional()));
		assertEquals(0, new BigDecimal("7015000.00")
				.compareTo(counterpartyRepository.findById(cp.getId()).orElseThrow().getAvailableCredit()));
	}

	@Test
	void whenInsufficientCredit_thenNotionalIsNotConsumedAndNoRfqPersisted() {
		final Counterparty cp = counterpartyRepository.save(new Counterparty("Saga Capital Two",
				"549300SAGACAP00002", new BigDecimal("1000000.00")));
		final Bond bond = bondRepository.save(new Bond("US912828SG02", "US Treasury",
				new BigDecimal("2.0000"), LocalDate.of(2030, 1, 15), new BigDecimal("20000000.00")));
		final long rfqCountBefore = rfqRepository.count();

		assertThrows(InsufficientCreditException.class, () -> saga.executeRfq(rfqDto(cp.getId(), bond.getId(),
				new BigDecimal("5000000.00"), new BigDecimal("4990000.00"))));

		assertEquals(0, new BigDecimal("20000000.00")
				.compareTo(bondRepository.findById(bond.getId()).orElseThrow().getAvailableNotional()));
		assertEquals(0, new BigDecimal("1000000.00")
				.compareTo(counterpartyRepository.findById(cp.getId()).orElseThrow().getAvailableCredit()));
		assertEquals(rfqCountBefore, rfqRepository.count());
	}

	@Test
	void whenInsufficientNotional_thenCreditIsUntouchedAndNoRfqPersisted() {
		final Counterparty cp = counterpartyRepository.save(new Counterparty("Saga Capital Three",
				"549300SAGACAP00003", new BigDecimal("10000000.00")));
		final Bond bond = bondRepository.save(new Bond("US912828SG03", "US Treasury",
				new BigDecimal("2.0000"), LocalDate.of(2030, 1, 15), new BigDecimal("1000000.00")));
		final long rfqCountBefore = rfqRepository.count();

		assertThrows(InsufficientNotionalException.class, () -> saga.executeRfq(rfqDto(cp.getId(), bond.getId(),
				new BigDecimal("5000000.00"), new BigDecimal("4990000.00"))));

		assertEquals(0, new BigDecimal("1000000.00")
				.compareTo(bondRepository.findById(bond.getId()).orElseThrow().getAvailableNotional()));
		assertEquals(0, new BigDecimal("10000000.00")
				.compareTo(counterpartyRepository.findById(cp.getId()).orElseThrow().getAvailableCredit()));
		assertEquals(rfqCountBefore, rfqRepository.count());
	}

	@Test
	void whenCreditExactlyCoversExecutionPrice_thenRfqExecutes() {
		final Counterparty cp = counterpartyRepository.save(new Counterparty("Saga Capital Four",
				"549300SAGACAP00004", new BigDecimal("2000000.00")));
		final Bond bond = bondRepository.save(new Bond("US912828SG04", "US Treasury",
				new BigDecimal("2.0000"), LocalDate.of(2030, 1, 15), new BigDecimal("2000000.00")));

		final Rfq executed = saga.executeRfq(rfqDto(cp.getId(), bond.getId(),
				new BigDecimal("2000000.00"), new BigDecimal("2000000.00")));

		assertEquals(RfqStatus.EXECUTED, executed.getStatus());
		assertEquals(0, BigDecimal.ZERO
				.compareTo(bondRepository.findById(bond.getId()).orElseThrow().getAvailableNotional()));
		assertEquals(0, BigDecimal.ZERO
				.compareTo(counterpartyRepository.findById(cp.getId()).orElseThrow().getAvailableCredit()));
	}

	@Test
	void whenCounterpartyMissing_thenResourceNotFound() {
		final Bond bond = bondRepository.save(new Bond("US912828SG05", "US Treasury",
				new BigDecimal("2.0000"), LocalDate.of(2030, 1, 15), new BigDecimal("2000000.00")));

		assertThrows(ResourceNotFoundException.class, () -> saga.executeRfq(rfqDto(987654L, bond.getId(),
				new BigDecimal("100000.00"), new BigDecimal("99000.00"))));

		assertEquals(0, new BigDecimal("2000000.00")
				.compareTo(bondRepository.findById(bond.getId()).orElseThrow().getAvailableNotional()));
	}

	@Test
	void whenBondMissing_thenResourceNotFound() {
		final Counterparty cp = counterpartyRepository.save(new Counterparty("Saga Capital Six",
				"549300SAGACAP00006", new BigDecimal("2000000.00")));

		assertThrows(ResourceNotFoundException.class, () -> saga.executeRfq(rfqDto(cp.getId(), 987654L,
				new BigDecimal("100000.00"), new BigDecimal("99000.00"))));

		assertEquals(0, new BigDecimal("2000000.00")
				.compareTo(counterpartyRepository.findById(cp.getId()).orElseThrow().getAvailableCredit()));
	}

	@Test
	void whenSellSideRfq_thenSideIsPreserved() {
		final Counterparty cp = counterpartyRepository.save(new Counterparty("Saga Capital Seven",
				"549300SAGACAP00007", new BigDecimal("5000000.00")));
		final Bond bond = bondRepository.save(new Bond("US912828SG07", "US Treasury",
				new BigDecimal("2.0000"), LocalDate.of(2030, 1, 15), new BigDecimal("5000000.00")));

		final RfqDto dto = rfqDto(cp.getId(), bond.getId(),
				new BigDecimal("1000000.00"), new BigDecimal("999000.00"));
		dto.setSide(Side.SELL);

		final Rfq executed = saga.executeRfq(dto);

		assertEquals(Side.SELL, executed.getSide());
		assertTrue(rfqRepository.findById(executed.getId()).isPresent());
	}

	private static RfqDto rfqDto(final long counterpartyId, final long bondId,
			final BigDecimal notionalAmount, final BigDecimal executionPrice) {
		final RfqDto dto = new RfqDto();
		dto.setCounterpartyId(counterpartyId);
		dto.setBondId(bondId);
		dto.setNotionalAmount(notionalAmount);
		dto.setSide(Side.BUY);
		dto.setExecutionPrice(executionPrice);
		return dto;
	}
}
