package com.javieraviles.creditservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.javieraviles.creditservice.dto.CreditReservationDto;
import com.javieraviles.creditservice.entity.Counterparty;
import com.javieraviles.creditservice.entity.CreditReservation;
import com.javieraviles.creditservice.entity.ReservationStatus;
import com.javieraviles.creditservice.exception.ResourceNotFoundException;
import com.javieraviles.creditservice.repository.CounterpartyRepository;
import com.javieraviles.creditservice.repository.CreditReservationRepository;

@RestController
class CreditReservationController {

	@Autowired
	private CounterpartyRepository counterpartyRepository;

	@Autowired
	private CreditReservationRepository reservationRepository;

	/**
	 * Reserves credit for a counterparty. Credit is deducted as soon as the
	 * reservation is granted, so a granted reservation cannot be outbid by a
	 * concurrent one. Responds 409 when the counterparty has not enough credit.
	 */
	@PostMapping("/counterparties/{id}/reservations")
	@Transactional
	ResponseEntity<CreditReservationDto> reserve(@PathVariable("id") Long id,
			@RequestBody CreditReservationDto reservationRequest) {
		final Counterparty counterparty = counterpartyRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException());
		counterparty.deductCredit(reservationRequest.getAmount());
		counterpartyRepository.save(counterparty);
		final CreditReservation reservation = reservationRepository
				.save(new CreditReservation(counterparty.getId(), reservationRequest.getAmount()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toDto(reservation));
	}

	@GetMapping("/counterparties/{id}/reservations/{reservationId}")
	ResponseEntity<CreditReservationDto> getOne(@PathVariable("id") Long id,
			@PathVariable("reservationId") Long reservationId) {
		return ResponseEntity.ok(toDto(findReservation(id, reservationId)));
	}

	/**
	 * Compensating action for {@link #reserve}: gives the reserved credit back to
	 * the counterparty. Releasing an already released reservation is a no-op, so
	 * the caller can retry compensation safely.
	 */
	@DeleteMapping("/counterparties/{id}/reservations/{reservationId}")
	@Transactional
	ResponseEntity<Void> release(@PathVariable("id") Long id, @PathVariable("reservationId") Long reservationId) {
		final CreditReservation reservation = findReservation(id, reservationId);
		if (reservation.getStatus() == ReservationStatus.RESERVED) {
			final Counterparty counterparty = counterpartyRepository.findById(reservation.getCounterpartyId())
					.orElseThrow(() -> new ResourceNotFoundException());
			counterparty.addCredit(reservation.getAmount());
			counterpartyRepository.save(counterparty);
			reservation.release();
			reservationRepository.save(reservation);
		}
		return ResponseEntity.noContent().build();
	}

	private CreditReservation findReservation(final Long counterpartyId, final Long reservationId) {
		final CreditReservation reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new ResourceNotFoundException());
		if (reservation.getCounterpartyId() != counterpartyId) {
			throw new ResourceNotFoundException();
		}
		return reservation;
	}

	private CreditReservationDto toDto(final CreditReservation reservation) {
		return new CreditReservationDto(reservation.getId(), reservation.getCounterpartyId(),
				reservation.getAmount(), reservation.getStatus());
	}
}
