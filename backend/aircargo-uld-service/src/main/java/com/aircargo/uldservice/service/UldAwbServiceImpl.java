package com.aircargo.uldservice.service;

import com.aircargo.feign.client.MawbClient;
import com.aircargo.uldservice.dto.UldAwbDTO;
import com.aircargo.uldservice.entity.UldAwb;
import com.aircargo.uldservice.repository.UldAwbRepository;
import com.aircargo.uldservice.repository.UldRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UldAwbServiceImpl implements UldAwbService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UldAwbServiceImpl.class);

    private final UldAwbRepository uldAwbRepository;
    private final UldRepository uldRepository;
    private final MawbClient mawbClient;

    public UldAwbServiceImpl(UldAwbRepository uldAwbRepository,
                              UldRepository uldRepository,
                              MawbClient mawbClient) {
        this.uldAwbRepository = uldAwbRepository;
        this.uldRepository = uldRepository;
        this.mawbClient = mawbClient;
    }

    @Override
    @Cacheable(value = "uld-awbs", key = "{#uldId, #mawbId}")
    public List<UldAwbDTO> getAll(UUID uldId, UUID mawbId) {
        List<UldAwb> results;
        if (uldId != null) {
            results = uldAwbRepository.findByUldId(uldId);
        } else if (mawbId != null) {
            results = uldAwbRepository.findByMawbId(mawbId);
        } else {
            results = uldAwbRepository.findAll();
        }
        return results.stream()
                .map(UldAwbDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "uld-awbs", key = "#id")
    public Optional<UldAwbDTO> getById(UUID id) {
        return uldAwbRepository.findById(id)
                .map(UldAwbDTO::fromEntity);
    }

    @Override
    @CacheEvict(value = {"uld-awbs", "ulds"}, allEntries = true)
    public UldAwbDTO create(UldAwbDTO dto) {
        if (!uldRepository.existsById(dto.getUldId())) {
            throw new IllegalArgumentException("ULD not found: " + dto.getUldId());
        }
        UldAwb entity = UldAwbDTO.toEntity(dto);
        entity.setId(null);
        UldAwb saved = uldAwbRepository.save(entity);

        // Auto-set MAWB status to MANIFESTED when ULD is assigned to a flight
        if (dto.getMawbId() != null) {
            try {
                uldRepository.findById(dto.getUldId()).ifPresent(uld -> {
                    if (uld.getFlightId() != null) {
                        try {
                            var mawb = mawbClient.getMawbById(dto.getMawbId());
                            if (mawb != null && ("BOOKED".equals(mawb.getStatus()) || "RECEIVED".equals(mawb.getStatus()))) {
                                mawbClient.updateMawbStatus(dto.getMawbId(), "MANIFESTED");
                                log.info("Auto-set MAWB {} to MANIFESTED (ULD-AWB created for flight-assigned ULD)", dto.getMawbId());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to set MAWB {} to MANIFESTED: {}", dto.getMawbId(), e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to resolve ULD for MAWB status update: {}", e.getMessage());
            }
        }

        return UldAwbDTO.fromEntity(saved);
    }

    @Override
    @CacheEvict(value = {"uld-awbs", "ulds"}, allEntries = true)
    public Optional<UldAwbDTO> update(UUID id, UldAwbDTO dto) {
        return uldAwbRepository.findById(id)
                .map(existing -> {
                    UldAwb updated = UldAwbDTO.toEntity(dto);
                    updated.setId(existing.getId());
                    return uldAwbRepository.save(updated);
                })
                .map(UldAwbDTO::fromEntity);
    }

    @Override
    @CacheEvict(value = {"uld-awbs", "ulds"}, allEntries = true)
    public boolean delete(UUID id) {
        if (!uldAwbRepository.existsById(id)) return false;
        uldAwbRepository.deleteById(id);
        return true;
    }
}
