package com.javieraviles.confirmationservice.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.confirmationservice.dto.TradeConfirmationDto;
import com.javieraviles.confirmationservice.entity.TradeConfirmation;
import com.javieraviles.confirmationservice.exception.ResourceNotFoundException;
import com.javieraviles.confirmationservice.repository.TradeConfirmationRepository;

@Service
public class TradeConfirmationService {

	private final Logger logger = LoggerFactory.getLogger(TradeConfirmationService.class);

	@Autowired
	private TradeConfirmationRepository repository;

	@Transactional
	public TradeConfirmation confirm(final TradeConfirmationDto confirmation) {
		final TradeConfirmation stored = repository
				.save(new TradeConfirmation(confirmation.getCounterpartyName(), confirmation.getCreditAmount()));
		logger.info("Trade confirmation: counterparty {} credit updated, amount {}",
				stored.getCounterpartyName(), stored.getCreditAmount());
		return stored;
	}

	public List<TradeConfirmation> findAll() {
		return repository.findAll();
	}

	public TradeConfirmation findById(final Long id) {
		return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException());
	}
}
