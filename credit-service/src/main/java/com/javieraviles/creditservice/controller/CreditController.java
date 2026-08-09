package com.javieraviles.creditservice.controller;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.javieraviles.creditservice.dto.CreditCheckRequest;
import com.javieraviles.creditservice.dto.CreditCheckResponse;
import com.javieraviles.creditservice.entity.CreditAccount;
import com.javieraviles.creditservice.repository.CreditAccountRepository;
import com.javieraviles.creditservice.service.CreditService;

@RestController
class CreditController {

	@Autowired
	private CreditService creditService;

	@Autowired
	private CreditAccountRepository repository;

	@GetMapping("/credit-accounts")
	List<CreditAccount> getAll() {
		return repository.findAll();
	}

	@PostMapping("/credit-accounts")
	ResponseEntity<CreditAccount> createAccount(@Valid @RequestBody CreditAccount account) {
		return ResponseEntity.status(HttpStatus.CREATED).body(creditService.createAccount(account));
	}

	@GetMapping("/credit-accounts/{lei}")
	ResponseEntity<CreditAccount> getByLei(@PathVariable String lei) {
		return ResponseEntity.ok(creditService.findByLei(lei));
	}

	@PostMapping("/credit-checks")
	ResponseEntity<CreditCheckResponse> checkAndReserve(@Valid @RequestBody CreditCheckRequest request) {
		final CreditAccount account = creditService.reserveCredit(request.getLei(), request.getAmount());
		return ResponseEntity.ok(new CreditCheckResponse(account.getLei(), true, request.getAmount(),
				account.getAvailableCredit()));
	}

	@PostMapping("/credit-releases")
	ResponseEntity<CreditCheckResponse> release(@Valid @RequestBody CreditCheckRequest request) {
		final CreditAccount account = creditService.releaseCredit(request.getLei(), request.getAmount());
		return ResponseEntity.ok(new CreditCheckResponse(account.getLei(), true, request.getAmount().negate(),
				account.getAvailableCredit()));
	}
}
