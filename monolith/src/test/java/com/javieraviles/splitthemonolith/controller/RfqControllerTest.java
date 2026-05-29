package com.javieraviles.splitthemonolith.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;
import com.javieraviles.splitthemonolith.saga.RFQExecutionSaga;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RfqController.class)
class RfqControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockBean
	private RfqRepository repository;

	@MockBean
	private RFQExecutionSaga rfqExecutionSaga;

	@MockBean
	private BondRepository bondRepository;

	@MockBean
	private CounterpartyRepository counterpartyRepository;

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule());

	private Rfq buildRfq() {
		Counterparty cp = new Counterparty("Acme Asset Management",
				"549300ACME00000001", new BigDecimal("10000000.00"));
		Bond bond = new Bond("US912828YK15", "US Treasury",
				new BigDecimal("2.8750"), LocalDate.of(2032, 5, 15),
				new BigDecimal("25000000.00"));
		return new Rfq(cp, bond, new BigDecimal("1000000.00"),
				Side.BUY, RfqStatus.EXECUTED, new BigDecimal("998750.00"));
	}

	@Test
	void getAll_returnsListOfDtos() throws Exception {
		when(repository.findAll()).thenReturn(Collections.singletonList(buildRfq()));

		mvc.perform(get("/rfqs").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].side", is("BUY")))
				.andExpect(jsonPath("$[0].status", is("EXECUTED")));
	}

	@Test
	void getOne_exists_returnsDto() throws Exception {
		when(repository.findById(1L)).thenReturn(Optional.of(buildRfq()));

		mvc.perform(get("/rfqs/1").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.side", is("BUY")))
				.andExpect(jsonPath("$.status", is("EXECUTED")));
	}

	@Test
	void getOne_notFound_returns404() throws Exception {
		when(repository.findById(9999L)).thenReturn(Optional.empty());

		mvc.perform(get("/rfqs/9999").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void createRfq_delegatesToSaga_returns201() throws Exception {
		when(rfqExecutionSaga.executeRfq(any(RfqDto.class))).thenReturn(buildRfq());

		RfqDto dto = new RfqDto();
		dto.setCounterpartyId(1L);
		dto.setBondId(1L);
		dto.setNotionalAmount(new BigDecimal("1000000.00"));
		dto.setSide(Side.BUY);
		dto.setExecutionPrice(new BigDecimal("998750.00"));

		mvc.perform(post("/rfqs")
				.content(MAPPER.writeValueAsString(dto))
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("EXECUTED")));

		verify(rfqExecutionSaga).executeRfq(any(RfqDto.class));
	}

	@Test
	void deleteRfq_callsRepository() throws Exception {
		mvc.perform(delete("/rfqs/1"))
				.andExpect(status().isOk());

		verify(repository).deleteById(1L);
	}
}
