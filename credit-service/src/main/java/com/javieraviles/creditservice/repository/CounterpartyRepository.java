package com.javieraviles.creditservice.repository;

import com.javieraviles.creditservice.entity.Counterparty;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {
}
