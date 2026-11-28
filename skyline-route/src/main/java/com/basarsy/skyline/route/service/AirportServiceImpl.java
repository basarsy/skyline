package com.basarsy.skyline.route.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.route.dto.AirportRequest;
import com.basarsy.skyline.route.dto.AirportResponse;
import com.basarsy.skyline.route.mapper.AirportMapper;
import com.basarsy.skyline.route.repository.AirportRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AirportServiceImpl implements AirportService {

    private static final Pattern IATA_CODE = Pattern.compile("^[A-Z]{3}$");

    private final AirportRepository airportRepository;
    private final AirportMapper airportMapper;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "airports", key = "'all'")
    public List<AirportResponse> findAll() {
        return airportRepository.findAll().stream().map(airportMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "airports", key = "#id")
    public AirportResponse findById(UUID id) {
        return airportRepository
                .findById(id)
                .map(airportMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Airport not found: " + id));
    }

    @Override
    @Transactional
    @CacheEvict(value = "airports", allEntries = true)
    public AirportResponse create(AirportRequest request) {
        String iataCode = normalizeCode(request.iataCode());
        if (!IATA_CODE.matcher(iataCode).matches()) {
            throw new SkylineException("IATA code must be three letters", HttpStatus.BAD_REQUEST);
        }
        if (airportRepository.existsByIataCode(iataCode)) {
            throw new SkylineException("Airport with IATA code already exists", HttpStatus.CONFLICT);
        }
        var airport = airportMapper.toEntity(request);
        airport.setIataCode(iataCode);
        airport.setIcaoCode(normalizeCode(request.icaoCode()));
        return airportMapper.toResponse(airportRepository.save(airport));
    }

    /**
     * Routes carry their airports (time zones included), so both caches go. Search days and every local time
     * worked out from this airport follow from the next request on.
     */
    @Override
    @Transactional
    @Caching(evict = {@CacheEvict(value = "airports", allEntries = true),
            @CacheEvict(value = "routes", allEntries = true)})
    public AirportResponse updateTimezone(UUID id, String timezone) {
        var airport = airportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airport not found: " + id));
        airport.setTimezone(timezone);
        return airportMapper.toResponse(airportRepository.save(airport));
    }

    /**
     * Airport codes as stored: trimmed and upper-cased. {@link Locale#ROOT}, so a Turkish-locale host doesn't
     * turn "ist" into "İST".
     */
    static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
