package com.javieraviles.confirmationservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class ConfirmationControllerTest {

	@Autowired
	private MockMvc mvc;

	@Test
	public void whenValidConfirmation_thenReturnCreated() throws Exception {
		mvc.perform(post("/confirmations/")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"counterpartyName\":\"Acme Asset Management\",\"creditAmount\":1000000.00}"))
				.andExpect(status().isCreated());
	}

	@Test
	public void whenCreditAmountNotPositive_thenReturnBadRequest() throws Exception {
		mvc.perform(post("/confirmations/")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"counterpartyName\":\"Acme Asset Management\",\"creditAmount\":-5.00}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	public void whenCounterpartyNameBlank_thenReturnBadRequest() throws Exception {
		mvc.perform(post("/confirmations/")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"counterpartyName\":\"\",\"creditAmount\":1000000.00}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	public void whenCreditAmountMissing_thenReturnBadRequest() throws Exception {
		mvc.perform(post("/confirmations/")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"counterpartyName\":\"Acme Asset Management\"}"))
				.andExpect(status().isBadRequest());
	}

}
