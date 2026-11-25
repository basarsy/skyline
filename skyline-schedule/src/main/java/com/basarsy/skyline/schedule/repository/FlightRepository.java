package com.basarsy.skyline.schedule.repository;

import com.basarsy.skyline.schedule.entity.Flight;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightRepository extends JpaRepository<Flight, UUID>, JpaSpecificationExecutor<Flight> {

    @Query("""
            SELECT f FROM Flight f
            WHERE f.routeId IN :routeIds
              AND f.departureTime >= :departureStart
              AND f.departureTime < :departureEnd
              AND f.status IN :statuses
            """)
    Page<Flight> searchFlights(
            @Param("routeIds") java.util.List<UUID> routeIds,
            @Param("departureStart") LocalDateTime departureStart,
            @Param("departureEnd") LocalDateTime departureEnd,
            @Param("statuses") Collection<FlightStatus> statuses,
            Pageable pageable);

    /**
     * Holds one seat on a flight in one of {@code statuses} that is scheduled to depart no earlier than
     * {@code earliestDeparture} (sales are still open). Bulk updates bypass Hibernate's optimistic locking, so the
     * version is bumped here: a concurrent load-modify-save of the same flight (update, cancel) then fails with
     * an optimistic lock conflict instead of overwriting the seat count.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Flight f
            SET f.availableSeats = f.availableSeats - 1, f.version = f.version + 1, f.updatedAt = :now
            WHERE f.id = :id AND f.availableSeats > 0 AND f.status IN :statuses
              AND f.departureTime >= :earliestDeparture
            """)
    int decrementSeat(@Param("id") UUID id, @Param("statuses") Collection<FlightStatus> statuses,
            @Param("earliestDeparture") LocalDateTime earliestDeparture, @Param("now") LocalDateTime now);

    /**
     * Flight numbers of the aircraft's other flights (not {@code excludeId}, not in {@code ended}) whose block
     * overlaps {@code departure → arrival}, each block widened by {@code turnaround} so the aircraft has time on
     * the ground in between.
     */
    @Query("""
            SELECT f.flightNumber FROM Flight f
            WHERE f.aircraftId = :aircraftId AND f.id <> :excludeId AND f.status NOT IN :ended
              AND f.departureTime < :busyUntil AND f.arrivalTime > :busyFrom
            ORDER BY f.departureTime
            """)
    List<String> aircraftConflicts(@Param("aircraftId") UUID aircraftId, @Param("excludeId") UUID excludeId,
            @Param("ended") Collection<FlightStatus> ended, @Param("busyFrom") LocalDateTime busyFrom,
            @Param("busyUntil") LocalDateTime busyUntil, Pageable pageable);

    /** Flight numbers of the aircraft's flights not in {@code ended}, earliest first. */
    @Query("""
            SELECT f.flightNumber FROM Flight f
            WHERE f.aircraftId = :aircraftId AND f.status NOT IN :ended
            ORDER BY f.departureTime
            """)
    List<String> flightsOnAircraft(@Param("aircraftId") UUID aircraftId,
            @Param("ended") Collection<FlightStatus> ended, Pageable pageable);

    /** Gives a seat back. A cancelled flight already had its seats reset to capacity, so it is left alone. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Flight f
            SET f.availableSeats = f.availableSeats + 1, f.version = f.version + 1, f.updatedAt = :now
            WHERE f.id = :id AND f.status <> :cancelled
            """)
    int incrementSeat(@Param("id") UUID id, @Param("cancelled") FlightStatus cancelled,
            @Param("now") LocalDateTime now);
}
