package com.javieraviles.splitthemonolith.repository;

import java.util.Optional;

import javax.persistence.LockModeType;

import com.javieraviles.splitthemonolith.entity.Counterparty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from counterparties e where e.id = :id")
	Optional<Counterparty> findByIdForUpdate(@Param("id") Long id);
}
