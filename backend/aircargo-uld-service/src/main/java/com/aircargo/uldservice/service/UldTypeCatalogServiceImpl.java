package com.aircargo.uldservice.service;

import com.aircargo.uldservice.dto.UldTypeCatalogDTO;
import com.aircargo.uldservice.entity.UldTypeCatalog;
import com.aircargo.uldservice.repository.UldTypeCatalogRepository;
import com.aircargo.uldservice.util.UldTypes;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UldTypeCatalogServiceImpl implements UldTypeCatalogService {

    private final UldTypeCatalogRepository repository;

    public UldTypeCatalogServiceImpl(UldTypeCatalogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Cacheable(value = "uld-type-catalog", key = "#activeOnly != null && #activeOnly ? 'active' : 'all'")
    public List<UldTypeCatalogDTO> getAll(Boolean activeOnly) {
        List<UldTypeCatalog> items = Boolean.TRUE.equals(activeOnly)
                ? repository.findByIsActiveTrueOrderBySortOrderAscCodeAsc()
                : repository.findAllByOrderBySortOrderAscCodeAsc();
        return items.stream().map(UldTypeCatalogDTO::fromEntity).toList();
    }

    @Override
    public Optional<UldTypeCatalogDTO> getById(UUID id) {
        return repository.findById(id).map(UldTypeCatalogDTO::fromEntity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "uld-type-catalog", allEntries = true)
    public UldTypeCatalogDTO create(UldTypeCatalogDTO dto) {
        String code = UldTypes.normalize(dto.getCode());
        if (!UldTypes.isValid(code)) {
            throw new IllegalArgumentException("Código de tipo ULD inválido: use 3-5 caracteres alfanuméricos (ej. PMC, AKE)");
        }
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("El tipo ULD " + code + " ya existe en el catálogo");
        }
        dto.setCode(code);
        if (dto.getSortOrder() == null) dto.setSortOrder(100);
        return UldTypeCatalogDTO.fromEntity(repository.save(UldTypeCatalogDTO.toEntity(dto)));
    }

    @Override
    @Transactional
    @CacheEvict(value = "uld-type-catalog", allEntries = true)
    public Optional<UldTypeCatalogDTO> update(UUID id, UldTypeCatalogDTO dto) {
        return repository.findById(id).map(existing -> {
            if (dto.getCode() != null) {
                String code = UldTypes.normalize(dto.getCode());
                if (!UldTypes.isValid(code)) {
                    throw new IllegalArgumentException("Código de tipo ULD inválido: use 3-5 caracteres alfanuméricos");
                }
                repository.findByCodeIgnoreCase(code)
                        .filter(other -> !other.getId().equals(id))
                        .ifPresent(other -> { throw new IllegalArgumentException("El tipo ULD " + code + " ya existe en el catálogo"); });
                existing.setCode(code);
            }
            if (dto.getDescription() != null) existing.setDescription(dto.getDescription());
            if (dto.getIsActive() != null) existing.setIsActive(dto.getIsActive());
            if (dto.getSortOrder() != null) existing.setSortOrder(dto.getSortOrder());
            return UldTypeCatalogDTO.fromEntity(repository.save(existing));
        });
    }

    @Override
    @Transactional
    @CacheEvict(value = "uld-type-catalog", allEntries = true)
    public boolean delete(UUID id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
