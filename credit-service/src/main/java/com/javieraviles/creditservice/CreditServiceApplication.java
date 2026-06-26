package com.javieraviles.creditservice;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.javieraviles.creditservice.entity.Counterparty;
import com.javieraviles.creditservice.repository.CounterpartyRepository;

@SpringBootApplication
public class CreditServiceApplication implements CommandLineRunner {

	private final CounterpartyRepository counterpartyRepository;

	public CreditServiceApplication(final CounterpartyRepository counterpartyRepository) {
		this.counterpartyRepository = counterpartyRepository;
	}

	public static void main(String[] args) {
		SpringApplication.run(CreditServiceApplication.class, args);
	}

	@Override
	public void run(String... args) {
		// Seed data mirrors the monolith so the extracted service is a drop-in
		// owner of counterparty credit for local/demo runs.
		counterpartyRepository.save(new Counterparty("Acme Asset Management",
				"549300EXAMPLE12345678", new BigDecimal("50000000.00")));
	}
}
