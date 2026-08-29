package com.aircargo.loadplanningservice.service;

import com.aircargo.feign.client.FlightClient;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.feign.client.UldClient;
import com.aircargo.feign.dto.FlightDTO;
import com.aircargo.feign.dto.MawbDTO;
import com.aircargo.feign.dto.UldAwbDTO;
import com.aircargo.feign.dto.UldDTO;
import com.aircargo.loadplanningservice.dto.LoadPlanningDTO;
import com.aircargo.loadplanningservice.dto.LoadPlanningUldDTO;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LoadPlanningServiceImpl implements LoadPlanningService {

    private final FlightClient flightClient;
    private final UldClient uldClient;
    private final MawbClient mawbClient;

    public LoadPlanningServiceImpl(FlightClient flightClient,
                                    UldClient uldClient,
                                    MawbClient mawbClient) {
        this.flightClient = flightClient;
        this.uldClient = uldClient;
        this.mawbClient = mawbClient;
    }

    @Override
    @Cacheable(value = "load-plans", key = "#flightId")
    public Optional<LoadPlanningDTO> getByFlightId(UUID flightId) {
        try {
            FlightDTO flight = flightClient.getFlightById(flightId);
            if (flight == null) return Optional.empty();

            List<UldDTO> ulds = uldClient.getUlds(null, flightId);

            List<LoadPlanningUldDTO> uldDtos = ulds.stream()
                    .map(uld -> {
                        List<UldAwbDTO> awbs = uld.getAwbs();
                        if (awbs == null) {
                            awbs = uldClient.getUldAwbs(uld.getId(), null);
                        }

                        LoadPlanningUldDTO dto = new LoadPlanningUldDTO();
                        dto.setId(uld.getId());
                        dto.setUldNumber(uld.getUldNumber());
                        dto.setUldType(uld.getUldType());
                        dto.setPosition(uld.getPosition());
                        dto.setConfig(uld.getConfig());
                        dto.setSealNumber(uld.getSealNumber());
                        dto.setTareLbs(uld.getTareLbs());
                        dto.setGrossWeightLbs(uld.getGrossWeightLbs());
                        dto.setNetWeightLbs(uld.getNetWeightLbs());
                        dto.setStatus(uld.getStatus());
                        dto.setAwbs(awbs);
                        dto.setDestination(uld.getDestination());
                        dto.setBuiltBy(uld.getBuiltBy());
                        dto.setConfirmedWith(uld.getConfirmedWith());
                        dto.setCompletedAt(uld.getCompletedAt() != null ? uld.getCompletedAt().toString() : null);
                        return dto;
                    })
                    .collect(Collectors.toList());

            String airlineName = null;
            try {
                var airline = flightClient.getAirlineById(flight.getAirlineId());
                if (airline != null) airlineName = airline.getName();
            } catch (Exception ignored) {}

            LoadPlanningDTO result = new LoadPlanningDTO();
            result.setFlightId(flight.getId());
            result.setFlightNumber(flight.getFlightNumber());
            result.setOrigin(flight.getOrigin());
            result.setDestination(flight.getDestination());
            result.setAircraftReg(flight.getAircraftReg());
            result.setFlightDate(flight.getFlightDate());
            result.setTotalPositions(flight.getTotalPositions());
            result.setMaxPayloadKg(flight.getMaxPayloadKg() != null
                    ? java.math.BigDecimal.valueOf(flight.getMaxPayloadKg()) : null);
            result.setUlds(uldDtos);
            result.setAirlineName(airlineName);
            return Optional.of(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    @CacheEvict(value = "load-plans", key = "#flightId")
    public LoadPlanningDTO closeLoadPlan(UUID flightId) {
        FlightDTO flight = flightClient.getFlightById(flightId);
        if (flight == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found: " + flightId);
        }

        flightClient.updateFlightStatus(flightId, "DEPARTED");

        List<UldDTO> ulds = uldClient.getUlds(null, flightId);
        for (UldDTO uld : ulds) {
            if (!"OFFLOADED".equals(uld.getStatus()) && !"LEFT_BEHIND".equals(uld.getStatus())) {
                UldDTO update = new UldDTO();
                update.setStatus("LOADED");
                uldClient.updateUld(uld.getId(), update);
            }

            List<UldAwbDTO> links = uld.getAwbs();
            if (links == null) {
                links = uldClient.getUldAwbs(uld.getId(), null);
            }
            for (UldAwbDTO link : links) {
                if (link.getMawbId() != null) {
                    MawbDTO mawb = mawbClient.getMawbById(link.getMawbId());
                    if (mawb != null && !"DEPARTED".equals(mawb.getStatus())) {
                        mawbClient.updateMawbStatus(link.getMawbId(), "DEPARTED");
                    }
                }
            }
        }

        return getByFlightId(flightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Load plan not found after close"));
    }
}
