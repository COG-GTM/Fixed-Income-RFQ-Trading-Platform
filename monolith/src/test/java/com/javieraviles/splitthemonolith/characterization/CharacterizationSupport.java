package com.javieraviles.splitthemonolith.characterization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared helpers for the characterization suite. These tests exercise the
 * application through its HTTP boundary only, so that they keep passing across
 * internal refactorings such as the trade confirmation port/adapter extraction.
 */
final class CharacterizationSupport {

	static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	private CharacterizationSupport() {
	}

	static long createCounterparty(final MockMvc mvc, final String name, final String lei,
			final String creditLimit) throws Exception {
		final Counterparty counterparty = new Counterparty(name, lei, new BigDecimal(creditLimit));
		final MvcResult result = mvc.perform(post("/counterparties").content(asJson(counterparty))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return idOf(result);
	}

	static long createBond(final MockMvc mvc, final String isin, final String availableNotional) throws Exception {
		final Bond bond = new Bond(isin, "US Treasury", new BigDecimal("2.7500"),
				LocalDate.of(2030, 11, 15), new BigDecimal(availableNotional));
		final MvcResult result = mvc.perform(post("/bonds").content(asJson(bond))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();
		return idOf(result);
	}

	static String creditPatch(final String operation, final String amount) {
		return String.format("{\"operation\":\"%s\",\"amount\":\"%s\"}", operation, amount);
	}

	static long idOf(final MvcResult result) throws Exception {
		return bodyOf(result).get("id").asLong();
	}

	static JsonNode bodyOf(final MvcResult result) throws Exception {
		return MAPPER.readTree(result.getResponse().getContentAsString());
	}

	static String asJson(final Object obj) {
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
