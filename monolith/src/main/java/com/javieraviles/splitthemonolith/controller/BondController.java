package com.javieraviles.splitthemonolith.controller;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.javieraviles.splitthemonolith.dto.AmountUpdateDto;
import com.javieraviles.splitthemonolith.dto.OperationEnum;
import com.javieraviles.splitthemonolith.entity.Bond;
import com.javieraviles.splitthemonolith.exception.ResourceNotFoundException;
import com.javieraviles.splitthemonolith.repository.BondRepository;

@RestController
class BondController {

	@Autowired
	private BondRepository repository;

	@GetMapping("/bonds")
	List<Bond> getAll() {
		return repository.findAll();
	}

	@PostMapping("/bonds")
	ResponseEntity<Bond> createBond(@Valid @RequestBody Bond newBond) {
		return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(newBond));
	}

	@GetMapping("/bonds/{id}")
	ResponseEntity<Bond> getOne(@PathVariable Long id) {
		final Bond bond = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException());
		return ResponseEntity.ok(bond);
	}

	@PutMapping("/bonds/{id}")
	ResponseEntity<Bond> updateBond(@Valid @RequestBody Bond updatedBond, @PathVariable Long id) {
		final Bond bond = repository.findById(id).map(b -> {
			b.setIsin(updatedBond.getIsin());
			b.setIssuer(updatedBond.getIssuer());
			b.setCouponRate(updatedBond.getCouponRate());
			b.setMaturityDate(updatedBond.getMaturityDate());
			b.setAvailableNotional(updatedBond.getAvailableNotional());
			return repository.save(b);
		}).orElseThrow(() -> new ResourceNotFoundException());
		return ResponseEntity.ok(bond);
	}

	@RequestMapping(value = "/bonds/{id}", method = RequestMethod.PATCH, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Bond> partialUpdateGeneric(@Valid @RequestBody AmountUpdateDto notionalUpdate,
			@PathVariable("id") Long id) {
		final Bond bond = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException());
		if (notionalUpdate.getOperation() == OperationEnum.ADD) {
			bond.addNotional(notionalUpdate.getAmount());
		} else {
			bond.deductNotional(notionalUpdate.getAmount());
		}
		return ResponseEntity.ok(repository.save(bond));
	}

	@DeleteMapping("/bonds/{id}")
	void deleteBond(@PathVariable Long id) {
		repository.deleteById(id);
	}
}
