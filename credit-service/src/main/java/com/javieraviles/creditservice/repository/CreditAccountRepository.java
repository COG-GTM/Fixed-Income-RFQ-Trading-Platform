package com.javieraviles.creditservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.javieraviles.creditservice.entity.CreditAccount;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {

	Optional<CreditAccount> findByLei(String lei);
}
