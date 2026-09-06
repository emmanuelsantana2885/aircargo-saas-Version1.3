package com.aircargo.uldservice.service;

import com.aircargo.uldservice.dto.UldAwbDTO;
import com.aircargo.uldservice.dto.UldDTO;
import com.aircargo.uldservice.entity.Uld;
import com.aircargo.uldservice.entity.UldAwb;
import com.aircargo.uldservice.entity.UldFlightOperator;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.uldservice.entity.UldStatus;
import com.aircargo.uldservice.repository.UldAwbRepository;
import com.aircargo.uldservice.repository.UldFlightOperatorRepository;
import com.aircargo.uldservice.repository.UldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UldServiceImplTest {

    @Mock
    private UldRepository uldRepository;
    @Mock
    private UldAwbRepository uldAwbRepository;
    @Mock
    private UldFlightOperatorRepository uldFlightOperatorRepository;
    @Mock
    private MawbClient mawbClient;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private UldServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UldServiceImpl(uldRepository, uldAwbRepository, uldFlightOperatorRepository, mawbClient, rabbitTemplate);
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
    void create_withFlightAndMissingOperators_throws() {
        UldDTO dto = sampleDto();
        dto.setFlightId(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> service.create(dto));
    }

    @Test
    void create_withFlightAndOperators_persistsFlightSnapshot() {
        UUID uldId = UUID.randomUUID();
        UUID flightId = UUID.randomUUID();
        UldDTO dto = sampleDto();
        dto.setId(uldId);
        dto.setFlightId(flightId);
        dto.setLoadedBy("Alan");
        dto.setWeighedBy("Beatriz");
        dto.setConfirmedWith("ACOMS");
        when(uldRepository.save(any(Uld.class))).thenAnswer(inv -> inv.getArgument(0));
        when(uldFlightOperatorRepository.findByUldIdAndFlightId(uldId, flightId)).thenReturn(Optional.empty());
        when(uldFlightOperatorRepository.save(any(UldFlightOperator.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(dto);

        ArgumentCaptor<UldFlightOperator> cap = ArgumentCaptor.forClass(UldFlightOperator.class);
        verify(uldFlightOperatorRepository).save(cap.capture());
        assertEquals(uldId, cap.getValue().getUldId());
        assertEquals(flightId, cap.getValue().getFlightId());
        assertEquals("Alan", cap.getValue().getLoadedBy());
        assertEquals("Beatriz", cap.getValue().getWeighedBy());
        assertEquals("ACOMS", cap.getValue().getConfirmedWith());
    }

    @Test
    void update_enforceMandatory_rejectsIncompleteOperators() {
        UUID uldId = UUID.randomUUID();
        Uld existing = UldDTO.toEntity(sampleDto());
        existing.setId(uldId);
        existing.setFlightId(UUID.randomUUID());
        when(uldRepository.findById(uldId)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service.update(uldId, new UldDTO()));
    }

    @Test
    void update_partialPatch_skipsMandatoryCheck() {
        UUID uldId = UUID.randomUUID();
        Uld existing = UldDTO.toEntity(sampleDto());
        existing.setId(uldId);
        existing.setFlightId(UUID.randomUUID());
        when(uldRepository.findById(uldId)).thenReturn(Optional.of(existing));
        when(uldRepository.save(any(Uld.class))).thenAnswer(inv -> inv.getArgument(0));
        when(uldFlightOperatorRepository.findByUldIdAndFlightId(any(), any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.update(uldId, new UldDTO(), false));
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
        when(uldFlightOperatorRepository.findByUldIdInAndFlightId(anyList(), any(UUID.class))).thenReturn(List.of());

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
        when(uldFlightOperatorRepository.findByUldIdInAndFlightId(anyList(), any(UUID.class))).thenReturn(List.of());

        service.getAll(airlineId, flightId);

        verify(uldRepository).findByFlightId(flightId);
        verify(uldRepository, never()).findByAirlineId(any());
    }

    @Test
    void getAll_resolvesOperatorsFromFlightSnapshot() {
        UUID flightId = UUID.randomUUID();
        UUID uldId = UUID.randomUUID();
        Uld uld = UldDTO.toEntity(sampleDto());
        uld.setId(uldId);
        uld.setFlightId(flightId);
        when(uldRepository.findByFlightId(flightId)).thenReturn(List.of(uld));
        when(uldAwbRepository.findByUldIdIn(anyList())).thenReturn(List.of());

        com.aircargo.uldservice.entity.UldFlightOperator op =
                new com.aircargo.uldservice.entity.UldFlightOperator();
        op.setUldId(uldId);
        op.setFlightId(flightId);
        op.setLoadedBy("Carmen");
        op.setWeighedBy("Pedro");
        op.setConfirmedWith("ACOMS");
        when(uldFlightOperatorRepository.findByUldIdInAndFlightId(anyList(), any(UUID.class))).thenReturn(List.of(op));

        List<UldDTO> result = service.getAll(null, flightId);

        assertEquals("Carmen", result.get(0).getLoadedBy());
        assertEquals("Pedro", result.get(0).getWeighedBy());
        assertEquals("ACOMS", result.get(0).getConfirmedWith());
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
        when(uldFlightOperatorRepository.findByUldIdAndFlightId(any(), any())).thenReturn(Optional.empty());

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
        when(uldFlightOperatorRepository.findByUldIdAndFlightId(any(), any())).thenReturn(Optional.empty());

        UldDTO result = service.assignFlight(uldId, flightId);

        assertEquals(flightId, result.getFlightId());
    }

    @Test
    void assignFlight_copiesOperatorsToDestinationFlight() {
        UUID uldId = UUID.randomUUID();
        UUID flightId = UUID.randomUUID();
        Uld uld = UldDTO.toEntity(sampleDto());
        uld.setId(uldId);
        uld.setLoadedBy("John");
        uld.setWeighedBy("Maria");
        uld.setConfirmedWith("Booked");
        when(uldRepository.findById(uldId)).thenReturn(Optional.of(uld));
        when(uldRepository.save(any(Uld.class))).thenAnswer(inv -> inv.getArgument(0));
        when(uldAwbRepository.findByUldId(any())).thenReturn(List.of());
        when(uldFlightOperatorRepository.findByUldIdAndFlightId(any(), any())).thenReturn(Optional.empty());
        when(uldFlightOperatorRepository.save(any(UldFlightOperator.class))).thenAnswer(inv -> inv.getArgument(0));

        UldDTO result = service.assignFlight(uldId, flightId);

        assertEquals(flightId, result.getFlightId());
        ArgumentCaptor<UldFlightOperator> cap = ArgumentCaptor.forClass(UldFlightOperator.class);
        verify(uldFlightOperatorRepository).save(cap.capture());
        assertEquals(flightId, cap.getValue().getFlightId());
        assertEquals("John", cap.getValue().getLoadedBy());
        assertEquals("Maria", cap.getValue().getWeighedBy());
        assertEquals("Booked", cap.getValue().getConfirmedWith());
    }

    @Test
    void delete_returnsFalse_whenNotExists() {
        UUID id = UUID.randomUUID();
        when(uldRepository.findById(id)).thenReturn(Optional.empty());

        assertFalse(service.delete(id));
        verify(uldRepository, never()).deleteById(any());
    }
}
