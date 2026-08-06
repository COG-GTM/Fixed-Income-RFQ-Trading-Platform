package com.javieraviles.splitthemonolith.confirmation;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * Outbound port for the trade confirmation capability.
 *
 * This is the extraction seam for the strangler migration: the monolith only
 * knows this interface, so an implementation can be swapped (in-process,
 * remote microservice, dual-write, ...) without touching calling code.
 */
public interface TradeConfirmationPort {

	void sendConfirmation(TradeConfirmationDto confirmation);
}
