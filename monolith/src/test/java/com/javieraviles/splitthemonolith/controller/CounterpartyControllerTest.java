package com.javieraviles.splitthemonolith.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;
import com.javieraviles.splitthemonolith.restclient.TradeConfirmationMicroserviceClient;
import com.javieraviles.splitthemonolith.service.TradeConfirmationService;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

class CounterpartyControllerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static Counterparty buildCounterparty() {
		return new Counterparty("Acme Asset Management",
				"549300ACME00000001", new BigDecimal("10000000.00"));
	}

	@Nested
	@WebMvcTest(CounterpartyController.class)
	@TestPropertySource(properties = "use.confirmation.service=false")
	class LocalConfirmationTests {

		@Autowired
		private MockMvc mvc;

		@MockBean
		private CounterpartyRepository repository;

		@MockBean
		private TradeConfirmationService tradeConfirmationService;

		@MockBean
		private TradeConfirmationMicroserviceClient confirmationMsClient;

		@MockBean
		private BondRepository bondRepository;

		@MockBean
		private RfqRepository rfqRepository;

		@Test
		void getAll_returnsCounterpartyList() throws Exception {
			when(repository.findAll()).thenReturn(Collections.singletonList(buildCounterparty()));

			mvc.perform(get("/counterparties").contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(1)))
					.andExpect(jsonPath("$[0].name", is("Acme Asset Management")));
		}

		@Test
		void createCounterparty_returns201() throws Exception {
			Counterparty cp = buildCounterparty();
			when(repository.save(any(Counterparty.class))).thenReturn(cp);

			mvc.perform(post("/counterparties")
					.content(MAPPER.writeValueAsString(cp))
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.name", is("Acme Asset Management")));
		}

		@Test
		void getOne_exists_returnsCounterparty() throws Exception {
			when(repository.findById(1L)).thenReturn(Optional.of(buildCounterparty()));

			mvc.perform(get("/counterparties/1").contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name", is("Acme Asset Management")));
		}

		@Test
		void getOne_notFound_returns404() throws Exception {
			when(repository.findById(9999L)).thenReturn(Optional.empty());

			mvc.perform(get("/counterparties/9999").contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isNotFound());
		}

		@Test
		void putCounterparty_exists_updatesAndReturns200() throws Exception {
			Counterparty existing = buildCounterparty();
			Counterparty updated = new Counterparty("Fidelity Investments",
					"549300FIDELITY00001", new BigDecimal("20000000.00"));
			when(repository.findById(1L)).thenReturn(Optional.of(existing));
			when(repository.save(any(Counterparty.class))).thenReturn(updated);

			mvc.perform(put("/counterparties/1")
					.content(MAPPER.writeValueAsString(updated))
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name", is("Fidelity Investments")));
		}

		@Test
		void putCounterparty_notFound_returns404() throws Exception {
			when(repository.findById(9999L)).thenReturn(Optional.empty());

			Counterparty updated = buildCounterparty();
			mvc.perform(put("/counterparties/9999")
					.content(MAPPER.writeValueAsString(updated))
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isNotFound());
		}

		@Test
		void patchCounterparty_addWithLocalService_callsTradeConfirmationService() throws Exception {
			Counterparty cp = buildCounterparty();
			when(repository.findById(1L)).thenReturn(Optional.of(cp));
			when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));

			Map<String, String> body = new HashMap<>();
			body.put("amount", "1000000");
			body.put("operation", "ADD");

			mvc.perform(patch("/counterparties/1")
					.content(MAPPER.writeValueAsString(body))
					.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk());

			verify(tradeConfirmationService).sendTradeConfirmation(any(TradeConfirmationDto.class));
			verify(confirmationMsClient, never()).sendConfirmation(any(TradeConfirmationDto.class));
		}

		@Test
		void patchCounterparty_deductOperation_noConfirmation() throws Exception {
			Counterparty cp = buildCounterparty();
			when(repository.findById(1L)).thenReturn(Optional.of(cp));
			when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));

			Map<String, String> body = new HashMap<>();
			body.put("amount", "500000");
			body.put("operation", "DEDUCT");

			mvc.perform(patch("/counterparties/1")
					.content(MAPPER.writeValueAsString(body))
					.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk());

			verify(tradeConfirmationService, never()).sendTradeConfirmation(any(TradeConfirmationDto.class));
			verify(confirmationMsClient, never()).sendConfirmation(any(TradeConfirmationDto.class));
		}

		@Test
		void patchCounterparty_deductExceedsCredit_returns400() throws Exception {
			Counterparty cp = new Counterparty("Small Fund LLC",
					"549300SMALLFUND001", new BigDecimal("500.00"));
			when(repository.findById(1L)).thenReturn(Optional.of(cp));

			Map<String, String> body = new HashMap<>();
			body.put("amount", "1000");
			body.put("operation", "DEDUCT");

			mvc.perform(patch("/counterparties/1")
					.content(MAPPER.writeValueAsString(body))
					.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest());
		}

		@Test
		void patchCounterparty_invalidOperation_returns400() throws Exception {
			Counterparty cp = buildCounterparty();
			when(repository.findById(1L)).thenReturn(Optional.of(cp));

			Map<String, String> body = new HashMap<>();
			body.put("amount", "1000");
			body.put("operation", "INVALID");

			mvc.perform(patch("/counterparties/1")
					.content(MAPPER.writeValueAsString(body))
					.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest())
					.andExpect(content().string("wrong operation"));
		}

		@Test
		void deleteCounterparty_callsRepository() throws Exception {
			mvc.perform(delete("/counterparties/1"))
					.andExpect(status().isOk());

			verify(repository).deleteById(1L);
		}
	}

	@Nested
	@WebMvcTest(CounterpartyController.class)
	@TestPropertySource(properties = "use.confirmation.service=true")
	class MicroserviceConfirmationTests {

		@Autowired
		private MockMvc mvc;

		@MockBean
		private CounterpartyRepository repository;

		@MockBean
		private TradeConfirmationService tradeConfirmationService;

		@MockBean
		private TradeConfirmationMicroserviceClient confirmationMsClient;

		@MockBean
		private BondRepository bondRepository;

		@MockBean
		private RfqRepository rfqRepository;

		@Test
		void patchCounterparty_addWithMicroservice_callsMsClient() throws Exception {
			Counterparty cp = buildCounterparty();
			when(repository.findById(1L)).thenReturn(Optional.of(cp));
			when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));

			Map<String, String> body = new HashMap<>();
			body.put("amount", "1000000");
			body.put("operation", "ADD");

			mvc.perform(patch("/counterparties/1")
					.content(MAPPER.writeValueAsString(body))
					.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk());

			verify(confirmationMsClient).sendConfirmation(any(TradeConfirmationDto.class));
			verify(tradeConfirmationService, never()).sendTradeConfirmation(any(TradeConfirmationDto.class));
		}
	}
}
