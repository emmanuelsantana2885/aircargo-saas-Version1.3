package com.aircargo.uldservice.service;

import com.aircargo.common.dto.PageResponse;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.uldservice.dto.UldAwbDTO;
import com.aircargo.uldservice.dto.UldDTO;
import com.aircargo.uldservice.entity.Uld;
import com.aircargo.uldservice.entity.UldAwb;
import com.aircargo.uldservice.entity.UldStatus;
import com.aircargo.uldservice.repository.UldAwbRepository;
import com.aircargo.uldservice.repository.UldRepository;
import com.aircargo.uldservice.util.UldTypes;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UldServiceImpl implements UldService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UldServiceImpl.class);
    private static final BigDecimal KG_PER_LB = new BigDecimal("0.453592");

    private final UldRepository uldRepository;
    private final UldAwbRepository uldAwbRepository;
    private final MawbClient mawbClient;

    public UldServiceImpl(UldRepository uldRepository, UldAwbRepository uldAwbRepository, MawbClient mawbClient) {
        this.uldRepository = uldRepository;
        this.uldAwbRepository = uldAwbRepository;
        this.mawbClient = mawbClient;
    }

    private void computeMetricWeights(Uld e) {
        if (e.getTareLbs() != null) {
            e.setTareKg(e.getTareLbs().multiply(KG_PER_LB).setScale(2, RoundingMode.HALF_UP));
        }
        if (e.getGrossWeightLbs() != null) {
            e.setGrossWeightKg(e.getGrossWeightLbs().multiply(KG_PER_LB).setScale(2, RoundingMode.HALF_UP));
        }
        if (e.getGrossWeightLbs() != null && e.getTareLbs() != null) {
            BigDecimal netLbs = e.getGrossWeightLbs().subtract(e.getTareLbs());
            e.setNetWeightLbs(netLbs);
            e.setNetWeightKg(netLbs.multiply(KG_PER_LB).setScale(2, RoundingMode.HALF_UP));
        }
    }

    private UldDTO enrichWithAwbs(UldDTO dto) {
        if (dto == null || dto.getId() == null) return dto;
        List<UldAwbDTO> awbs = uldAwbRepository.findByUldId(dto.getId())
                .stream().map(UldAwbDTO::fromEntity).collect(Collectors.toList());
        dto.setAwbs(awbs);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "ulds", key = "{#airlineId, #flightId}")
    public List<UldDTO> getAll(UUID airlineId, UUID flightId) {
        List<Uld> results;
        if (flightId != null) results = uldRepository.findByFlightId(flightId);
        else if (airlineId != null) results = uldRepository.findByAirlineId(airlineId);
        else results = uldRepository.findAll();
        List<UldDTO> dtos = results.stream()
                .map(UldDTO::fromEntity)
                .collect(Collectors.toList());
        List<UUID> uldIds = dtos.stream().map(UldDTO::getId).collect(Collectors.toList());
        if (!uldIds.isEmpty()) {
            List<UldAwb> allAwbs = uldAwbRepository.findByUldIdIn(uldIds);
            Map<UUID, List<UldAwbDTO>> awbMap = allAwbs.stream()
                    .collect(Collectors.groupingBy(
                            UldAwb::getUldId,
                            Collectors.mapping(UldAwbDTO::fromEntity, Collectors.toList())
                    ));
            dtos.forEach(dto -> dto.setAwbs(awbMap.getOrDefault(dto.getId(), List.of())));
        }
        return dtos;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UldDTO> getAll(UUID airlineId, UUID flightId, int page, int size) {
        PageRequest pageReq = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Uld> result;
        if (flightId != null) result = uldRepository.findByFlightId(flightId, pageReq);
        else if (airlineId != null) result = uldRepository.findByAirlineId(airlineId, pageReq);
        else result = uldRepository.findAll(pageReq);

        List<UldDTO> dtos = result.getContent().stream()
                .map(UldDTO::fromEntity)
                .collect(Collectors.toList());

        List<UUID> uldIds = dtos.stream().map(UldDTO::getId).collect(Collectors.toList());
        if (!uldIds.isEmpty()) {
            List<UldAwb> allAwbs = uldAwbRepository.findByUldIdIn(uldIds);
            Map<UUID, List<UldAwbDTO>> awbMap = allAwbs.stream()
                    .collect(Collectors.groupingBy(
                            UldAwb::getUldId,
                            Collectors.mapping(UldAwbDTO::fromEntity, Collectors.toList())
                    ));
            dtos.forEach(dto -> dto.setAwbs(awbMap.getOrDefault(dto.getId(), List.of())));
        }

        return PageResponse.of(dtos, page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "ulds", key = "#id")
    public Optional<UldDTO> getById(UUID id) {
        return uldRepository.findById(id)
                .map(UldDTO::fromEntity)
                .map(this::enrichWithAwbs);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"ulds", "uld-awbs"}, allEntries = true)
    public UldDTO create(UldDTO dto) {
        if (dto.getUldType() != null) validateUldType(dto.getUldType());
        Uld e = UldDTO.toEntity(dto);
        if (e.getStatus() == null) e.setStatus(UldStatus.OPEN);
        computeMetricWeights(e);
        Uld saved = uldRepository.save(e);
        return enrichWithAwbs(UldDTO.fromEntity(saved));
    }

    private void validateUldType(String uldType) {
        if (!UldTypes.isValid(uldType)) {
            throw new IllegalArgumentException("uldType inválido: use 3-5 caracteres alfanuméricos (ej. PMC, AKE). Regístrelo primero en el catálogo de tipos ULD");
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"ulds", "uld-awbs"}, allEntries = true)
    public Optional<UldDTO> update(UUID id, UldDTO dto) {
        return uldRepository.findById(id)
                .map(existing -> {
                    if (dto.getAirlineId() != null) existing.setAirlineId(dto.getAirlineId());
                    if (dto.getFlightId() != null) existing.setFlightId(dto.getFlightId());
                    if (dto.getUldNumber() != null) existing.setUldNumber(dto.getUldNumber());
                    if (dto.getUldType() != null) {
                        validateUldType(dto.getUldType());
                        existing.setUldType(dto.getUldType());
                    }
                    if (dto.getPosition() != null) existing.setPosition(dto.getPosition());
                    if (dto.getConfig() != null) existing.setConfig(dto.getConfig());
                    if (dto.getSealNumber() != null) existing.setSealNumber(dto.getSealNumber());
                    if (dto.getTareLbs() != null) {
                        existing.setTareLbs(dto.getTareLbs());
                        existing.setTareKg(dto.getTareLbs().multiply(KG_PER_LB));
                    }
                    if (dto.getGrossWeightLbs() != null) {
                        existing.setGrossWeightLbs(dto.getGrossWeightLbs());
                        existing.setGrossWeightKg(dto.getGrossWeightLbs().multiply(KG_PER_LB));
                    }
                    if (dto.getNetWeightLbs() != null) {
                        existing.setNetWeightLbs(dto.getNetWeightLbs());
                        existing.setNetWeightKg(dto.getNetWeightLbs().multiply(KG_PER_LB));
                    }
                    if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
                    if (dto.getBuiltAt() != null) existing.setBuiltAt(dto.getBuiltAt());
                    if (dto.getLoadedAt() != null) existing.setLoadedAt(dto.getLoadedAt());
                    if (dto.getNotes() != null) existing.setNotes(dto.getNotes());
                    return uldRepository.save(existing);
                })
                .map(UldDTO::fromEntity)
                .map(this::enrichWithAwbs);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"ulds", "uld-awbs"}, allEntries = true)
    public UldDTO transferUld(UUID uldId, UUID destinationFlightId, String reason) {
        Uld uld = uldRepository.findById(uldId)
                .orElseThrow(() -> new IllegalArgumentException("ULD not found: " + uldId));
        uld.setFlightId(destinationFlightId);
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String note = "[" + timestamp + "] Transferido a " + destinationFlightId + ": " + (reason != null ? reason : "");
        if (uld.getNotes() != null) {
            note = note + "\n" + uld.getNotes();
        }
        uld.setNotes(note);
        Uld saved = uldRepository.save(uld);
        return enrichWithAwbs(UldDTO.fromEntity(saved));
    }

    @Override
    @Transactional
    @CacheEvict(value = {"ulds", "uld-awbs"}, allEntries = true)
    public UldDTO assignFlight(UUID id, UUID flightId) {
        Uld uld = uldRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ULD not found: " + id));
        uld.setFlightId(flightId);
        Uld saved = uldRepository.save(uld);

        // Auto-set MAWB status to MANIFESTED when ULD is assigned to a flight
        if (flightId != null) {
            setMawbStatusToManifested(id);
        }

        return enrichWithAwbs(UldDTO.fromEntity(saved));
    }

    private void setMawbStatusToManifested(UUID uldId) {
        try {
            List<UldAwb> links = uldAwbRepository.findByUldId(uldId);
            for (UldAwb link : links) {
                if (link.getMawbId() != null) {
                    try {
                        var mawb = mawbClient.getMawbById(link.getMawbId());
                        if (mawb != null && ("BOOKED".equals(mawb.getStatus()) || "RECEIVED".equals(mawb.getStatus()))) {
                            mawbClient.updateMawbStatus(link.getMawbId(), "MANIFESTED");
                            log.info("Auto-set MAWB {} status to MANIFESTED (ULD assigned to flight)", link.getMawbId());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to set MAWB {} to MANIFESTED: {}", link.getMawbId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve ULD-AWB links for ULD {}: {}", uldId, e.getMessage());
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"ulds", "uld-awbs"}, allEntries = true)
    public boolean delete(UUID id) {
        if (!uldRepository.existsById(id)) return false;
        uldRepository.deleteById(id);
        return true;
    }
}
