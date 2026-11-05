package com.javieraviles.splitthemonolith.repository;

import java.util.List;

import com.javieraviles.splitthemonolith.entity.Counterparty;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

	List<Counterparty> findByLei(String lei);
}
