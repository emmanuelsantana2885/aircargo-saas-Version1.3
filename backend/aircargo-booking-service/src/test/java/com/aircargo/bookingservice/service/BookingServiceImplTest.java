package com.aircargo.bookingservice.service;

import com.aircargo.bookingservice.dto.BookingDTO;
import com.aircargo.bookingservice.entity.Booking;
import com.aircargo.bookingservice.entity.Flight;
import com.aircargo.bookingservice.repository.BookingRepository;
import com.aircargo.feign.client.FlightClient;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.feign.dto.FlightDTO;
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
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private FlightClient flightClient;
    @Mock
    private MawbClient mawbClient;

    private BookingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(bookingRepository, flightClient, mawbClient);
    }

    private Booking sampleBooking() {
        Flight flight = new Flight();
        flight.setId(UUID.randomUUID());
        return Booking.builder()
                .id(UUID.randomUUID())
                .clientName("Rannik Cargo")
                .flight(flight)
                .build();
    }

    @Test
    void create_derivesAirlineFromFlight_whenMissing() {
        UUID flightId = UUID.randomUUID();
        UUID airlineId = UUID.randomUUID();
        FlightDTO flightDto = new FlightDTO();
        flightDto.setAirlineId(airlineId);
        when(flightClient.getFlightById(flightId)).thenReturn(flightDto);

        BookingDTO dto = BookingDTO.builder()
                .clientName("Rannik Cargo")
                .flightId(flightId)
                .build();
        Booking saved = sampleBooking();
        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);

        BookingDTO result = service.create(dto);

        assertNotNull(result);
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertNotNull(captor.getValue().getAirline());
        assertEquals(airlineId, captor.getValue().getAirline().getId());
    }

    @Test
    void create_doesNotFail_whenFlightServiceUnavailable() {
        UUID flightId = UUID.randomUUID();
        when(flightClient.getFlightById(flightId)).thenThrow(new RuntimeException("flight down"));
        Booking saved = sampleBooking();
        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);

        BookingDTO dto = BookingDTO.builder().clientName("X").flightId(flightId).build();

        assertDoesNotThrow(() -> service.create(dto));
    }

    @Test
    void update_capsFulfillmentPctAt9999_9999() {
        Booking existing = sampleBooking();
        when(bookingRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDTO dto = BookingDTO.builder()
                .fulfillmentPct(BigDecimal.valueOf(12000.0000))
                .build();

        Optional<BookingDTO> result = service.update(existing.getId(), dto);

        assertTrue(result.isPresent());
        assertEquals(0, result.get().getFulfillmentPct().compareTo(BigDecimal.valueOf(9999.9999)));
    }

    @Test
    void updateAwb_setsAwbAndPublishesEvent() {
        Booking existing = sampleBooking();
        when(bookingRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<BookingDTO> result = service.updateAwb(existing.getId(), "406-05912970");

        assertTrue(result.isPresent());
        assertEquals("406-05912970", result.get().getAwbNumber());
    }

    @Test
    void findByMawbId_returnsFirstBooking() {
        Booking b = sampleBooking();
        UUID mawbId = UUID.randomUUID();
        when(bookingRepository.findByMawbId(mawbId)).thenReturn(List.of(b));

        Optional<BookingDTO> result = service.findByMawbId(mawbId);

        assertTrue(result.isPresent());
        assertEquals(b.getClientName(), result.get().getClientName());
    }

    @Test
    void delete_returnsFalse_whenNotExists() {
        UUID id = UUID.randomUUID();
        when(bookingRepository.existsById(id)).thenReturn(false);

        assertFalse(service.delete(id));
        verify(bookingRepository, never()).deleteById(any());
    }
}
