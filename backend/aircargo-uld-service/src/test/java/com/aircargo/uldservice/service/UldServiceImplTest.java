package com.aircargo.uldservice.service;

import com.aircargo.uldservice.dto.UldAwbDTO;
import com.aircargo.uldservice.dto.UldDTO;
import com.aircargo.uldservice.entity.Uld;
import com.aircargo.uldservice.entity.UldAwb;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.uldservice.entity.UldStatus;
import com.aircargo.uldservice.repository.UldAwbRepository;
import com.aircargo.uldservice.repository.UldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UldServiceImplTest {

    @Mock
    private UldRepository uldRepository;
    @Mock
    private UldAwbRepository uldAwbRepository;
    @Mock
    private MawbClient mawbClient;

    private UldServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UldServiceImpl(uldRepository, uldAwbRepository, mawbClient);
    }

    private UldDTO sampleDto() {
        UldDTO dto = new UldDTO();
        dto.setUldNumber("PMC12345");
        dto.setTareLbs(new BigDecimal("140"));
        dto.setGrossWeightLbs(new BigDecimal("1000"));
        return dto;
    }

    @Test
    void create_defaultsStatusToOpen_whenStatusMissing() {
        UldDTO dto = sampleDto();
        dto.setStatus(null);
        when(uldRepository.save(any(Uld.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(dto);

        ArgumentCaptor<Uld> captor = ArgumentCaptor.forClass(Uld.class);
        verify(uldRepository).save(captor.capture());
        assertEquals(UldStatus.OPEN, captor.getValue().getStatus());
    }

    @Test
    void create_computesMetricWeights() {
        Uld saved = UldDTO.toEntity(sampleDto());
        when(uldRepository.save(any(Uld.class))).thenAnswer(inv -> inv.getArgument(0));

        UldDTO result = service.create(sampleDto());

        ArgumentCaptor<Uld> captor = ArgumentCaptor.forClass(Uld.class);
        verify(uldRepository).save(captor.capture());
        Uld e = captor.getValue();

        assertEquals(0, e.getTareKg().compareTo(new BigDecimal("63.50")));
        assertEquals(0, e.getGrossWeightKg().compareTo(new BigDecimal("453.59")));
        assertEquals(0, e.getNetWeightLbs().compareTo(new BigDecimal("860")));
        assertEquals(0, e.getNetWeightKg().compareTo(new BigDecimal("390.09")));
        assertNotNull(result);
    }

    @Test
    void create_doesNotComputeNet_whenOnlyTareProvided() {
        UldDTO dto = sampleDto();
        dto.setGrossWeightLbs(null);
        Uld saved = UldDTO.toEntity(dto);
        when(uldRepository.save(any(Uld.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(dto);

        ArgumentCaptor<Uld> captor = ArgumentCaptor.forClass(Uld.class);
        verify(uldRepository).save(captor.capture());
        Uld e = captor.getValue();
        assertEquals(0, e.getTareKg().compareTo(new BigDecimal("63.50")));
        assertNull(e.getNetWeightLbs());
        assertNull(e.getNetWeightKg());
    }

    @Test
    void getAll_filtersByFlightAndEnrichesAwbs() {
        UUID flightId = UUID.randomUUID();
        UUID uldId = UUID.randomUUID();
        Uld uld = UldDTO.toEntity(sampleDto());
        uld.setId(uldId);
        when(uldRepository.findByFlightId(flightId)).thenReturn(List.of(uld));

        UldAwb awb = new UldAwb();
        awb.setId(UUID.randomUUID());
        awb.setUldId(uldId);
        awb.setMawbLabel("406-05912970");
        when(uldAwbRepository.findByUldIdIn(List.of(uldId))).thenReturn(List.of(awb));

        List<UldDTO> result = service.getAll(null, flightId);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getAwbs());
        assertEquals(1, result.get(0).getAwbs().size());
        assertEquals("406-05912970", result.get(0).getAwbs().get(0).getMawbLabel());
    }

    @Test
    void getAll_prefersFlightFilterOverAirline() {
        UUID flightId = UUID.randomUUID();
        UUID airlineId = UUID.randomUUID();
        when(uldRepository.findByFlightId(flightId)).thenReturn(List.of(UldDTO.toEntity(sampleDto())));
        when(uldAwbRepository.findByUldIdIn(any())).thenReturn(List.of());

        service.getAll(airlineId, flightId);

        verify(uldRepository).findByFlightId(flightId);
        verify(uldRepository, never()).findByAirlineId(any());
    }

    @Test
    void update_preservesNullAndRecomputesMetricWeights() {
        Uld existing = UldDTO.toEntity(sampleDto());
        existing.setId(UUID.randomUUID());
        when(uldRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(uldRepository.save(any(Uld.class))).thenAnswer(inv -> inv.getArgument(0));

        UldDTO dto = new UldDTO();
        dto.setUldNumber("PMC99999");

        Optional<UldDTO> result = service.update(existing.getId(), dto);

        assertTrue(result.isPresent());
        assertEquals("PMC99999", result.get().getUldNumber());
        assertEquals(0, result.get().getTareLbs().compareTo(new BigDecimal("140")));
        assertNull(result.get().getTareKg());
    }

    @Test
    void transferUld_setsFlightAndAppendsNote() {
        UUID uldId = UUID.randomUUID();
        UUID destFlight = UUID.randomUUID();
        Uld uld = UldDTO.toEntity(sampleDto());
        uld.setId(uldId);
        when(uldRepository.findById(uldId)).thenReturn(Optional.of(uld));
        when(uldRepository.save(any(Uld.class))).thenAnswer(inv -> inv.getArgument(0));
        when(uldAwbRepository.findByUldId(any())).thenReturn(List.of());

        UldDTO result = service.transferUld(uldId, destFlight, "Overbooked");

        assertEquals(destFlight, result.getFlightId());
        assertNotNull(result.getNotes());
        assertTrue(result.getNotes().contains("Transferido a " + destFlight));
        assertTrue(result.getNotes().contains("Overbooked"));
    }

    @Test
    void assignFlight_setsFlight() {
        UUID uldId = UUID.randomUUID();
        UUID flightId = UUID.randomUUID();
        Uld uld = UldDTO.toEntity(sampleDto());
        uld.setId(uldId);
        when(uldRepository.findById(uldId)).thenReturn(Optional.of(uld));
        when(uldRepository.save(any(Uld.class))).thenAnswer(inv -> inv.getArgument(0));
        when(uldAwbRepository.findByUldId(any())).thenReturn(List.of());

        UldDTO result = service.assignFlight(uldId, flightId);

        assertEquals(flightId, result.getFlightId());
    }

    @Test
    void delete_returnsFalse_whenNotExists() {
        UUID id = UUID.randomUUID();
        when(uldRepository.existsById(id)).thenReturn(false);

        assertFalse(service.delete(id));
        verify(uldRepository, never()).deleteById(any());
    }
}
