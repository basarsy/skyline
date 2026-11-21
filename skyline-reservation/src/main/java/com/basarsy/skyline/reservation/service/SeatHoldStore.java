package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Temporary seat holds in Redis, one hash per flight: reading a flight's holds is a single HGETALL (no keyspace
 * scan), and every script touches only that hash, declared in KEYS. Two passengers can never hold the same seat:
 * the second {@link #hold} returns false and becomes a 409.
 *
 * <p>Fields: {@code s:<seat>} = {@code owner|cabin:fare:price|expiresAtMillis}, and {@code o:<owner>} = the seat that owner
 * holds. A hold carries the price it was shown at, so confirming within the hold charges that price even if the
 * flight's surcharge changes meanwhile. A slot (one passenger on one flight, {@link HoldOwner}) holds at most one
 * seat: holding another releases the previous one in the same atomic script. Expiry is per seat (checked against
 * Redis time); the hash itself expires once its newest hold has. The final guard against double booking is still
 * the database's unique (flight, seat).
 */
@Component
public class SeatHoldStore {

    /** The most passengers one booking (one group) can have. */
    public static final int MAX_PASSENGERS = 9;

    static final String PREFIX = "skyline:seat-holds:";

    /**
     * KEYS[1] = the flight's hash; ARGV = seat, owner, ttl millis, price. Fails (0) if someone else holds the seat
     * and their hold has not expired; otherwise releases the owner's previous seat and holds this one.
     */
    private static final RedisScript<Long> HOLD = new DefaultRedisScript<>("""
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local current = redis.call('HGET', KEYS[1], 's:' .. ARGV[1])
            if current then
              local owner, expires = string.match(current, '^(.-)|.-|(%d+)$')
              if owner ~= ARGV[2] and tonumber(expires) > now then return 0 end
            end
            local previous = redis.call('HGET', KEYS[1], 'o:' .. ARGV[2])
            if previous and previous ~= ARGV[1] then
              local held = redis.call('HGET', KEYS[1], 's:' .. previous)
              if held and string.sub(held, 1, #ARGV[2] + 1) == ARGV[2] .. '|' then
                redis.call('HDEL', KEYS[1], 's:' .. previous)
              end
            end
            local expires = now + tonumber(ARGV[3])
            redis.call('HSET', KEYS[1], 's:' .. ARGV[1], ARGV[2] .. '|' .. ARGV[4] .. '|' .. expires, 'o:' .. ARGV[2], ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);

    /** KEYS[1] = the flight's hash; ARGV[1] = owner. Releases the owner's seat if the owner still holds it. */
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            local previous = redis.call('HGET', KEYS[1], 'o:' .. ARGV[1])
            if previous then
              local held = redis.call('HGET', KEYS[1], 's:' .. previous)
              if held and string.sub(held, 1, #ARGV[1] + 1) == ARGV[1] .. '|' then
                redis.call('HDEL', KEYS[1], 's:' .. previous)
              end
              redis.call('HDEL', KEYS[1], 'o:' .. ARGV[1])
            end
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

        /** A booking has at most {@link #MAX_PASSENGERS} passengers, numbered from 0. */
        static HoldOwner forPassenger(UUID userId, int passengerIndex) {
            if (passengerIndex < 0 || passengerIndex >= MAX_PASSENGERS) {
                throw new SkylineException("passenger must be between 0 and " + (MAX_PASSENGERS - 1),
                        HttpStatus.BAD_REQUEST);
            }
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

    /** A live hold: who has it, and the price it was shown at for that cabin and fare. */
    public record Hold(HoldOwner owner, CabinClass cabin, FareType fare, BigDecimal price) {

        /** The held price, if the hold was priced for this cabin and fare. */
        public Optional<BigDecimal> priceFor(CabinClass cabin, FareType fare) {
            return this.cabin == cabin && this.fare == fare ? Optional.of(price) : Optional.empty();
        }
    }

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final Clock clock;

    public SeatHoldStore(StringRedisTemplate redis, @Value("${skyline.seats.hold-ttl:PT15M}") Duration ttl,
            Clock clock) {
        this.redis = redis;
        this.ttl = ttl;
        this.clock = clock;
    }

    public Duration ttl() {
        return ttl;
    }

    /**
     * Holds {@code seat} for {@code owner}, priced at {@code price} for that cabin and fare, releasing the owner's
     * previous seat; false if someone else holds it.
     */
    public boolean hold(UUID flightId, String seat, HoldOwner owner, CabinClass cabin, FareType fare,
            BigDecimal price) {
        Long result = redis.execute(HOLD, List.of(key(flightId)), seat, owner.value(),
                String.valueOf(ttl.toMillis()), cabin + ":" + fare + ":" + price.toPlainString());
        return result != null && result == 1L;
    }

    /** Releases whatever seat the owner holds on the flight (no-op if none or already expired). */
    public void release(UUID flightId, HoldOwner owner) {
        redis.execute(RELEASE, List.of(key(flightId)), owner.value());
    }

    /** The owner's live hold on {@code seat}, if they have one. */
    public Optional<Hold> heldBy(UUID flightId, String seat, HoldOwner owner) {
        Object value = redis.opsForHash().get(key(flightId), "s:" + seat);
        return value == null ? Optional.empty() : parse(value.toString()).filter(hold -> hold.owner().equals(owner));
    }

    /** Every live hold on a flight: seat number to hold. */
    public Map<String, Hold> holdsOnFlight(UUID flightId) {
        Map<String, Hold> holds = new HashMap<>();
        redis.opsForHash().entries(key(flightId)).forEach((field, value) -> {
            String name = field.toString();
            if (name.startsWith("s:")) {
                parse(value.toString()).ifPresent(hold -> holds.put(name.substring(2), hold));
            }
        });
        return holds;
    }

    /** {@code owner|cabin:fare:price|expiresAtMillis}, or empty once expired. */
    private Optional<Hold> parse(String value) {
        String[] parts = value.split("\\|");
        if (parts.length != 3 || Long.parseLong(parts[2]) <= clock.millis()) {
            return Optional.empty();
        }
        String[] priced = parts[1].split(":");
        return Optional.of(new Hold(HoldOwner.parse(parts[0]), CabinClass.valueOf(priced[0]),
                FareType.valueOf(priced[1]), new BigDecimal(priced[2])));
    }

    static String key(UUID flightId) {
        return PREFIX + flightId;
    }
}
