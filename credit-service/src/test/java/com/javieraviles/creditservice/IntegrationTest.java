package com.javieraviles.creditservice;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.creditservice.entity.Counterparty;

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

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	public void givenOneCounterparty_whenGetCounterparties_thenReturnJsonArray() throws Exception {
		mvc.perform(get("/counterparties").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name", is("Acme Asset Management")));
	}

	@Test
	public void whenGetNonExistentCounterparty_thenReturnNotFound() throws Exception {
		mvc.perform(get("/counterparties/9999").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	public void whenReserveCredit_thenCreditIsDeducted() throws Exception {
		final long cpId = createCounterparty("Fidelity Investments", "549300FIDELITY00001",
				new BigDecimal("20000000.00"));

		mvc.perform(post("/counterparties/" + cpId + "/reservations").content("{\"amount\":\"1000000.00\"}")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.counterpartyId", is((int) cpId)))
				.andExpect(jsonPath("$.status", is("RESERVED")));

		mvc.perform(get("/counterparties/" + cpId).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(19000000.00)));
	}

	@Test
	public void whenReserveMoreCreditThanAvailable_thenReturnConflict() throws Exception {
		final long cpId = createCounterparty("Small Fund LLC", "549300SMALLFUND001", new BigDecimal("500000.00"));

		mvc.perform(post("/counterparties/" + cpId + "/reservations").content("{\"amount\":\"999000.00\"}")
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict());

		mvc.perform(get("/counterparties/" + cpId).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(500000.00)));
	}

	@Test
	public void whenReleaseReservation_thenCreditIsGivenBack() throws Exception {
		final long cpId = createCounterparty("BlackRock Fund Advisors", "549300BLACKROCK0001",
				new BigDecimal("30000000.00"));

		final MvcResult reservation = mvc
				.perform(post("/counterparties/" + cpId + "/reservations").content("{\"amount\":\"4993750.00\"}")
						.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		final long reservationId = extractId(reservation);

		mvc.perform(delete("/counterparties/" + cpId + "/reservations/" + reservationId))
				.andExpect(status().isNoContent());

		mvc.perform(get("/counterparties/" + cpId).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(30000000.00)));

		mvc.perform(get("/counterparties/" + cpId + "/reservations/" + reservationId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status", is("RELEASED")));
	}

	@Test
	public void whenReleaseReservationTwice_thenCreditIsGivenBackOnlyOnce() throws Exception {
		final long cpId = createCounterparty("Vanguard Group", "549300VANGUARD00001", new BigDecimal("10000000.00"));

		final MvcResult reservation = mvc
				.perform(post("/counterparties/" + cpId + "/reservations").content("{\"amount\":\"2500000.00\"}")
						.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		final long reservationId = extractId(reservation);

		mvc.perform(delete("/counterparties/" + cpId + "/reservations/" + reservationId))
				.andExpect(status().isNoContent());
		mvc.perform(delete("/counterparties/" + cpId + "/reservations/" + reservationId))
				.andExpect(status().isNoContent());

		mvc.perform(get("/counterparties/" + cpId).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(10000000.00)));
	}

	private long createCounterparty(final String name, final String lei, final BigDecimal creditLimit)
			throws Exception {
		final MvcResult result = mvc
				.perform(post("/counterparties").content(asJsonString(new Counterparty(name, lei, creditLimit)))
						.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return extractId(result);
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
