package com.javieraviles.splitthemonolith;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.entity.Counterparty;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.entity.RfqStatus;
import com.javieraviles.splitthemonolith.entity.Side;
import com.javieraviles.splitthemonolith.repository.BondRepository;
import com.javieraviles.splitthemonolith.repository.CounterpartyRepository;
import com.javieraviles.splitthemonolith.repository.RfqRepository;

@SpringBootApplication
public class SplitTheMonolithApplication implements CommandLineRunner {

	@Autowired
	CounterpartyRepository counterpartyRepository;

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
		counterpartyRepository.save(acme);
		bondRepository.save(ustNote);
		rfqRepository.save(new Rfq(acme, ustNote, new BigDecimal("5000000.00"),
				Side.BUY, RfqStatus.EXECUTED, new BigDecimal("4987500.00")));
	}

	@Value("${confirmationms.connectTimeoutMs:2000}")
	private int confirmationConnectTimeoutMs;

	@Value("${confirmationms.readTimeoutMs:2000}")
	private int confirmationReadTimeoutMs;

	@Bean
	RestTemplate restTemplate() {
		CloseableHttpClient httpClient = HttpClientBuilder.create().build();
		return createRestTemplate(httpClient);
	}

	private RestTemplate createRestTemplate(final CloseableHttpClient httpClient) {
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
		requestFactory.setConnectTimeout(confirmationConnectTimeoutMs);
		requestFactory.setReadTimeout(confirmationReadTimeoutMs);
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		return restTemplate;
	}

}
