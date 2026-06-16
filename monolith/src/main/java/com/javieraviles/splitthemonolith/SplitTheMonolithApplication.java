package com.javieraviles.splitthemonolith;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.counterpartycredit.CounterpartyCreditService;
import com.javieraviles.counterpartycredit.domain.Counterparty;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

// Component/entity/repository scanning is broadened to cover the extracted
// Counterparty Credit bounded context, which lives outside the monolith's base
// package. This is the in-process composition root of the strangler-fig step.
@SpringBootApplication(scanBasePackages = { "com.javieraviles.splitthemonolith",
		"com.javieraviles.counterpartycredit" })
@EntityScan(basePackages = { "com.javieraviles.splitthemonolith", "com.javieraviles.counterpartycredit" })
@EnableJpaRepositories(basePackages = { "com.javieraviles.splitthemonolith", "com.javieraviles.counterpartycredit" })
public class SplitTheMonolithApplication implements CommandLineRunner {

	@Autowired
	CounterpartyCreditService counterpartyCreditService;

	@Autowired
	BondRepository bondRepository;

	@Autowired
	RfqRepository rfqRepository;

	public static void main(String[] args) {
		SpringApplication.run(SplitTheMonolithApplication.class, args);
	}

	@Override
	public void run(String... args) {
		final Counterparty acme = new Counterparty("Acme Asset Management",
				"549300EXAMPLE12345678", new BigDecimal("50000000.00"));
		final Bond ustNote = new Bond("US912828YK15", "US Treasury",
				new BigDecimal("2.7500"), LocalDate.of(2030, 11, 15),
				new BigDecimal("100000000.00"));
		final Counterparty savedAcme = counterpartyCreditService.create(acme);
		bondRepository.save(ustNote);
		rfqRepository.save(new Rfq(savedAcme, ustNote, new BigDecimal("5000000.00"),
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
