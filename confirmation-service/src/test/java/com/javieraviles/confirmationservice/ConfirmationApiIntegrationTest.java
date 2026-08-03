package com.javieraviles.confirmationservice;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.confirmationservice.dto.TradeConfirmationDto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class ConfirmationApiIntegrationTest {

	@Autowired
	private MockMvc mvc;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	public void whenPostConfirmation_thenReturnCreated() throws Exception {
		final TradeConfirmationDto confirmation = new TradeConfirmationDto("Acme Asset Management",
				new BigDecimal("1500000.00"));

		mvc.perform(post("/confirmations").content(asJsonString(confirmation))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.counterpartyName", is("Acme Asset Management")))
				.andExpect(jsonPath("$.creditAmount", is(1500000.00)))
				.andExpect(jsonPath("$.confirmedAt").exists());
	}

	@Test
	public void whenPostConfirmationWithTrailingSlash_thenReturnCreated() throws Exception {
		final TradeConfirmationDto confirmation = new TradeConfirmationDto("Fidelity Investments",
				new BigDecimal("250000.00"));

		mvc.perform(post("/confirmations/").content(asJsonString(confirmation))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated());
	}

	@Test
	public void whenPostConfirmation_thenItIsRetrievable() throws Exception {
		final TradeConfirmationDto confirmation = new TradeConfirmationDto("BlackRock Fund Advisors",
				new BigDecimal("750000.00"));

		final MvcResult result = mvc.perform(post("/confirmations").content(asJsonString(confirmation))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated()).andReturn();

		mvc.perform(get("/confirmations/" + extractId(result)).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.counterpartyName", is("BlackRock Fund Advisors")));

		mvc.perform(get("/confirmations").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
	}

	@Test
	public void whenGetNonExistentConfirmation_thenReturnNotFound() throws Exception {
		mvc.perform(get("/confirmations/9999").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	public void whenPostConfirmationWithNonPositiveAmount_thenReturnBadRequest() throws Exception {
		final TradeConfirmationDto confirmation = new TradeConfirmationDto("Acme Asset Management",
				new BigDecimal("-1.00"));

		mvc.perform(post("/confirmations").content(asJsonString(confirmation))
				.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
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
