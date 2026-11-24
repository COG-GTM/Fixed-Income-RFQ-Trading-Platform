package com.javieraviles.splitthemonolith.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.RfqRepository;
import com.javieraviles.splitthemonolith.saga.RFQExecutionSaga;

@ExtendWith(MockitoExtension.class)
class RfqControllerTest {

	@Mock
	private RfqRepository repository;

	@Mock
	private RFQExecutionSaga rfqExecutionSaga;

	@InjectMocks
	private RfqController controller;

	private Rfq rfq;

	@BeforeEach
	void setUp() {
		final Counterparty cp = new Counterparty("Acme Asset Management", "549300EXAMPLE12345678",
				new BigDecimal("50000000.00"));
		ReflectionTestUtils.setField(cp, "id", 7L);
		final Bond bond = new Bond("US912828YK15", "US Treasury", new BigDecimal("2.7500"),
				LocalDate.of(2030, 11, 15), new BigDecimal("100000000.00"));
		ReflectionTestUtils.setField(bond, "id", 9L);
		rfq = new Rfq(cp, bond, new BigDecimal("5000000.00"), Side.SELL, RfqStatus.EXECUTED,
				new BigDecimal("4987500.00"));
		ReflectionTestUtils.setField(rfq, "id", 42L);
	}

	private static void assertMapped(final RfqDto dto) {
		assertThat(dto.getId()).isEqualTo(42L);
		assertThat(dto.getCounterpartyId()).isEqualTo(7L);
		assertThat(dto.getBondId()).isEqualTo(9L);
		assertThat(dto.getNotionalAmount()).isEqualByComparingTo("5000000.00");
		assertThat(dto.getSide()).isEqualTo(Side.SELL);
		assertThat(dto.getStatus()).isEqualTo(RfqStatus.EXECUTED);
		assertThat(dto.getExecutionPrice()).isEqualByComparingTo("4987500.00");
	}

	@Test
	void getAll_mapsEntitiesToDtos() {
		when(repository.findAll()).thenReturn(Collections.singletonList(rfq));

		final List<RfqDto> result = controller.getAll();

		assertThat(result).hasSize(1);
		assertMapped(result.get(0));
	}

	@Test
	void getOne_mapsEntityToDto() {
		when(repository.findById(42L)).thenReturn(Optional.of(rfq));

		final ResponseEntity<RfqDto> response = controller.getOne(42L);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertMapped(response.getBody());
	}

	@Test
	void getOne_withUnknownId_throwsNotFound() {
		when(repository.findById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.getOne(42L)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void create_delegatesToSagaAndReturnsCreated() {
		final RfqDto request = new RfqDto();
		when(rfqExecutionSaga.executeRfq(request)).thenReturn(rfq);

		final ResponseEntity<RfqDto> response = controller.createRfq(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertMapped(response.getBody());
	}
}
