package com.javieraviles.counterpartyservice.restclient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.counterpartyservice.dto.TradeConfirmationDto;

@Component
public class TradeConfirmationMicroserviceClient {

        @Value(value = "${confirmationms.url}")
        private String confirmationMsBaseUri;

        @Autowired
        private RestTemplate restTemplate;

        public void sendConfirmation(final TradeConfirmationDto confirmation) {
                HttpEntity<TradeConfirmationDto> requestEntity = new HttpEntity<>(confirmation, getJsonHeaders());
                restTemplate.exchange(confirmationMsBaseUri + "confirmations/",
                                HttpMethod.POST, requestEntity, String.class);
        }

        private HttpHeaders getJsonHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return headers;
        }

}
