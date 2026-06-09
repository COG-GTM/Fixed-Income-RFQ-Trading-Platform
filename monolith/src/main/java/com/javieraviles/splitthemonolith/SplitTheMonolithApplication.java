package com.javieraviles.splitthemonolith;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

@SpringBootApplication
public class SplitTheMonolithApplication implements CommandLineRunner {

        @Autowired
        BondRepository bondRepository;

        @Autowired
        RfqRepository rfqRepository;

        public static void main(String[] args) {
                SpringApplication.run(SplitTheMonolithApplication.class, args);
        }

        @Override
        public void run(String... args) {
                final Bond ustNote = new Bond("US912828YK15", "US Treasury",
                                new BigDecimal("2.7500"), LocalDate.of(2030, 11, 15),
                                new BigDecimal("100000000.00"));
                bondRepository.save(ustNote);
                rfqRepository.save(new Rfq(1L, ustNote, new BigDecimal("5000000.00"),
                                Side.BUY, RfqStatus.EXECUTED, new BigDecimal("4987500.00")));
        }

        @Bean
        RestTemplate restTemplate() {
                CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                return createRestTemplate(httpClient);
        }

        private RestTemplate createRestTemplate(final CloseableHttpClient httpClient) {
                HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
                RestTemplate restTemplate = new RestTemplate(requestFactory);
                return restTemplate;
        }

}
