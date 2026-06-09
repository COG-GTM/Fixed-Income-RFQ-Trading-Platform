package com.javieraviles.splitthemonolith.restclient;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.dto.CounterpartyDto;
import com.javieraviles.splitthemonolith.dto.CreditDeductionRequest;
import com.javieraviles.splitthemonolith.exception.InsufficientCreditException;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;

@Component
public class CounterpartyServiceRestClient implements CounterpartyServiceProxy {

        @Value(value = "${counterpartyservice.url}")
        private String counterpartyServiceBaseUri;

        @Autowired
        private RestTemplate restTemplate;

        @Override
        public CounterpartyDto validateCounterparty(long counterpartyId) {
                try {
                        ResponseEntity<CounterpartyDto> response = restTemplate.exchange(
                                        counterpartyServiceBaseUri + "counterparties/" + counterpartyId + "/validate",
                                        HttpMethod.POST, new HttpEntity<>(getJsonHeaders()), CounterpartyDto.class);
                        return response.getBody();
                } catch (HttpClientErrorException.NotFound e) {
                        throw new ResourceNotFoundException();
                }
        }

        @Override
        public CounterpartyDto deductCredit(long counterpartyId, BigDecimal amount) {
                try {
                        CreditDeductionRequest request = new CreditDeductionRequest(amount);
                        HttpEntity<CreditDeductionRequest> requestEntity = new HttpEntity<>(request, getJsonHeaders());
                        ResponseEntity<CounterpartyDto> response = restTemplate.exchange(
                                        counterpartyServiceBaseUri + "counterparties/" + counterpartyId + "/deduct-credit",
                                        HttpMethod.POST, requestEntity, CounterpartyDto.class);
                        return response.getBody();
                } catch (HttpClientErrorException.NotFound e) {
                        throw new ResourceNotFoundException();
                } catch (HttpClientErrorException.BadRequest e) {
                        throw new InsufficientCreditException();
                }
        }

        private HttpHeaders getJsonHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return headers;
        }
}
