package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Side;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class RfqExecutionSagaBoundaryTest {

	@Autowired
	private MockMvc mvc;

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule());

	@Test
	public void whenCreditExactlyMatchesPrice_thenCreditBecomesZero() throws Exception {
		final BigDecimal exactCredit = new BigDecimal("998750.00");

		final Counterparty cp = new Counterparty("Exact Credit Corp",
				"549300EXACTCREDIT01", exactCredit);
		final Bond bond = new Bond("US912828EX01", "US Treasury",
				new BigDecimal("2.7500"), LocalDate.of(2034, 6, 15),
				new BigDecimal("50000000.00"));

		MvcResult resultCp = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(exactCredit);

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")));

		mvc.perform(get("/counterparties/" + cpId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(0.0)));
	}

	@Test
	public void whenCreditOneAbovePrice_thenCreditIsOne() throws Exception {
		final BigDecimal credit = new BigDecimal("998750.01");
		final BigDecimal price = new BigDecimal("998750.00");

		final Counterparty cp = new Counterparty("OffByOne Above Corp",
				"549300OFFBYONE0ABV", credit);
		final Bond bond = new Bond("US912828OB01", "US Treasury",
				new BigDecimal("3.0000"), LocalDate.of(2035, 1, 15),
				new BigDecimal("50000000.00"));

		MvcResult resultCp = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(price);

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")));

		mvc.perform(get("/counterparties/" + cpId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(0.01)));
	}

	@Test
	public void whenCreditOneBelowPrice_thenInsufficientCredit() throws Exception {
		final BigDecimal credit = new BigDecimal("998749.99");
		final BigDecimal price = new BigDecimal("998750.00");

		final Counterparty cp = new Counterparty("OffByOne Below Corp",
				"549300OFFBYONE0BLW", credit);
		final Bond bond = new Bond("US912828OB02", "US Treasury",
				new BigDecimal("2.5000"), LocalDate.of(2035, 3, 15),
				new BigDecimal("50000000.00"));

		MvcResult resultCp = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(price);

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		mvc.perform(get("/counterparties/" + cpId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(998749.99)));
	}

	@Test
	public void whenExactCreditUsed_thenSubsequentRfqFails() throws Exception {
		final BigDecimal exactCredit = new BigDecimal("500000.00");

		final Counterparty cp = new Counterparty("Depleted Credit Corp",
				"549300DEPLETEDCR01", exactCredit);
		final Bond bond = new Bond("US912828DP01", "US Treasury",
				new BigDecimal("3.2500"), LocalDate.of(2036, 9, 15),
				new BigDecimal("50000000.00"));

		MvcResult resultCp = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq1 = new RfqDto();
		rfq1.setCounterpartyId(cpId);
		rfq1.setBondId(bondId);
		rfq1.setNotionalAmount(new BigDecimal("500000.00"));
		rfq1.setSide(Side.BUY);
		rfq1.setExecutionPrice(exactCredit);

		mvc.perform(post("/rfqs").content(asJsonString(rfq1)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")));

		final RfqDto rfq2 = new RfqDto();
		rfq2.setCounterpartyId(cpId);
		rfq2.setBondId(bondId);
		rfq2.setNotionalAmount(new BigDecimal("100000.00"));
		rfq2.setSide(Side.BUY);
		rfq2.setExecutionPrice(new BigDecimal("1.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq2)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}

	@Test
	public void whenConcurrentExecution_thenNoCreditOverdraft() throws Exception {
		final BigDecimal credit = new BigDecimal("1000000.00");

		final Counterparty cp = new Counterparty("Concurrent Corp",
				"549300CONCURRENT01", credit);
		final Bond bond = new Bond("US912828CC01", "US Treasury",
				new BigDecimal("2.8750"), LocalDate.of(2037, 4, 15),
				new BigDecimal("500000000.00"));

		MvcResult resultCp = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final int threadCount = 5;
		final BigDecimal pricePerRfq = new BigDecimal("300000.00");
		final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		final CountDownLatch latch = new CountDownLatch(1);
		final List<Future<Integer>> futures = new ArrayList<>();

		for (int i = 0; i < threadCount; i++) {
			futures.add(executor.submit(() -> {
				latch.await();
				final RfqDto rfq = new RfqDto();
				rfq.setCounterpartyId(cpId);
				rfq.setBondId(bondId);
				rfq.setNotionalAmount(new BigDecimal("300000.00"));
				rfq.setSide(Side.BUY);
				rfq.setExecutionPrice(pricePerRfq);

				MvcResult result = mvc.perform(post("/rfqs")
						.content(asJsonString(rfq))
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON))
						.andReturn();
				return result.getResponse().getStatus();
			}));
		}

		latch.countDown();

		int successCount = 0;
		for (Future<Integer> future : futures) {
			int statusCode = future.get();
			if (statusCode == 201) {
				successCount++;
			}
		}
		executor.shutdown();

		MvcResult cpResult = mvc.perform(get("/counterparties/" + cpId)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk()).andReturn();

		JsonNode cpNode = MAPPER.readTree(cpResult.getResponse().getContentAsString());
		BigDecimal remainingCredit = cpNode.get("availableCredit").decimalValue();

		assert remainingCredit.compareTo(BigDecimal.ZERO) >= 0 :
				"Credit must never go negative; got " + remainingCredit;
	}

	@Test
	public void whenNotionalExactlyMatchesAmount_thenNotionalBecomesZero() throws Exception {
		final BigDecimal exactNotional = new BigDecimal("1000000.00");

		final Counterparty cp = new Counterparty("Notional Test Corp",
				"549300NOTIONAL0001", new BigDecimal("50000000.00"));
		final Bond bond = new Bond("US912828NT01", "US Treasury",
				new BigDecimal("3.5000"), LocalDate.of(2034, 11, 15),
				exactNotional);

		MvcResult resultCp = mvc.perform(post("/counterparties").content(asJsonString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		MvcResult resultBond = mvc.perform(post("/bonds").content(asJsonString(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		final long cpId = extractId(resultCp);
		final long bondId = extractId(resultBond);

		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(cpId);
		rfq.setBondId(bondId);
		rfq.setNotionalAmount(exactNotional);
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("998750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")));

		mvc.perform(get("/bonds/" + bondId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional", is(0.0)));
	}

	private static long extractId(final MvcResult result) throws Exception {
		final JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
		return node.get("id").asLong();
	}

	private static String asJsonString(final Object obj) {
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
