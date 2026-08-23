package com.aircargo.uldservice.repository;

import com.aircargo.uldservice.entity.UldTypeCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UldTypeCatalogRepository extends JpaRepository<UldTypeCatalog, UUID> {
    List<UldTypeCatalog> findAllByOrderBySortOrderAscCodeAsc();

    List<UldTypeCatalog> findByIsActiveTrueOrderBySortOrderAscCodeAsc();

    Optional<UldTypeCatalog> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
