package com.javieraviles.splitthemonolith.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BondController.class)
class BondControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockBean
	private BondRepository repository;

	@MockBean
	private CounterpartyRepository counterpartyRepository;

	@MockBean
	private RfqRepository rfqRepository;

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule());

	private Bond buildBond() {
		return new Bond("US912828YK15", "US Treasury",
				new BigDecimal("2.8750"), LocalDate.of(2032, 5, 15),
				new BigDecimal("25000000.00"));
	}

	@Test
	void getAll_returnsBondList() throws Exception {
		when(repository.findAll()).thenReturn(Collections.singletonList(buildBond()));

		mvc.perform(get("/bonds").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].isin", is("US912828YK15")));
	}

	@Test
	void createBond_returns201() throws Exception {
		Bond bond = buildBond();
		when(repository.save(any(Bond.class))).thenReturn(bond);

		mvc.perform(post("/bonds")
				.content(MAPPER.writeValueAsString(bond))
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.isin", is("US912828YK15")));
	}

	@Test
	void getOne_exists_returnsBond() throws Exception {
		when(repository.findById(1L)).thenReturn(Optional.of(buildBond()));

		mvc.perform(get("/bonds/1").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isin", is("US912828YK15")));
	}

	@Test
	void getOne_notFound_returns404() throws Exception {
		when(repository.findById(9999L)).thenReturn(Optional.empty());

		mvc.perform(get("/bonds/9999").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void putBond_exists_updatesAndReturns200() throws Exception {
		Bond existing = buildBond();
		Bond updated = new Bond("US912828ZT09", "US Treasury",
				new BigDecimal("3.1250"), LocalDate.of(2033, 8, 15),
				new BigDecimal("50000000.00"));
		when(repository.findById(1L)).thenReturn(Optional.of(existing));
		when(repository.save(any(Bond.class))).thenReturn(updated);

		mvc.perform(put("/bonds/1")
				.content(MAPPER.writeValueAsString(updated))
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isin", is("US912828ZT09")));
	}

	@Test
	void putBond_notFound_returns404() throws Exception {
		when(repository.findById(9999L)).thenReturn(Optional.empty());

		Bond updated = buildBond();
		mvc.perform(put("/bonds/9999")
				.content(MAPPER.writeValueAsString(updated))
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void patchBond_addOperation_callsAddNotional() throws Exception {
		Bond bond = buildBond();
		when(repository.findById(1L)).thenReturn(Optional.of(bond));
		when(repository.save(any(Bond.class))).thenAnswer(inv -> inv.getArgument(0));

		Map<String, String> body = new HashMap<>();
		body.put("amount", "1000000");
		body.put("operation", "ADD");

		mvc.perform(patch("/bonds/1")
				.content(MAPPER.writeValueAsString(body))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional", is(26000000.00)));
	}

	@Test
	void patchBond_deductOperation_callsDeductNotional() throws Exception {
		Bond bond = buildBond();
		when(repository.findById(1L)).thenReturn(Optional.of(bond));
		when(repository.save(any(Bond.class))).thenAnswer(inv -> inv.getArgument(0));

		Map<String, String> body = new HashMap<>();
		body.put("amount", "500000");
		body.put("operation", "DEDUCT");

		mvc.perform(patch("/bonds/1")
				.content(MAPPER.writeValueAsString(body))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableNotional", is(24500000.00)));
	}

	@Test
	void patchBond_deductExceedsAvailable_returns400() throws Exception {
		Bond bond = new Bond("US912828YK15", "US Treasury",
				new BigDecimal("2.8750"), LocalDate.of(2032, 5, 15),
				new BigDecimal("100.00"));
		when(repository.findById(1L)).thenReturn(Optional.of(bond));

		Map<String, String> body = new HashMap<>();
		body.put("amount", "500");
		body.put("operation", "DEDUCT");

		mvc.perform(patch("/bonds/1")
				.content(MAPPER.writeValueAsString(body))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}

	@Test
	void patchBond_invalidOperation_returns400() throws Exception {
		Bond bond = buildBond();
		when(repository.findById(1L)).thenReturn(Optional.of(bond));

		Map<String, String> body = new HashMap<>();
		body.put("amount", "1000");
		body.put("operation", "INVALID");

		mvc.perform(patch("/bonds/1")
				.content(MAPPER.writeValueAsString(body))
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("wrong operation"));
	}

	@Test
	void deleteBond_callsRepository() throws Exception {
		mvc.perform(delete("/bonds/1"))
				.andExpect(status().isOk());

		verify(repository).deleteById(1L);
	}
}
