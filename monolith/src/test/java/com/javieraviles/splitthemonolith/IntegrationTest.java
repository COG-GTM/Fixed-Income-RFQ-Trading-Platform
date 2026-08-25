package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.dto.CounterpartyDto;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Side;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class IntegrationTest {

	@Autowired
	private MockMvc mvc;

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule());

	private static final CreditServiceStub CREDIT_SERVICE = new CreditServiceStub();

	@BeforeAll
	public static void startCreditService() throws IOException {
		CREDIT_SERVICE.start(CreditServiceStub.PORT);
	}

	@AfterAll
	public static void stopCreditService() {
		CREDIT_SERVICE.stop();
	}

	@Test
	public void givenOneCounterparty_whenGetCounterparties_thenReturnJsonArray() throws Exception {
		mvc.perform(get("/counterparties").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name", is("Acme Asset Management")));
	}

	@Test
	public void givenOneBond_whenGetBonds_thenReturnJsonArray() throws Exception {
		mvc.perform(get("/bonds").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].isin", is("US912828YK15")));
	}

	@Test
	public void whenExecuteRfq_thenReturnCreated() throws Exception {
		final CounterpartyDto cp = newCounterparty("Fidelity Investments",
				"549300FIDELITY00001", new BigDecimal("20000000.00"));
		final Bond bond = new Bond("US912828ZT09", "US Treasury",
				new BigDecimal("3.1250"), LocalDate.of(2032, 5, 15),
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
		rfq.setExecutionPrice(new BigDecimal("998750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")));
	}

	@Test
	public void whenExecuteRfq_withInsufficientNotional_thenReturnBadRequest() throws Exception {
		final CounterpartyDto cp = newCounterparty("BlackRock Fund Advisors",
				"549300BLACKROCK0001", new BigDecimal("30000000.00"));
		final Bond bond = new Bond("US912828AB12", "US Treasury",
				new BigDecimal("2.5000"), LocalDate.of(2031, 8, 15),
				new BigDecimal("2000000.00"));

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
		rfq.setNotionalAmount(new BigDecimal("5000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("4993750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient notional")));
	}

	@Test
	public void whenExecuteRfq_withInsufficientCredit_thenReturnBadRequest() throws Exception {
		final CounterpartyDto cp = newCounterparty("Small Fund LLC",
				"549300SMALLFUND001", new BigDecimal("500000.00"));
		final Bond bond = new Bond("US912828CD34", "US Treasury",
				new BigDecimal("3.0000"), LocalDate.of(2033, 2, 15),
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
		rfq.setSide(Side.SELL);
		rfq.setExecutionPrice(new BigDecimal("999000.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest())
				.andExpect(status().reason(containsString("Insufficient credit")));
	}

	@Test
	public void whenExecuteRfq_withNonExistentCounterparty_thenReturnNotFound() throws Exception {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(9999L);
		rfq.setBondId(1L);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("998750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
	}

	@Test
	public void whenExecuteRfq_withNonExistentBond_thenReturnNotFound() throws Exception {
		final RfqDto rfq = new RfqDto();
		rfq.setCounterpartyId(1L);
		rfq.setBondId(9999L);
		rfq.setNotionalAmount(new BigDecimal("1000000.00"));
		rfq.setSide(Side.BUY);
		rfq.setExecutionPrice(new BigDecimal("998750.00"));

		mvc.perform(post("/rfqs").content(asJsonString(rfq)).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
	}

	private static CounterpartyDto newCounterparty(final String name, final String lei,
			final BigDecimal creditLimit) {
		return new CounterpartyDto(0L, name, lei, creditLimit, creditLimit);
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
