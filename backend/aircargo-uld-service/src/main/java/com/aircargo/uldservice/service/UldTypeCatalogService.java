package com.aircargo.uldservice.service;

import com.aircargo.uldservice.dto.UldTypeCatalogDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UldTypeCatalogService {

    List<UldTypeCatalogDTO> getAll(Boolean activeOnly);

    Optional<UldTypeCatalogDTO> getById(UUID id);

    UldTypeCatalogDTO create(UldTypeCatalogDTO dto);

    Optional<UldTypeCatalogDTO> update(UUID id, UldTypeCatalogDTO dto);

    boolean delete(UUID id);
}
