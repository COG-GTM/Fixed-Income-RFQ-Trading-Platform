package com.javieraviles.splitthemonolith;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.splitthemonolith.entity.Counterparty;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Confirmation seam with the feature flag OFF (default): credit-add must use the
 * in-process confirmation path and succeed unchanged.
 */
@SpringBootTest(properties = {
		"use.confirmation.service=false",
		"spring.datasource.url=jdbc:h2:mem:seam-local-db;DB_CLOSE_DELAY=-1" })
@AutoConfigureMockMvc
public class ConfirmationSeamLocalTest {

	@Autowired
	private MockMvc mvc;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	public void givenFlagOff_whenAddCredit_thenInProcessPathSucceeds() throws Exception {
		final long id = createCounterparty("Local Path Fund", "549300LOCALPATH001", "1000000.00");

		mvc.perform(patch("/counterparties/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":\"250000.00\",\"operation\":\"ADD\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(1250000.00)));
	}

	private long createCounterparty(final String name, final String lei, final String creditLimit) throws Exception {
		final Counterparty cp = new Counterparty(name, lei, new BigDecimal(creditLimit));
		final MvcResult result = mvc.perform(post("/counterparties").content(MAPPER.writeValueAsString(cp))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		final JsonNode node = MAPPER.readTree(result.getResponse().getContentAsString());
		return node.get("id").asLong();
	}

}
