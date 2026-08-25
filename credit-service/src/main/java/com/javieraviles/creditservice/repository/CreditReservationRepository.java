package com.javieraviles.creditservice.repository;

import com.javieraviles.creditservice.entity.CreditReservation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditReservationRepository extends JpaRepository<CreditReservation, Long> {
}
