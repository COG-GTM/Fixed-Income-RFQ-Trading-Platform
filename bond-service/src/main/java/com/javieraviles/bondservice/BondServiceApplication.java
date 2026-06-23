package com.javieraviles.bondservice;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.javieraviles.bondservice.entity.Bond;
import com.javieraviles.bondservice.repository.BondRepository;

@SpringBootApplication
public class BondServiceApplication implements CommandLineRunner {

	private final BondRepository bondRepository;

	public BondServiceApplication(final BondRepository bondRepository) {
		this.bondRepository = bondRepository;
	}

	public static void main(String[] args) {
		SpringApplication.run(BondServiceApplication.class, args);
	}

	@Override
	public void run(String... args) {
		final Bond ustNote = new Bond("US912828YK15", "US Treasury",
				new BigDecimal("2.7500"), LocalDate.of(2030, 11, 15),
				new BigDecimal("100000000.00"));
		bondRepository.save(ustNote);
	}

}
