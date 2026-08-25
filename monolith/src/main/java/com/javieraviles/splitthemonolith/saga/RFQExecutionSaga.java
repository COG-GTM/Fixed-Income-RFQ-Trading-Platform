package com.javieraviles.splitthemonolith.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.javieraviles.splitthemonolith.dto.CreditReservationDto;
import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.restclient.CreditServiceClient;
import com.javieraviles.splitthemonolith.service.RfqBookingService;

/**
 * Executes an RFQ across the two aggregates that no longer share a database:
 * counterparty credit (owned by the credit service) and bond notional (still
 * owned by the monolith).
 *
 * Since a single @Transactional method cannot span both, the saga runs
 * reserve -> book -> confirm, and compensates the remote reservation whenever
 * the local booking fails.
 */
@Component
public class RFQExecutionSaga {

	Logger logger = LoggerFactory.getLogger(RFQExecutionSaga.class);

	@Autowired
	private BondRepository bondRepository;

	@Autowired
	private CreditServiceClient creditServiceClient;

	@Autowired
	private RfqBookingService rfqBookingService;

	public Rfq executeRfq(final RfqDto rfqDto) {

		// Fail fast on unknown bonds so that no credit is reserved for them.
		if (!bondRepository.existsById(rfqDto.getBondId())) {
			throw new ResourceNotFoundException();
		}

		// Step 1: reserve credit remotely. Throws ResourceNotFoundException for an
		// unknown counterparty and InsufficientCreditException when credit is short.
		final CreditReservationDto reservation = creditServiceClient
				.reserveCredit(rfqDto.getCounterpartyId(), rfqDto.getExecutionPrice());

		final Rfq rfq;
		try {
			// Step 2: deduct notional and persist the RFQ in the local transaction.
			rfq = rfqBookingService.bookRfq(rfqDto);
		} catch (final RuntimeException e) {
			// Compensation: the local step rolled back, so the credit the credit
			// service already deducted has to be given back before failing.
			compensate(rfqDto.getCounterpartyId(), reservation.getId());
			throw e;
		}

		// Step 3: the local transaction committed, so the reservation is confirmed
		// as consumed by this RFQ and no compensation is due any more.
		logger.info("RFQ {} executed, credit reservation {} confirmed for counterparty {}", rfq.getId(),
				reservation.getId(), rfqDto.getCounterpartyId());
		return rfq;
	}

	private void compensate(final long counterpartyId, final long reservationId) {
		try {
			creditServiceClient.releaseCreditReservation(counterpartyId, reservationId);
			logger.info("Credit reservation {} released for counterparty {}", reservationId, counterpartyId);
		} catch (final RuntimeException e) {
			// Compensation is idempotent on the credit service, so it can be retried
			// out of band. Never mask the failure that triggered it.
			logger.error("Could not release credit reservation {} for counterparty {}", reservationId, counterpartyId,
					e);
		}
	}
}
