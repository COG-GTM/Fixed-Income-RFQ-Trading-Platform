package com.javieraviles.creditservice.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.javieraviles.creditservice.entity.CreditAccount;
import com.javieraviles.creditservice.exception.CreditAccountNotFoundException;
import com.javieraviles.creditservice.repository.CreditAccountRepository;

@Service
public class CreditService {

	@Autowired
	private CreditAccountRepository repository;

	@Transactional
	public CreditAccount reserveCredit(final String lei, final BigDecimal amount) {
		final CreditAccount account = findByLei(lei);
		account.reserve(amount);
		return repository.save(account);
	}

	@Transactional
	public CreditAccount releaseCredit(final String lei, final BigDecimal amount) {
		final CreditAccount account = findByLei(lei);
		account.release(amount);
		return repository.save(account);
	}

	@Transactional(readOnly = true)
	public CreditAccount findByLei(final String lei) {
		return repository.findByLei(lei).orElseThrow(CreditAccountNotFoundException::new);
	}

	@Transactional
	public CreditAccount createAccount(final CreditAccount account) {
		if (account.getAvailableCredit() == null) {
			account.setAvailableCredit(account.getCreditLimit());
		}
		return repository.save(account);
	}
}
