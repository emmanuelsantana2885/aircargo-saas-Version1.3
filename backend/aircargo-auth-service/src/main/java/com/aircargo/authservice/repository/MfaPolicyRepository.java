package com.aircargo.authservice.repository;

import com.aircargo.authservice.entity.MfaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaPolicyRepository extends JpaRepository<MfaPolicy, Integer> {
}