package com.javieraviles.bondservice.repository;

import com.javieraviles.bondservice.entity.Bond;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BondRepository extends JpaRepository<Bond, Long> {
}
