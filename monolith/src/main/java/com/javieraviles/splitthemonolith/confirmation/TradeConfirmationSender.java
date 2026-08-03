package com.javieraviles.splitthemonolith.confirmation;

import com.javieraviles.splitthemonolith.dto.TradeConfirmationDto;

/**
 * Seam through which the monolith delivers trade confirmations. The
 * {@code use.confirmation.service} toggle selects the implementation: the
 * in-process legacy one, or the extracted confirmation-service.
 */
public interface TradeConfirmationSender {

	void sendTradeConfirmation(TradeConfirmationDto confirmation);
}
