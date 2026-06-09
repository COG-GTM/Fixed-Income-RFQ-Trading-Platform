package com.javieraviles.counterpartyservice.repository;

import com.javieraviles.counterpartyservice.entity.Counterparty;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {
}
