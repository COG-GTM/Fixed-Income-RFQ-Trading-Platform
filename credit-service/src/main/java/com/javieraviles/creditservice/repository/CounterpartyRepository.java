package com.javieraviles.creditservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.javieraviles.creditservice.entity.Counterparty;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {
}
