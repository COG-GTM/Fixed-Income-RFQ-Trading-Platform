package com.javieraviles.splitthemonolith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class RfqConcurrencyIntegrationTest {

	private static final int PARALLEL_REQUESTS = 20;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private RfqRepository rfqRepository;

	@Test
	public void whenParallelRfqsForFullBalances_thenOnlyOneExecutesAndBalancesStayConsistent() throws Exception {
		final BigDecimal notional = new BigDecimal("10000000.00");
		final BigDecimal credit = new BigDecimal("9987500.00");
		final Counterparty cp = counterpartyRepository
				.save(new Counterparty("Race Capital Partners", "549300RACECAPITAL001", credit));
		final Bond bond = bondRepository.save(new Bond("US912828RC01", "US Treasury",
				new BigDecimal("2.0000"), LocalDate.of(2034, 2, 15), notional));

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cp.getId());
		rfq.setBondId(bond.getId());
		rfq.setNotionalAmount(notional);
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(credit);
		final String body = new ObjectMapper().writeValueAsString(rfq);

		final ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
		final CountDownLatch start = new CountDownLatch(1);
		final List<Future<HttpStatus>> results = new ArrayList<>();
		try {
			for (int i = 0; i < PARALLEL_REQUESTS; i++) {
				results.add(executor.submit(() -> {
					start.await();
					final int status = mvc.perform(post("/rfqs").content(body).contentType(MediaType.APPLICATION_JSON))
							.andReturn().getResponse().getStatus();
					return HttpStatus.valueOf(status);
				}));
			}
			start.countDown();

			int created = 0;
			for (final Future<HttpStatus> result : results) {
				final HttpStatus status = result.get(60, TimeUnit.SECONDS);
				if (status == HttpStatus.CREATED) {
					created++;
				} else {
					assertTrue(status == HttpStatus.BAD_REQUEST || status == HttpStatus.CONFLICT,
							"unexpected status " + status);
				}
			}
			assertEquals(1, created);
		} finally {
			executor.shutdownNow();
		}

		final BigDecimal executedNotional = rfqRepository.findAll().stream()
				.filter(r -> r.getBond().getId() == bond.getId() && r.getStatus() == RfqStatus.EXECUTED)
				.map(Rfq::getNotionalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		final BigDecimal executedPrice = rfqRepository.findAll().stream()
				.filter(r -> r.getCounterparty().getId() == cp.getId() && r.getStatus() == RfqStatus.EXECUTED)
				.map(Rfq::getExecutionPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
		final BigDecimal remainingNotional = bondRepository.findById(bond.getId()).get().getAvailableNotional();
		final BigDecimal remainingCredit = counterpartyRepository.findById(cp.getId()).get().getAvailableCredit();

		assertEquals(0, notional.compareTo(remainingNotional.add(executedNotional)));
		assertEquals(0, credit.compareTo(remainingCredit.add(executedPrice)));
		assertEquals(0, BigDecimal.ZERO.compareTo(remainingNotional));
		assertEquals(0, BigDecimal.ZERO.compareTo(remainingCredit));
	}
}
