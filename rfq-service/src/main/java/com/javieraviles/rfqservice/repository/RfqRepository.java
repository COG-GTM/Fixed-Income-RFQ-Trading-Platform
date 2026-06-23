package com.javieraviles.rfqservice.repository;

import com.javieraviles.rfqservice.entity.Rfq;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RfqRepository extends JpaRepository<Rfq, Long> {
}
