package com.aircargo.uldservice.repository;

import com.aircargo.uldservice.entity.UldFlightOperator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UldFlightOperatorRepository extends JpaRepository<UldFlightOperator, UUID> {

    Optional<UldFlightOperator> findByUldIdAndFlightId(UUID uldId, UUID flightId);

    List<UldFlightOperator> findByUldIdInAndFlightId(Collection<UUID> uldIds, UUID flightId);
}