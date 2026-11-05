package com.javieraviles.splitthemonolith.repository;

import java.util.Optional;

import javax.persistence.LockModeType;

import com.javieraviles.splitthemonolith.entity.Bond;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BondRepository extends JpaRepository<Bond, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from bonds e where e.id = :id")
	Optional<Bond> findByIdForUpdate(@Param("id") Long id);
}
