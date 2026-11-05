package com.javieraviles.splitthemonolith.repository;

import java.util.List;

import com.javieraviles.splitthemonolith.entity.Rfq;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RfqRepository extends JpaRepository<Rfq, Long> {

	List<Rfq> findByCounterpartyId(long counterpartyId);
}
