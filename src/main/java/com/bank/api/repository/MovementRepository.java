package com.bank.api.repository;

import com.bank.api.domain.entity.Movement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    List<Movement> findByAccountIdOrderByCreatedAtDesc(Long accountId);
}
