package com.javieraviles.splitthemonolith.controller;

import java.util.List;
import java.util.stream.Collectors;

import javax.validation.Valid;

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
import com.javieraviles.splitthemonolith.security.AccessPolicy;

@RestController
class RfqController {

	@Autowired
	private RfqRepository repository;

	@Autowired
	private RFQExecutionSaga rfqExecutionSaga;

	@Autowired
	private AccessPolicy accessPolicy;

	@GetMapping("/rfqs")
	List<RfqDto> getAll() {
		final List<Rfq> rfqs = accessPolicy.isOperator() ? repository.findAll()
				: repository.findByCounterpartyId(accessPolicy.ownCounterparty().getId());
		return rfqs.stream().map(this::toDto).collect(Collectors.toList());
	}

	@PostMapping("/rfqs")
	ResponseEntity<RfqDto> createRfq(@Valid @RequestBody RfqDto newRfq) {
		if (!accessPolicy.isOperator()) {
			final long ownId = accessPolicy.ownCounterparty().getId();
			if (newRfq.getCounterpartyId() != 0) {
				accessPolicy.checkCounterpartyAccess(newRfq.getCounterpartyId());
			}
			newRfq.setCounterpartyId(ownId);
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(toDto(rfqExecutionSaga.executeRfq(newRfq)));
	}

	@GetMapping("/rfqs/{id}")
	ResponseEntity<RfqDto> getOne(@PathVariable Long id) {
		final Rfq rfq = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException());
		accessPolicy.checkCounterpartyAccess(rfq.getCounterparty().getId());
		return ResponseEntity.ok(toDto(rfq));
	}

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
