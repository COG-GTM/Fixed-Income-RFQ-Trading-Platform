package com.javieraviles.splitthemonolith.restclient;

import java.math.BigDecimal;

import com.javieraviles.splitthemonolith.dto.CounterpartyDto;

public interface CounterpartyServiceProxy {

        CounterpartyDto validateCounterparty(long counterpartyId);

        CounterpartyDto deductCredit(long counterpartyId, BigDecimal amount);
}
