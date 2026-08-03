package com.javieraviles.confirmationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.javieraviles.confirmationservice.entity.TradeConfirmation;

public interface TradeConfirmationRepository extends JpaRepository<TradeConfirmation, Long> {
}
