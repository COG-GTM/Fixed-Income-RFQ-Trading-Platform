package com.javieraviles.rfqservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import com.javieraviles.rfqservice.dto.RfqDto;
import com.javieraviles.rfqservice.entity.Rfq;
import com.javieraviles.rfqservice.entity.RfqStatus;
import com.javieraviles.rfqservice.entity.Side;
import com.javieraviles.rfqservice.exception.InsufficientCreditException;
import com.javieraviles.rfqservice.repository.RfqRepository;
import com.javieraviles.rfqservice.restclient.BondServiceClient;
import com.javieraviles.rfqservice.restclient.CounterpartyServiceClient;
import com.javieraviles.rfqservice.saga.RFQExecutionSaga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RFQExecutionSagaTest {

	@Mock
	private RfqRepository rfqRepository;

	@Mock
	private BondServiceClient bondServiceClient;

	@Mock
	private CounterpartyServiceClient counterpartyServiceClient;

	@InjectMocks
	private RFQExecutionSaga saga;

	private RfqDto sampleDto() {
		final RfqDto dto = new RfqDto();
		dto.setBondId(1L);
		dto.setCounterpartyId(2L);
		dto.setNotionalAmount(new BigDecimal("1000000.00"));
		dto.setExecutionPrice(new BigDecimal("998750.00"));
		dto.setSide(Side.BUY);
		return dto;
	}

	@Test
	public void whenBothStepsSucceed_thenRfqSavedAsExecuted() {
		final RfqDto dto = sampleDto();
		when(rfqRepository.save(any(Rfq.class))).thenAnswer(invocation -> invocation.getArgument(0));

		final Rfq result = saga.executeRfq(dto);

		verify(bondServiceClient).deductNotional(1L, new BigDecimal("1000000.00"));
		verify(counterpartyServiceClient).deductCredit(2L, new BigDecimal("998750.00"));
		verify(bondServiceClient, never()).addNotional(any(Long.class), any(BigDecimal.class));
		assertThat(result.getStatus()).isEqualTo(RfqStatus.EXECUTED);
	}

	@Test
	public void whenCreditDeductionFails_thenNotionalIsCompensatedAndRfqNotSaved() {
		final RfqDto dto = sampleDto();
		doThrow(new InsufficientCreditException()).when(counterpartyServiceClient)
				.deductCredit(eq(2L), any(BigDecimal.class));

		assertThatThrownBy(() -> saga.executeRfq(dto)).isInstanceOf(InsufficientCreditException.class);

		verify(bondServiceClient).deductNotional(1L, new BigDecimal("1000000.00"));
		verify(bondServiceClient).addNotional(1L, new BigDecimal("1000000.00"));
		verify(rfqRepository, never()).save(any(Rfq.class));
	}
}
