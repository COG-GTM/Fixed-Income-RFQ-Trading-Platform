package com.javieraviles.creditservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.creditservice.dto.CreditOperationRequest;
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
public class CreditServiceIntegrationTest {

	@Autowired
	private MockMvc mvc;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	public void reserveThenReleasePreservesCreditInvariant() throws Exception {
		final long id = createCounterparty(new Counterparty("Reserve Fund", "549300RESERVE000001",
				new BigDecimal("1000000.00")));

		mvc.perform(post("/credit/reservations").content(asJson(new CreditOperationRequest(id, new BigDecimal("400000.00"))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit").value(600000.00));

		mvc.perform(post("/credit/releases").content(asJson(new CreditOperationRequest(id, new BigDecimal("400000.00"))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit").value(1000000.00));
	}

	@Test
	public void reserveBeyondAvailableCreditReturnsConflict() throws Exception {
		final long id = createCounterparty(new Counterparty("Tiny Fund", "549300TINYFUND00001",
				new BigDecimal("100000.00")));

		mvc.perform(post("/credit/reservations").content(asJson(new CreditOperationRequest(id, new BigDecimal("100000.01"))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isConflict());

		// Invariant intact after a rejected reservation.
		mvc.perform(get("/credit/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit").value(100000.00));
	}

	@Test
	public void reserveForUnknownCounterpartyReturnsNotFound() throws Exception {
		mvc.perform(post("/credit/reservations").content(asJson(new CreditOperationRequest(9999L, new BigDecimal("1.00"))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
	}

	private long createCounterparty(final Counterparty counterparty) throws Exception {
		final MvcResult result = mvc.perform(post("/counterparties").content(asJson(counterparty))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();
		final JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
		return node.get("id").asLong();
	}

	private static String asJson(final Object obj) {
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
