package com.basarsy.skyline.reservation.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Temporary seat holds in Redis. Each held seat is one key ({@code SET NX} with a TTL), so a hold expires on its
 * own and two passengers can never hold the same seat: the second {@link #hold} returns false and becomes a 409.
 *
 * <p>A hold belongs to a <em>slot</em>: one passenger on one flight ({@link HoldOwner}). A slot holds at most one
 * seat; holding another seat releases the previous one in the same atomic script, so picking 12A then 14C never
 * leaves 12A held. The final guard against double booking is still the database's unique (flight, seat).
 */
@Component
public class SeatHoldStore {

    static final String SEAT_PREFIX = "skyline:seat-hold:";
    static final String SLOT_PREFIX = "skyline:seat-hold-slot:";

    /**
     * KEYS[1] = seat key, KEYS[2] = slot key; ARGV[1] = owner, ARGV[2] = ttl millis.
     * Fails (0) if someone else holds the seat; otherwise releases the slot's previous seat and holds this one.
     */
    private static final RedisScript<Long> HOLD = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current and current ~= ARGV[1] then return 0 end
            local previous = redis.call('GET', KEYS[2])
            if previous and previous ~= KEYS[1] and redis.call('GET', previous) == ARGV[1] then
              redis.call('DEL', previous)
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SET', KEYS[2], KEYS[1], 'PX', ARGV[2])
            return 1
            """, Long.class);

    /** KEYS[1] = slot key; ARGV[1] = owner. Releases the slot's seat if the slot still owns it. */
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[1])
            if previous and redis.call('GET', previous) == ARGV[1] then redis.call('DEL', previous) end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    /**
     * Who holds a seat: a user, for one passenger slot on one flight. Before booking the slot is the passenger's
     * position in the booking ("p0", "p1", ...); for an existing reservation it is the reservation id.
     */
    public record HoldOwner(UUID userId, String slot) {
        public HoldOwner {
            if (slot == null || !slot.matches("[A-Za-z0-9-]{1,64}")) {
                throw new IllegalArgumentException("Invalid hold slot: " + slot);
            }
        }

        static HoldOwner forPassenger(UUID userId, int passengerIndex) {
            return new HoldOwner(userId, "p" + passengerIndex);
        }

        static HoldOwner forReservation(UUID userId, UUID reservationId) {
            return new HoldOwner(userId, reservationId.toString());
        }

        String value() {
            return userId + ":" + slot;
        }

        static HoldOwner parse(String value) {
            int colon = value.indexOf(':');
            return new HoldOwner(UUID.fromString(value.substring(0, colon)), value.substring(colon + 1));
        }
    }

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SeatHoldStore(StringRedisTemplate redis, @Value("${skyline.seats.hold-ttl:PT15M}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }

    /** Holds {@code seat} for {@code owner}, releasing the owner's previous seat; false if someone else holds it. */
    public boolean hold(UUID flightId, String seat, HoldOwner owner) {
        Long result = redis.execute(HOLD, List.of(seatKey(flightId, seat), slotKey(flightId, owner)),
                owner.value(), String.valueOf(ttl.toMillis()));
        return result != null && result == 1L;
    }

    /** Releases whatever seat the owner holds on the flight (no-op if none or already expired). */
    public void release(UUID flightId, HoldOwner owner) {
        redis.execute(RELEASE, List.of(slotKey(flightId, owner)), owner.value());
    }

    /** Whether {@code owner} currently holds {@code seat}. */
    public boolean isHeldBy(UUID flightId, String seat, HoldOwner owner) {
        return owner.value().equals(redis.opsForValue().get(seatKey(flightId, seat)));
    }

    /** Every live hold on a flight: seat number to owner. */
    public Map<String, HoldOwner> holdsOnFlight(UUID flightId) {
        String prefix = SEAT_PREFIX + flightId + ":";
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(500).build())) {
            cursor.forEachRemaining(keys::add);
        }
        Map<String, HoldOwner> holds = new HashMap<>();
        if (keys.isEmpty()) {
            return holds;
        }
        List<String> owners = redis.opsForValue().multiGet(keys);
        for (int i = 0; i < keys.size(); i++) {
            String owner = owners == null ? null : owners.get(i);
            if (owner != null) { // expired between SCAN and MGET
                holds.put(keys.get(i).substring(prefix.length()), HoldOwner.parse(owner));
            }
        }
        return holds;
    }

    static String seatKey(UUID flightId, String seat) {
        return SEAT_PREFIX + flightId + ":" + seat;
    }

    static String slotKey(UUID flightId, HoldOwner owner) {
        return SLOT_PREFIX + flightId + ":" + owner.value();
    }
}
