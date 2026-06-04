package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.persistence.EntityManager;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.InsufficientNotionalException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.saga.RFQExecutionSaga;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
public class RFQExecutionSagaTest {

	@Autowired
	private RFQExecutionSaga saga;

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private EntityManager entityManager;

	// --- (a) Credit-check failure must NOT leave bond notional deducted ---

	@Test
	@Transactional
	public void creditCheckFailure_rollsBackBondNotionalDeduction() {
		Counterparty cp = counterpartyRepository.save(
				new Counterparty("Low Credit Fund", "549300LOWCREDIT0001", new BigDecimal("100000.00")));
		Bond bond = bondRepository.save(new Bond("US912828RB01", "US Treasury",
				new BigDecimal("2.5000"), LocalDate.of(2034, 3, 15), new BigDecimal("50000000.00")));
		entityManager.flush();
		entityManager.clear();

		BigDecimal originalNotional = new BigDecimal("50000000.00");
		BigDecimal originalCredit = new BigDecimal("100000.00");

		RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cp.getId());
		rfq.setBondId(bond.getId());
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("999000.00"));

		assertThrows(InsufficientCreditException.class, () -> saga.executeRfq(rfq));

		Bond reloadedBond = bondRepository.findById(bond.getId()).get();
		assertEquals(0, originalNotional.compareTo(reloadedBond.getAvailableNotional()),
				"Bond notional must be restored after credit-check failure");

		Counterparty reloadedCp = counterpartyRepository.findById(cp.getId()).get();
		assertEquals(0, originalCredit.compareTo(reloadedCp.getAvailableCredit()),
				"Counterparty credit must be unchanged after credit-check failure");
	}

	// --- (b) Successful execution debits both bond and counterparty correctly ---

	@Test
	@Transactional
	public void successfulExecution_debitsBondAndCounterparty() {
		Counterparty cp = counterpartyRepository.save(
				new Counterparty("Wealthy Fund", "549300WEALTHY00001", new BigDecimal("20000000.00")));
		Bond bond = bondRepository.save(new Bond("US912828RB02", "US Treasury",
				new BigDecimal("3.0000"), LocalDate.of(2035, 6, 15), new BigDecimal("80000000.00")));
		entityManager.flush();
		entityManager.clear();

		RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cp.getId());
		rfq.setBondId(bond.getId());
		rfq.setNotionalAmount(new BigDecimal("5000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("4993750.00"));

		Rfq executed = saga.executeRfq(rfq);

		assertNotNull(executed);
		assertEquals(RfqStatus.EXECUTED, executed.getStatus());

		Bond reloadedBond = bondRepository.findById(bond.getId()).get();
		assertEquals(0, new BigDecimal("75000000.00").compareTo(reloadedBond.getAvailableNotional()),
				"Bond notional should be reduced by trade notional");

		Counterparty reloadedCp = counterpartyRepository.findById(cp.getId()).get();
		assertEquals(0, new BigDecimal("15006250.00").compareTo(reloadedCp.getAvailableCredit()),
				"Counterparty credit should be reduced by execution price");
	}

	// --- (c) Rounding boundary cases on settlement amounts ---

	@Test
	@Transactional
	public void settlementAmount_usesHalfEvenRounding() {
		Counterparty cp = counterpartyRepository.save(
				new Counterparty("Rounding Fund", "549300ROUNDING0001", new BigDecimal("50000000.00")));
		Bond bond = bondRepository.save(new Bond("US912828RB03", "US Treasury",
				new BigDecimal("2.8750"), LocalDate.of(2036, 9, 15), new BigDecimal("100000000.00")));
		entityManager.flush();
		entityManager.clear();

		RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cp.getId());
		rfq.setBondId(bond.getId());
		rfq.setNotionalAmount(new BigDecimal("3000000.00"));
		rfq.setSide(Side.BUY);
		// 99.8333... per 100 face → settlement = 3000000 * 99.8333... / 100 = 2995000.00
		// But pass a value with excess precision to verify rounding
		rfq.setExecutionPrice(new BigDecimal("2995000.005"));

		Rfq executed = saga.executeRfq(rfq);

		// HALF_EVEN: 2995000.005 → 2995000.00 (rounds to even)
		assertEquals(0, new BigDecimal("2995000.00").compareTo(executed.getExecutionPrice()),
				"Settlement price must be rounded to 2dp using HALF_EVEN");
	}

	@Test
	@Transactional
	public void settlementAmount_roundsUpOnOddLastDigit() {
		Counterparty cp = counterpartyRepository.save(
				new Counterparty("Rounding Fund 2", "549300ROUNDING0002", new BigDecimal("50000000.00")));
		Bond bond = bondRepository.save(new Bond("US912828RB04", "US Treasury",
				new BigDecimal("3.1250"), LocalDate.of(2037, 1, 15), new BigDecimal("100000000.00")));
		entityManager.flush();
		entityManager.clear();

		RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cp.getId());
		rfq.setBondId(bond.getId());
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.SELL);
		// 999999.015 → HALF_EVEN rounds to 999999.02 (last retained digit is odd → round up)
		rfq.setExecutionPrice(new BigDecimal("999999.015"));

		Rfq executed = saga.executeRfq(rfq);

		assertEquals(0, new BigDecimal("999999.02").compareTo(executed.getExecutionPrice()),
				"HALF_EVEN should round .015 up when preceding digit is odd");
	}

	@Test
	@Transactional
	public void settlementAmount_roundsDownOnEvenLastDigit() {
		Counterparty cp = counterpartyRepository.save(
				new Counterparty("Rounding Fund 3", "549300ROUNDING0003", new BigDecimal("50000000.00")));
		Bond bond = bondRepository.save(new Bond("US912828RB05", "US Treasury",
				new BigDecimal("2.0000"), LocalDate.of(2038, 4, 15), new BigDecimal("100000000.00")));
		entityManager.flush();
		entityManager.clear();

		RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cp.getId());
		rfq.setBondId(bond.getId());
		rfq.setNotionalAmount(new BigDecimal("2000000.00"));
		rfq.setSide(Side.BUY);
		// 1998000.025 → HALF_EVEN rounds to 1998000.02 (last retained digit is even → round down)
		rfq.setExecutionPrice(new BigDecimal("1998000.025"));

		Rfq executed = saga.executeRfq(rfq);

		assertEquals(0, new BigDecimal("1998000.02").compareTo(executed.getExecutionPrice()),
				"HALF_EVEN should round .025 down when preceding digit is even");
	}

	// --- Notional-check failure leaves both aggregates unchanged ---

	@Test
	@Transactional
	public void notionalCheckFailure_leavesBothAggregatesUnchanged() {
		Counterparty cp = counterpartyRepository.save(
				new Counterparty("Big Spender", "549300BIGSPEND0001", new BigDecimal("90000000.00")));
		Bond bond = bondRepository.save(new Bond("US912828RB06", "US Treasury",
				new BigDecimal("2.2500"), LocalDate.of(2032, 7, 15), new BigDecimal("500000.00")));
		entityManager.flush();
		entityManager.clear();

		RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cp.getId());
		rfq.setBondId(bond.getId());
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("998000.00"));

		assertThrows(InsufficientNotionalException.class, () -> saga.executeRfq(rfq));

		Bond reloadedBond = bondRepository.findById(bond.getId()).get();
		assertEquals(0, new BigDecimal("500000.00").compareTo(reloadedBond.getAvailableNotional()),
				"Bond notional unchanged when notional check fails");

		Counterparty reloadedCp = counterpartyRepository.findById(cp.getId()).get();
		assertEquals(0, new BigDecimal("90000000.00").compareTo(reloadedCp.getAvailableCredit()),
				"Counterparty credit unchanged when notional check fails");
	}
}
