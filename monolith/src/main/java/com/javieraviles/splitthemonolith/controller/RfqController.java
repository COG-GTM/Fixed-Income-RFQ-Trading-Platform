package com.javieraviles.splitthemonolith.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.javieraviles.splitthemonolith.dto.RfqDto;
import com.javieraviles.splitthemonolith.entity.Rfq;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.RfqRepository;
import com.javieraviles.splitthemonolith.saga.RFQExecutionSaga;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "RFQ", description = "RFQ (trade execution) capability: atomic Request-for-Quote execution")
class RfqController {

	@Autowired
	private RfqRepository repository;

	@Autowired
	private RFQExecutionSaga rfqExecutionSaga;

	@Operation(summary = "List all RFQs")
	@GetMapping("/rfqs")
	List<RfqDto> getAll() {
		final List<Rfq> rfqs = repository.findAll();
		return rfqs.stream().map(this::toDto).collect(Collectors.toList());
	}

	@Operation(summary = "Execute an RFQ", description = "Deducts bond notional and counterparty credit in one transaction, persisting the RFQ as EXECUTED.")
	@PostMapping("/rfqs")
	ResponseEntity<RfqDto> createRfq(@RequestBody RfqDto newRfq) {
		return ResponseEntity.status(HttpStatus.CREATED).body(toDto(rfqExecutionSaga.executeRfq(newRfq)));
	}

	@Operation(summary = "Get an RFQ by ID")
	@GetMapping("/rfqs/{id}")
	ResponseEntity<RfqDto> getOne(@PathVariable Long id) {
		final Rfq rfq = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException());
		return ResponseEntity.ok(toDto(rfq));
	}

	@Operation(summary = "Delete an RFQ")
	@DeleteMapping("/rfqs/{id}")
	void deleteRfq(@PathVariable Long id) {
		repository.deleteById(id);
	}

	private RfqDto toDto(final Rfq rfq) {
		return new RfqDto(rfq.getId(), rfq.getCounterparty().getId(), rfq.getBond().getId(),
				rfq.getNotionalAmount(), rfq.getSide(), rfq.getStatus(),
				rfq.getExecutionPrice(), rfq.getCreatedAt());
	}
}
