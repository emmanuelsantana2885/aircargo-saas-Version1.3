package com.aircargo.mawbservice.service;

import com.aircargo.mawbservice.dto.MawbDTO;
import com.aircargo.mawbservice.entity.Mawb;
import com.aircargo.mawbservice.entity.MawbStatus;
import com.aircargo.mawbservice.repository.MawbRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MawbServiceImplTest {

    @Mock
    private MawbRepository mawbRepository;

    private ObjectMapper objectMapper = new ObjectMapper();
    private MawbServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MawbServiceImpl(mawbRepository, objectMapper);
    }

    private Mawb sampleMawb() {
        Mawb m = new Mawb();
        m.setId(UUID.randomUUID());
        m.setAwbNumber("406-05912970");
        m.setShipperName("Rannik");
        m.setConsigneeName("Consignee SA");
        m.setOrigin("SDQ");
        m.setDestination("MIA");
        m.setStatus(MawbStatus.BOOKED);
        return m;
    }

    @Test
    void create_defaultsStatusToBooked_whenNull() {
        MawbDTO dto = new MawbDTO();
        dto.setAwbNumber("406-00000001");

        Mawb saved = sampleMawb();
        when(mawbRepository.save(any(Mawb.class))).thenReturn(saved);

        MawbDTO result = service.create(dto);

        assertNotNull(result);
        ArgumentCaptor<Mawb> captor = ArgumentCaptor.forClass(Mawb.class);
        verify(mawbRepository).save(captor.capture());
        assertEquals(MawbStatus.BOOKED, captor.getValue().getStatus());
    }

    @Test
    void create_respectsDtoStatus_whenProvided() {
        MawbDTO dto = new MawbDTO();
        dto.setAwbNumber("406-00000002");
        dto.setStatus(MawbStatus.MANIFESTED);

        Mawb saved = sampleMawb();
        when(mawbRepository.save(any(Mawb.class))).thenReturn(saved);

        service.create(dto);

        ArgumentCaptor<Mawb> captor = ArgumentCaptor.forClass(Mawb.class);
        verify(mawbRepository).save(captor.capture());
        assertEquals(MawbStatus.MANIFESTED, captor.getValue().getStatus());
    }

    @Test
    void getByAwbNumber_returnsMawb() {
        Mawb m = sampleMawb();
        when(mawbRepository.findByAwbNumber("406-05912970")).thenReturn(Optional.of(m));

        Optional<MawbDTO> result = service.getByAwbNumber("406-05912970");

        assertTrue(result.isPresent());
        assertEquals("406-05912970", result.get().getAwbNumber());
    }

    @Test
    void updateStatus_publishesEvent_whenStatusChanges() {
        Mawb m = sampleMawb();
        m.setStatus(MawbStatus.BOOKED);
        when(mawbRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(mawbRepository.save(any(Mawb.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<MawbDTO> result = service.updateStatus(m.getId(), MawbStatus.DEPARTED);

        assertTrue(result.isPresent());
        assertEquals(MawbStatus.DEPARTED, result.get().getStatus());
    }

    @Test
    void updateStatus_doesNotPublish_whenStatusUnchanged() {
        Mawb m = sampleMawb();
        m.setStatus(MawbStatus.BOOKED);
        when(mawbRepository.findById(m.getId())).thenReturn(Optional.of(m));
        when(mawbRepository.save(any(Mawb.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(m.getId(), MawbStatus.BOOKED);

        assertEquals(MawbStatus.BOOKED, m.getStatus());
    }

    @Test
    void delete_returnsFalse_whenNotExists() {
        UUID id = UUID.randomUUID();
        when(mawbRepository.existsById(id)).thenReturn(false);

        assertFalse(service.delete(id));
        verify(mawbRepository, never()).deleteById(any());
    }

    @Test
    void update_ignoresNullFields() {
        Mawb existing = sampleMawb();
        when(mawbRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(mawbRepository.save(any(Mawb.class))).thenAnswer(inv -> inv.getArgument(0));

        MawbDTO dto = new MawbDTO();
        dto.setDestination("PUJ");
        Optional<MawbDTO> result = service.update(existing.getId(), dto);

        assertTrue(result.isPresent());
        assertEquals("PUJ", result.get().getDestination());
        assertEquals("SDQ", result.get().getOrigin());
    }

    @Test
    void getAll_returnsFilteredByAirlineOnly() {
        UUID airlineId = UUID.randomUUID();
        when(mawbRepository.findByAirlineId(airlineId)).thenReturn(List.of(sampleMawb()));

        List<MawbDTO> result = service.getAll(airlineId, null, null);

        assertEquals(1, result.size());
        verify(mawbRepository).findByAirlineId(airlineId);
    }
}
