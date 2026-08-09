package com.javieraviles.creditservice;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.javieraviles.creditservice.entity.CreditAccount;
import com.javieraviles.creditservice.repository.CreditAccountRepository;

@SpringBootApplication
public class CreditServiceApplication implements CommandLineRunner {

	@Autowired
	private CreditAccountRepository repository;

	public static void main(String[] args) {
		SpringApplication.run(CreditServiceApplication.class, args);
	}

	@Override
	public void run(String... args) {
		repository.save(new CreditAccount("549300EXAMPLE12345678", "Acme Asset Management",
				new BigDecimal("50000000.00")));
	}
}
