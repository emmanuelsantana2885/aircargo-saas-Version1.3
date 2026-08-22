package com.aircargo.authservice.repository;

import com.aircargo.authservice.entity.CommodityTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommodityTypeRepository extends JpaRepository<CommodityTypeEntity, UUID> {

    Optional<CommodityTypeEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<CommodityTypeEntity> findByIsActiveTrueOrderBySortOrderAscCodeAsc();

    List<CommodityTypeEntity> findAllByOrderBySortOrderAscCodeAsc();
}
