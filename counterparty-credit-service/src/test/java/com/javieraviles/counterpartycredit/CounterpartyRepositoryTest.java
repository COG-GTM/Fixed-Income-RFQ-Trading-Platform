package com.javieraviles.counterpartycredit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.javieraviles.counterpartycredit.domain.Counterparty;
import com.javieraviles.counterpartycredit.repository.CounterpartyRepository;

@DataJpaTest
class CounterpartyRepositoryTest {

	@Autowired
	private CounterpartyRepository repository;

	@Test
	void findByLei_resolvesPersistedCounterparty() {
		repository.save(new Counterparty("Acme Asset Management", "549300EXAMPLE12345678",
				new BigDecimal("50000000.00")));

		final Optional<Counterparty> resolved = repository.findByLei("549300EXAMPLE12345678");

		assertTrue(resolved.isPresent());
		assertEquals("Acme Asset Management", resolved.get().getName());
	}

	@Test
	void findByLei_returnsEmptyWhenUnknown() {
		assertTrue(repository.findByLei("000000UNKNOWN0000000").isEmpty());
	}
}
