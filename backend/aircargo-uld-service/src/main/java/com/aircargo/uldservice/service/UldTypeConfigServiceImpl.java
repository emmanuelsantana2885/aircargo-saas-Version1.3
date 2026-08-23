package com.aircargo.uldservice.service;

import com.aircargo.uldservice.dto.UldTypeConfigDTO;
import com.aircargo.uldservice.entity.UldTypeConfig;
import com.aircargo.uldservice.repository.UldTypeConfigRepository;
import com.aircargo.uldservice.util.UldTypes;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UldTypeConfigServiceImpl implements UldTypeConfigService {

    private final UldTypeConfigRepository repository;

    public UldTypeConfigServiceImpl(UldTypeConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Cacheable(value = "uld-type-config", key = "#airlineId != null ? #airlineId : 'all'")
    public List<UldTypeConfigDTO> getAll(UUID airlineId) {
        List<UldTypeConfig> configs = airlineId != null
                ? repository.findByAirlineId(airlineId)
                : repository.findAll();
        return configs.stream().map(UldTypeConfigDTO::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "uld-type-config", key = "#id")
    public Optional<UldTypeConfigDTO> getById(UUID id) {
        return repository.findById(id).map(UldTypeConfigDTO::fromEntity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "uld-type-config", allEntries = true)
    public UldTypeConfigDTO create(UldTypeConfigDTO dto) {
        if (dto.getAirlineId() == null) {
            throw new IllegalArgumentException("airlineId es obligatorio");
        }
        if (dto.getUldType() == null) {
            throw new IllegalArgumentException("uldType es obligatorio");
        }
        if (!UldTypes.isValid(dto.getUldType())) {
            throw new IllegalArgumentException("uldType inválido: use 3-5 caracteres alfanuméricos (ej. PMC, AKE). Regístrelo primero en el catálogo de tipos ULD");
        }
        UldTypeConfig entity = UldTypeConfigDTO.toEntity(dto);
        entity.setId(UUID.randomUUID());
        return UldTypeConfigDTO.fromEntity(repository.save(entity));
    }

    @Override
    @Transactional
    @CacheEvict(value = "uld-type-config", allEntries = true)
    public Optional<UldTypeConfigDTO> update(UUID id, UldTypeConfigDTO dto) {
        return repository.findById(id)
                .map(existing -> {
                    UldTypeConfig updated = UldTypeConfigDTO.toEntity(dto);
                    updated.setId(existing.getId());
                    return UldTypeConfigDTO.fromEntity(repository.save(updated));
                });
    }

    @Override
    @Transactional
    @CacheEvict(value = "uld-type-config", allEntries = true)
    public boolean delete(UUID id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    @Override
    @Transactional
    @CacheEvict(value = "uld-type-config", allEntries = true)
    public List<UldTypeConfigDTO> replaceAllForAirline(UUID airlineId, List<UldTypeConfigDTO> dtos) {
        if (airlineId == null) {
            throw new IllegalArgumentException("airlineId es obligatorio");
        }
        repository.deleteByAirlineId(airlineId);
        return dtos.stream()
                .map(dto -> {
                    if (!UldTypes.isValid(dto.getUldType())) {
                        throw new IllegalArgumentException("uldType inválido: " + dto.getUldType());
                    }
                    dto.setId(UUID.randomUUID());
                    dto.setAirlineId(airlineId);
                    return UldTypeConfigDTO.fromEntity(repository.save(UldTypeConfigDTO.toEntity(dto)));
                })
                .collect(Collectors.toList());
    }
}
