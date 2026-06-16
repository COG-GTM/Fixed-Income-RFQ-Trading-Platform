package com.javieraviles.counterpartycredit.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.javieraviles.counterpartycredit.domain.Counterparty;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

	Optional<Counterparty> findByLei(String lei);
}
