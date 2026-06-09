package com.javieraviles.counterpartyservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.javieraviles.counterpartyservice.dto.TradeConfirmationDto;

@Component
public class TradeConfirmationService {

        Logger logger = LoggerFactory.getLogger(TradeConfirmationService.class);

        public void sendTradeConfirmation(final TradeConfirmationDto confirmation) {
                logger.info("Trade confirmation: counterparty {} credit updated, amount {}",
                                confirmation.getCounterpartyName(), confirmation.getCreditAmount());
        }
}
