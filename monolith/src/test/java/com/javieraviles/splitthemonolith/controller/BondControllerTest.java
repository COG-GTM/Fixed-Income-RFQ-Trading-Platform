package com.javieraviles.splitthemonolith.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.exception.InsufficientNotionalException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;

@ExtendWith(MockitoExtension.class)
class BondControllerTest {

	private static final long ID = 1L;

	@Mock
	private BondRepository repository;

	@InjectMocks
	private BondController controller;

	private Bond bond;

	@BeforeEach
	void setUp() {
		bond = new Bond("US912828YK15", "US Treasury", new BigDecimal("2.7500"),
				LocalDate.of(2030, 11, 15), new BigDecimal("1000000.00"));
	}

	private static Map<String, String> body(final String operation, final String amount) {
		final Map<String, String> body = new HashMap<>();
		body.put("operation", operation);
		body.put("amount", amount);
		return body;
	}

	private void givenExistingBondSaved() {
		when(repository.findById(ID)).thenReturn(Optional.of(bond));
		when(repository.save(any(Bond.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void patchAdd_increasesNotional() {
		givenExistingBondSaved();

		final ResponseEntity<?> response = controller.partialUpdateGeneric(body("ADD", "500000.00"), ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("1500000.00");
	}

	@Test
	void patchDeduct_decreasesNotional() {
		givenExistingBondSaved();

		controller.partialUpdateGeneric(body("DEDUCT", "300000.00"), ID);

		assertThat(bond.getAvailableNotional()).isEqualByComparingTo("700000.00");
	}

	@Test
	void patchDeduct_aboveAvailableNotional_throwsAndDoesNotSave() {
		when(repository.findById(ID)).thenReturn(Optional.of(bond));

		assertThatThrownBy(() -> controller.partialUpdateGeneric(body("DEDUCT", "2000000.00"), ID))
				.isInstanceOf(InsufficientNotionalException.class);

		verify(repository, never()).save(any());
	}

	@Test
	void patch_withUnknownOperation_returnsBadRequest() {
		when(repository.findById(ID)).thenReturn(Optional.of(bond));

		final ResponseEntity<?> response = controller.partialUpdateGeneric(body("DOUBLE", "1.00"), ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isEqualTo("wrong operation");
		verify(repository, never()).save(any());
	}

	@Test
	void patch_withUnknownId_throwsNotFound() {
		when(repository.findById(ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.partialUpdateGeneric(body("ADD", "1.00"), ID))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void put_copiesAllEditableFields() {
		givenExistingBondSaved();
		final Bond update = new Bond("US912828ZT09", "US Treasury II", new BigDecimal("3.1250"),
				LocalDate.of(2032, 5, 15), new BigDecimal("42.00"));

		final Bond result = controller.updateBond(update, ID).getBody();

		assertThat(result.getIsin()).isEqualTo("US912828ZT09");
		assertThat(result.getIssuer()).isEqualTo("US Treasury II");
		assertThat(result.getCouponRate()).isEqualByComparingTo("3.1250");
		assertThat(result.getMaturityDate()).isEqualTo(LocalDate.of(2032, 5, 15));
		assertThat(result.getAvailableNotional()).isEqualByComparingTo("42.00");
	}

	@Test
	void put_withUnknownId_throwsNotFound() {
		when(repository.findById(ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.updateBond(bond, ID)).isInstanceOf(ResourceNotFoundException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void create_returnsCreatedWithSavedBond() {
		when(repository.save(bond)).thenReturn(bond);

		final ResponseEntity<Bond> response = controller.createBond(bond);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isSameAs(bond);
	}
}
