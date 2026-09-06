package com.aircargo.bookingservice.service;

import com.aircargo.bookingservice.dto.BookingDTO;
import com.aircargo.bookingservice.entity.Booking;
import com.aircargo.bookingservice.repository.BookingRepository;
import com.aircargo.common.dto.PageResponse;
import com.aircargo.common.entity.CommodityType;
import com.aircargo.feign.client.FlightClient;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.feign.dto.MawbDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    private final BookingRepository bookingRepository;
    private final FlightClient flightClient;
    private final MawbClient mawbClient;

    public BookingServiceImpl(BookingRepository bookingRepository, FlightClient flightClient,
                              MawbClient mawbClient) {
        this.bookingRepository = bookingRepository;
        this.flightClient = flightClient;
        this.mawbClient = mawbClient;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingDTO> getAll(UUID airlineId, UUID flightId) {
        List<Booking> results;
        if (flightId != null) results = bookingRepository.findByFlightId(flightId);
        else if (airlineId != null) results = bookingRepository.findByAirlineId(airlineId);
        else results = bookingRepository.findAll();
        return results.stream().map(BookingDTO::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingDTO> getAll(UUID airlineId, UUID flightId, int page, int size) {
        PageRequest pageReq = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> result;
        if (flightId != null) result = bookingRepository.findByFlightId(flightId, pageReq);
        else if (airlineId != null) result = bookingRepository.findByAirlineId(airlineId, pageReq);
        else result = bookingRepository.findAll(pageReq);

        List<BookingDTO> dtoList = result.getContent().stream()
                .map(BookingDTO::fromEntity)
                .collect(Collectors.toList());

        return PageResponse.of(dtoList, page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "bookings", key = "#id")
    public Optional<BookingDTO> getById(UUID id) {
        return bookingRepository.findById(id).map(BookingDTO::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "bookings", key = "T(java.lang.String).valueOf(#mawbId)")
    public Optional<BookingDTO> findByMawbId(UUID mawbId) {
        return bookingRepository.findByMawbId(mawbId).stream().findFirst().map(BookingDTO::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "bookings", key = "T(java.lang.String).valueOf(#flightId)")
    public List<BookingDTO> getByFlightId(UUID flightId) {
        return bookingRepository.findByFlightId(flightId).stream().map(BookingDTO::fromEntity).toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDTO create(BookingDTO dto) {
        if (dto.getAirlineId() == null && dto.getFlightId() != null) {
            try {
                var flight = flightClient.getFlightById(dto.getFlightId());
                if (flight != null) {
                    dto.setAirlineId(flight.getAirlineId());
                }
            } catch (Exception e) {
                // Flight service unavailable, proceed without airline
            }
        }

        Booking entity = BookingDTO.toEntity(dto);
        Booking saved = bookingRepository.save(entity);
        return BookingDTO.fromEntity(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "bookings", allEntries = true)
    public Optional<BookingDTO> update(UUID id, BookingDTO dto) {
        return bookingRepository.findById(id)
                .map(existing -> {
                    if (dto.getAirlineId() != null) {
                        com.aircargo.common.entity.Airline a = new com.aircargo.common.entity.Airline();
                        a.setId(dto.getAirlineId());
                        existing.setAirline(a);
                    }
                    if (dto.getFlightId() != null) {
                        com.aircargo.bookingservice.entity.Flight f = new com.aircargo.bookingservice.entity.Flight();
                        f.setId(dto.getFlightId());
                        existing.setFlight(f);
                    }
                    if (dto.getMawbId() != null) existing.setMawbId(dto.getMawbId());
                    if (dto.getClientName() != null) existing.setClientName(dto.getClientName());
                    if (dto.getContactName() != null) existing.setContactName(dto.getContactName());
                    if (dto.getCnee() != null) existing.setCnee(dto.getCnee());
                    if (dto.getShipperName() != null) existing.setShipperName(dto.getShipperName());
                    if (dto.getAwbNumber() != null) existing.setAwbNumber(dto.getAwbNumber());
                    if (dto.getSkids() != null) existing.setSkids(dto.getSkids());
                    if (dto.getUnits() != null) existing.setUnits(dto.getUnits());
                    if (dto.getReservedKg() != null) existing.setReservedKg(dto.getReservedKg());
                    if (dto.getConfirmedKg() != null) existing.setConfirmedKg(dto.getConfirmedKg());
                    if (dto.getReceivedKg() != null) existing.setReceivedKg(dto.getReceivedKg());
                    if (dto.getFulfillmentPct() != null) {
                        BigDecimal fp = dto.getFulfillmentPct();
                        if (fp.compareTo(BigDecimal.valueOf(9999.9999)) > 0) {
                            fp = BigDecimal.valueOf(9999.9999);
                        }
                        existing.setFulfillmentPct(fp);
                    }
                    if (dto.getDestination() != null) existing.setDestination(dto.getDestination());
                    if (dto.getPriority() != null) existing.setPriority(dto.getPriority());
                    if (dto.getCommodityType() != null) existing.setCommodityType(dto.getCommodityType());
                    if (dto.getDayReceived() != null) existing.setDayReceived(dto.getDayReceived());
                    if (dto.getTimeHours() != null) existing.setTimeHours(dto.getTimeHours());
                    if (dto.getPositions() != null) existing.setPositions(dto.getPositions());
                    if (dto.getRealPositions() != null) existing.setRealPositions(dto.getRealPositions());
                    if (dto.getLastWeekKg() != null) existing.setLastWeekKg(dto.getLastWeekKg());
                    if (dto.getLastWeekPositions() != null) existing.setLastWeekPositions(dto.getLastWeekPositions());
                    if (dto.getIsConfirmed() != null) existing.setIsConfirmed(dto.getIsConfirmed());
                    if (dto.getNotes() != null) existing.setNotes(dto.getNotes());
                    Booking saved = bookingRepository.save(existing);

                    // Sync MAWB if relevant fields changed
                    if (dto.getAwbNumber() != null || dto.getCommodityType() != null ||
                        dto.getDestination() != null || dto.getSkids() != null) {
                        syncMawbWithBooking(saved, saved.getAwbNumber());
                    }

                    return saved;
                })
                .map(BookingDTO::fromEntity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "bookings", allEntries = true)
    public Optional<BookingDTO> updateAwb(UUID id, String awbNumber) {
        return bookingRepository.findById(id)
                .map(booking -> {
                    booking.setAwbNumber(awbNumber);
                    Booking saved = bookingRepository.save(booking);

                    // Sync with MAWB service
                    syncMawbWithBooking(saved, awbNumber);

                    return BookingDTO.fromEntity(saved);
                });
    }

    private void syncMawbWithBooking(Booking booking, String awbNumber) {
        if (booking.getFlight() == null || booking.getFlight().getId() == null) {
            log.warn("Booking {} has no flightId, cannot sync MAWB", booking.getId());
            return;
        }
        UUID flightId = booking.getFlight().getId();
        UUID airlineId = booking.getAirline() != null ? booking.getAirline().getId() : null;

        // Fetch origin/destination from flight service
        String origin = null;
        String destination = booking.getDestination();
        try {
            var flight = flightClient.getFlightById(flightId);
            if (flight != null) {
                origin = flight.getOrigin();
                if (destination == null) destination = flight.getDestination();
            }
        } catch (Exception e) {
            log.warn("Could not fetch flight {} for MAWB sync", flightId);
        }

        try {
            MawbDTO mawb = null;
            try {
                mawb = mawbClient.getMawbByAwbNumber(awbNumber);
            } catch (Exception e) {
                log.info("MAWB {} not found, will create", awbNumber);
            }

            if (mawb == null) {
                // Create new MAWB
                MawbDTO createDto = new MawbDTO();
                createDto.setAwbNumber(awbNumber);
                createDto.setFlightId(flightId);
                createDto.setAirlineId(airlineId);
                createDto.setOrigin(origin);
                createDto.setDestination(destination);
                createDto.setPieces(booking.getSkids() != null ? booking.getSkids() : 1);
                createDto.setCommodityType(booking.getCommodityType() != null ? booking.getCommodityType().name() : CommodityType.GENERAL.name());
                createDto.setStatus("BOOKED");
                mawb = mawbClient.createMawb(createDto);
                log.info("Created MAWB {} from booking {}", awbNumber, booking.getId());
            } else {
                // Update existing MAWB with booking info
                boolean updated = false;
                String bookingCommodity = booking.getCommodityType() != null ? booking.getCommodityType().name() : null;
                if (bookingCommodity != null && !bookingCommodity.equals(mawb.getCommodityType())) {
                    mawb.setCommodityType(bookingCommodity);
                    updated = true;
                }
                if (booking.getDestination() != null && !booking.getDestination().equals(mawb.getDestination())) {
                    mawb.setDestination(booking.getDestination());
                    updated = true;
                }
                if (booking.getSkids() != null && (mawb.getPieces() == null || !booking.getSkids().equals(mawb.getPieces()))) {
                    mawb.setPieces(booking.getSkids());
                    updated = true;
                }
                if (updated) {
                    mawbClient.updateMawb(mawb.getId(), mawb);
                    log.info("Updated MAWB {} from booking {}", awbNumber, booking.getId());
                }
            }

            // Link booking to MAWB
            if (mawb != null && (booking.getMawbId() == null || !booking.getMawbId().equals(mawb.getId()))) {
                booking.setMawbId(mawb.getId());
                bookingRepository.save(booking);
            }

        } catch (Exception e) {
            log.warn("Failed to sync MAWB for booking {}: {}", booking.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "bookings", allEntries = true)
    public boolean delete(UUID id) {
        if (!bookingRepository.existsById(id)) return false;
        bookingRepository.deleteById(id);
        return true;
    }
}
