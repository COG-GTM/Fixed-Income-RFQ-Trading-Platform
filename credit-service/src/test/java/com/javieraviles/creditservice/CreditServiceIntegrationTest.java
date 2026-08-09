package com.javieraviles.creditservice;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.creditservice.dto.CreditCheckRequest;
import com.javieraviles.creditservice.entity.CreditAccount;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class CreditServiceIntegrationTest {

	@Autowired
	private MockMvc mvc;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	public void givenSeededAccount_whenGetByLei_thenReturnAccount() throws Exception {
		mvc.perform(get("/credit-accounts/549300EXAMPLE12345678").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.counterpartyName", is("Acme Asset Management")));
	}

	@Test
	public void whenCheckCreditWithinLimit_thenApprovedAndCreditReserved() throws Exception {
		createAccount("549300HAPPYPATH0001", "Happy Path Capital", "1000000.00");

		mvc.perform(post("/credit-checks")
				.content(asJsonString(new CreditCheckRequest("549300HAPPYPATH0001", new BigDecimal("400000.00"))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.approved", is(true)))
				.andExpect(jsonPath("$.availableCredit", is(600000.00)));

		mvc.perform(get("/credit-accounts/549300HAPPYPATH0001")).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(600000.00)));
	}

	@Test
	public void whenCheckCreditAboveLimit_thenConflictAndCreditUnchanged() throws Exception {
		createAccount("549300SMALLFUND001", "Small Fund LLC", "500000.00");

		mvc.perform(post("/credit-checks")
				.content(asJsonString(new CreditCheckRequest("549300SMALLFUND001", new BigDecimal("999000.00"))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isConflict())
				.andExpect(status().reason(containsString("Insufficient credit")));

		mvc.perform(get("/credit-accounts/549300SMALLFUND001")).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(500000.00)));
	}

	@Test
	public void whenCheckCreditForUnknownLei_thenNotFound() throws Exception {
		mvc.perform(post("/credit-checks")
				.content(asJsonString(new CreditCheckRequest("549300UNKNOWN00001", new BigDecimal("1.00"))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
	}

	@Test
	public void whenReleaseCredit_thenAvailableCreditRestored() throws Exception {
		createAccount("549300RELEASE000001", "Release Partners", "1000000.00");

		mvc.perform(post("/credit-checks")
				.content(asJsonString(new CreditCheckRequest("549300RELEASE000001", new BigDecimal("250000.00"))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

		mvc.perform(post("/credit-releases")
				.content(asJsonString(new CreditCheckRequest("549300RELEASE000001", new BigDecimal("250000.00"))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.availableCredit", is(1000000.00)));
	}

	private void createAccount(final String lei, final String name, final String creditLimit) throws Exception {
		mvc.perform(post("/credit-accounts")
				.content(asJsonString(new CreditAccount(lei, name, new BigDecimal(creditLimit))))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated());
	}

	private static String asJsonString(final Object obj) {
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
