package com.javieraviles.creditservice;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.javieraviles.creditservice.entity.Counterparty;
import com.javieraviles.creditservice.repository.CounterpartyRepository;

@SpringBootApplication
public class CreditServiceApplication implements CommandLineRunner {

	@Autowired
	CounterpartyRepository counterpartyRepository;

	public static void main(String[] args) {
		SpringApplication.run(CreditServiceApplication.class, args);
	}

	@Override
	public void run(String... args) {
		counterpartyRepository.save(new Counterparty("Acme Asset Management",
				"549300EXAMPLE12345678", new BigDecimal("50000000.00")));
	}

}
