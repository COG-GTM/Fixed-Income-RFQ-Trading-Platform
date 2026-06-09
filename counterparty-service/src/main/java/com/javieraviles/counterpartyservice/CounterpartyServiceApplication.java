package com.javieraviles.counterpartyservice;

import java.math.BigDecimal;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.counterpartyservice.entity.Counterparty;
import com.javieraviles.counterpartyservice.repository.CounterpartyRepository;

@SpringBootApplication
public class CounterpartyServiceApplication implements CommandLineRunner {

        @Autowired
        CounterpartyRepository counterpartyRepository;

        public static void main(String[] args) {
                SpringApplication.run(CounterpartyServiceApplication.class, args);
        }

        @Override
        public void run(String... args) {
                counterpartyRepository.save(new Counterparty("Acme Asset Management",
                                "549300EXAMPLE12345678", new BigDecimal("50000000.00")));
        }

        @Bean
        RestTemplate restTemplate() {
                CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                HttpComponentsClientHttpRequestFactory requestFactory =
                                new HttpComponentsClientHttpRequestFactory(httpClient);
                return new RestTemplate(requestFactory);
        }
}
