package com.javieraviles.splitthemonolith;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression coverage for the credit and inventory adjustment endpoints used
 * alongside RFQ execution.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class CreditAndInventoryApiTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();
	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private BondRepository bondRepository;

	@Test
	public void givenCounterparty_whenCreditAdded_thenAvailableCreditIncreases() throws Exception {
		final Counterparty cp = saveCounterparty("2000000.00");

		mvc.perform(patch("/counterparties/" + cp.getId())
				.content("{\"amount\":\"500000.00\",\"operation\":\"ADD\"}")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", Matchers.comparesEqualTo(2500000.00)));
	}

	@Test
	public void givenCounterparty_whenCreditDeductedBeyondLimit_thenBadRequest() throws Exception {
		final Counterparty cp = saveCounterparty("100000.00");

		mvc.perform(patch("/counterparties/" + cp.getId())
				.content("{\"amount\":\"250000.00\",\"operation\":\"DEDUCT\"}")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(Matchers.containsString("Insufficient credit")));

		mvc.perform(get("/counterparties/" + cp.getId()).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", Matchers.comparesEqualTo(100000.00)));
	}

	@Test
	public void givenCounterparty_whenUnknownOperation_thenBadRequest() throws Exception {
		final Counterparty cp = saveCounterparty("100000.00");

		mvc.perform(patch("/counterparties/" + cp.getId())
				.content("{\"amount\":\"1000.00\",\"operation\":\"TRANSFER\"}")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("wrong operation"));
	}

	@Test
	public void givenCounterparty_whenUpdated_thenFieldsPersisted() throws Exception {
		final Counterparty cp = saveCounterparty("3000000.00");
		final Counterparty updated = new Counterparty(cp.getName(), cp.getLei(), new BigDecimal("4000000.00"));

		mvc.perform(put("/counterparties/" + cp.getId()).content(MAPPER.writeValueAsString(updated))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creditLimit", Matchers.comparesEqualTo(4000000.00)));
	}

	@Test
	public void givenBond_whenNotionalAdded_thenInventoryIncreases() throws Exception {
		final Bond bond = saveBond("1000000.00");

		mvc.perform(patch("/bonds/" + bond.getId())
				.content("{\"amount\":\"750000.00\",\"operation\":\"ADD\"}")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional", Matchers.comparesEqualTo(1750000.00)));
	}

	@Test
	public void givenBond_whenNotionalDeductedBeyondInventory_thenBadRequest() throws Exception {
		final Bond bond = saveBond("1000000.00");

		mvc.perform(patch("/bonds/" + bond.getId())
				.content("{\"amount\":\"2000000.00\",\"operation\":\"DEDUCT\"}")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(status().reason(Matchers.containsString("Insufficient notional")));

		mvc.perform(get("/bonds/" + bond.getId()).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional", Matchers.comparesEqualTo(1000000.00)));
	}

	@Test
	public void givenBond_whenUpdated_thenFieldsPersisted() throws Exception {
		final Bond bond = saveBond("1000000.00");
		final Bond updated = new Bond(bond.getIsin(), "Corporate Issuer", new BigDecimal("4.1250"),
				LocalDate.of(2035, 9, 30), new BigDecimal("9000000.00"));

		mvc.perform(put("/bonds/" + bond.getId()).content(MAPPER.writeValueAsString(updated))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.issuer", Matchers.is("Corporate Issuer")))
				.andExpect(jsonPath("$.availableNotional", Matchers.comparesEqualTo(9000000.00)));
	}

	@Test
	public void givenUnknownIds_whenGetOne_thenNotFound() throws Exception {
		mvc.perform(get("/bonds/778899").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
		mvc.perform(get("/counterparties/778899").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	private Counterparty saveCounterparty(final String creditLimit) {
		final int seq = SEQUENCE.incrementAndGet();
		return counterpartyRepository.save(new Counterparty("Api Counterparty " + seq,
				String.format("549300API%011d", seq), new BigDecimal(creditLimit)));
	}

	private Bond saveBond(final String availableNotional) {
		final int seq = SEQUENCE.incrementAndGet();
		return bondRepository.save(new Bond(String.format("APIB%08d", seq), "US Treasury",
				new BigDecimal("3.0000"), LocalDate.of(2033, 12, 31), new BigDecimal(availableNotional)));
	}
}
