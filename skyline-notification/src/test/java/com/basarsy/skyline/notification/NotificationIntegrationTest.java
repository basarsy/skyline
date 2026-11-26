package com.basarsy.skyline.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.basarsy.skyline.common.security.JwtService;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.notification.event.BookingEvent;
import com.basarsy.skyline.notification.event.FlightChange;
import com.basarsy.skyline.notification.repository.RecipientRepository;
import com.basarsy.skyline.notification.service.BookingEventHandler;
import com.basarsy.skyline.notification.service.CheckInReminders;
import com.basarsy.skyline.notification.service.EmailSender;
import com.basarsy.skyline.notification.service.FlightChangeHandler;
import com.basarsy.skyline.notification.service.Inbox;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** The handlers, jobs and API against real PostgreSQL with the Flyway schema; SMTP is a mock. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class NotificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static final LocalDateTime DEPARTURE = LocalDateTime.of(2030, 6, 1, 9, 0);

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired BookingEventHandler bookingEvents;
    @Autowired FlightChangeHandler flightChanges;
    @Autowired EmailSender emailSender;
    @Autowired RecipientRepository recipients;
    @Autowired Inbox inbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;

    UUID userId;
    UUID flightId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM email_outbox");
        jdbc.update("DELETE FROM booking_recipients");
        jdbc.update("DELETE FROM flight_states");
        userId = UUID.randomUUID();
        flightId = UUID.randomUUID();
        reset(mailSender);
    }

    // ------------------------------------------------------------------ bookings

    @Test
    void aBookingIsConfirmedOnceInTheInboxAndByEmailEvenWhenRedelivered() {
        BookingEvent booked = booked(UUID.randomUUID(), "family@example.com", item("Ada"), item("Alan"));

        bookingEvents.handle(booked);
        bookingEvents.handle(booked);

        assertThat(count("notifications")).isEqualTo(1);
        Map<String, Object> entry = jdbc.queryForMap("SELECT * FROM notifications");
        assertThat(entry.get("user_id")).isEqualTo(userId);
        assertThat(entry.get("kind")).isEqualTo("BOOKING_CONFIRMED");
        assertThat(entry.get("link")).isEqualTo("/trips/" + booked.reservations().get(0).reservationId());
        Map<String, Object> email = jdbc.queryForMap("SELECT * FROM email_outbox");
        assertThat(email.get("recipient")).isEqualTo("family@example.com");
        assertThat((String) email.get("body"))
                .contains("Ada Lovelace", "Alan Lovelace", "SK100 IST → LHR, Sat 1 Jun 12:00")
                .contains("http://skyline.test/trips/");
        assertThat(recipients.activeOnFlight(flightId)).hasSize(2);
    }

    @Test
    void aCancellationThatArrivesFirstKeepsTheReservationInactive() {
        BookingEvent.Item ada = item("Ada");
        UUID bookingId = UUID.randomUUID();

        bookingEvents.handle(new BookingEvent(BookingEvent.CANCELLED, bookingId, userId, "ada@example.com",
                List.of(ada), DEPARTURE.minusDays(5)));
        bookingEvents.handle(booked(bookingId, "ada@example.com", ada));

        assertThat(recipients.activeOnFlight(flightId)).isEmpty();
        assertThat(jdbc.queryForList("SELECT kind FROM notifications ORDER BY created_at", String.class))
                .containsExactlyInAnyOrder("BOOKING_CANCELLED", "BOOKING_CONFIRMED");
    }

    // ------------------------------------------------------------------ bookings handled after a flight change

    @Test
    void aBookingHandledAfterARescheduleItPrecededTakesTheNewTime() {
        LocalDateTime later = DEPARTURE.plusHours(3);
        flightChanges.handle(reschedule(later, DEPARTURE.minusDays(4))); // after the booking (T-5d), handled first

        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));

        assertThat(recipients.activeOnFlight(flightId)).singleElement()
                .satisfies(r -> assertThat(r.departureTime()).isEqualTo(later));
        assertThat(jdbc.queryForObject("SELECT body FROM email_outbox WHERE subject LIKE 'Booking confirmed%'",
                String.class)).contains("15:00").doesNotContain("12:00");
    }

    @Test
    void aBookingHandledAfterARenumberItPrecededTakesTheNewNumber() {
        flightChanges.handle(renumber("SK100", "SK900", 4));

        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));

        assertThat(recipients.activeOnFlight(flightId)).singleElement()
                .satisfies(r -> assertThat(r.flightNumber()).isEqualTo("SK900"));
    }

    @Test
    void aBookingHandledAfterTheFlightWasCancelledIsInactiveAndToldOfTheCancellation() {
        flightChanges.handle(new FlightChange(FlightChange.CANCELLED, flightId, "SK100", "IST", "LHR",
                "Europe/Istanbul", "Europe/London", "CANCELLED", "SCHEDULED", DEPARTURE, DEPARTURE,
                DEPARTURE.plusHours(4), null, null, null, null, DEPARTURE.minusDays(4)));

        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));

        assertThat(recipients.activeOnFlight(flightId)).isEmpty();
        assertThat(jdbc.queryForList("SELECT subject FROM email_outbox", String.class))
                .containsExactly("SK100 is cancelled");
        assertThat(remind(DEPARTURE.minusHours(47))).as("no reminder for a cancelled flight").isZero();
    }

    @Test
    void aBookingMadeAfterTheLatestChangeKeepsItsOwnDetails() {
        flightChanges.handle(renumber("SK050", "SK100", 6)); // before the booking (T-5d): already in its details

        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));

        assertThat(recipients.activeOnFlight(flightId)).singleElement()
                .satisfies(r -> assertThat(r.flightNumber()).isEqualTo("SK100"));
    }

    // ------------------------------------------------------------------ flight changes

    @Test
    void aDelayReachesEachAccountOnceAndEachAddressOnceWithEveryPassenger() {
        bookingEvents.handle(booked(UUID.randomUUID(), "family@example.com", item("Ada"), item("Alan")));
        UUID otherUser = UUID.randomUUID();
        bookingEvents.handle(new BookingEvent(BookingEvent.BOOKED, UUID.randomUUID(), otherUser, "grace@example.com",
                List.of(item("Grace")), DEPARTURE.minusDays(4)));
        clearMessages();

        FlightChange delay = change(FlightChange.DELAYED, DEPARTURE.plusMinutes(95), null);
        flightChanges.handle(delay);
        flightChanges.handle(delay);

        assertThat(jdbc.queryForList("SELECT user_id FROM notifications", UUID.class))
                .containsExactlyInAnyOrder(userId, otherUser);
        List<Map<String, Object>> emails = jdbc.queryForList("SELECT * FROM email_outbox ORDER BY recipient");
        assertThat(emails).extracting(e -> e.get("recipient")).containsExactly("family@example.com", "grace@example.com");
        assertThat((String) emails.get(0).get("subject")).isEqualTo("SK100 is delayed");
        assertThat((String) emails.get(0).get("body"))
                .contains("delayed by 1 h 35 min", "13:35 instead of 12:00", "Ada Lovelace, Alan Lovelace");
        assertThat((String) emails.get(1).get("body")).contains("Grace Lovelace").doesNotContain("Ada");
    }

    @Test
    void boardingOnlyGoesToTheInboxAndArrivalToNobody() {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));
        clearMessages();

        flightChanges.handle(statusChange("BOARDING"));
        flightChanges.handle(statusChange("ARRIVED"));
        flightChanges.handle(new FlightChange("SOMETHING_NEW", flightId, "SK100", "IST", "LHR", "Europe/Istanbul",
                "Europe/London", "SCHEDULED", "SCHEDULED", DEPARTURE, DEPARTURE, DEPARTURE.plusHours(4), null, null,
                null, null, DEPARTURE.minusHours(1)));

        assertThat(jdbc.queryForList("SELECT kind FROM notifications", String.class)).containsExactly("FLIGHT_BOARDING");
        assertThat(count("email_outbox")).isZero();
    }

    @Test
    void aGateChangeNamesTheOldAndNewGate() {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));
        clearMessages();

        flightChanges.handle(change(FlightChange.GATE_CHANGED, null, "B4"));

        assertThat(jdbc.queryForObject("SELECT body FROM email_outbox", String.class))
                .contains("now departs from gate B12 (was B4)", "Boarding starts 45 minutes before departure (11:15)");
    }

    @Test
    void aNewFlightNumberIsAnnouncedAndUsedFromThenOn() {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));
        clearMessages();

        flightChanges.handle(new FlightChange(FlightChange.FLIGHT_NUMBER_CHANGED, flightId, "SK900", "IST", "LHR",
                "Europe/Istanbul", "Europe/London", "SCHEDULED", "SCHEDULED", DEPARTURE, DEPARTURE,
                DEPARTURE.plusHours(4), null, null, null, null, DEPARTURE.minusDays(3), "SK100"));

        assertThat(jdbc.queryForObject("SELECT subject FROM email_outbox", String.class))
                .isEqualTo("SK100 is now SK900");
        assertThat(jdbc.queryForObject("SELECT kind FROM notifications", String.class))
                .isEqualTo("FLIGHT_NUMBER_CHANGED");
        clearMessages();
        remind(DEPARTURE.minusHours(47));
        assertThat(jdbc.queryForObject("SELECT subject FROM email_outbox", String.class))
                .as("the reminder uses the new number").isEqualTo("Check-in is open for SK900");
    }

    @Test
    void aReplayedOlderRenumberLeavesTheCurrentNumber() {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada"))); // SK100
        flightChanges.handle(renumber("SK100", "SK200", 3));
        flightChanges.handle(renumber("SK200", "SK300", 2));

        flightChanges.handle(renumber("SK100", "SK200", 3)); // redelivered, or replayed from the DLT

        assertThat(recipients.activeOnFlight(flightId)).singleElement()
                .satisfies(r -> assertThat(r.flightNumber()).isEqualTo("SK300"));
    }

    FlightChange renumber(String from, String to, int daysBefore) {
        return new FlightChange(FlightChange.FLIGHT_NUMBER_CHANGED, flightId, to, "IST", "LHR", "Europe/Istanbul",
                "Europe/London", "SCHEDULED", "SCHEDULED", DEPARTURE, DEPARTURE, DEPARTURE.plusHours(4), null, null,
                null, null, DEPARTURE.minusDays(daysBefore), from);
    }

    @Test
    void anAirlineCancellationTellsPassengersAndEndsTheirBookings() {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));
        clearMessages();

        flightChanges.handle(change(FlightChange.CANCELLED, null, null));

        assertThat(jdbc.queryForObject("SELECT subject FROM email_outbox", String.class)).isEqualTo("SK100 is cancelled");
        assertThat(recipients.activeOnFlight(flightId)).isEmpty();
    }

    @Test
    void aRescheduleMovesTheReminderToTheNewTime() {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));
        LocalDateTime later = DEPARTURE.plusDays(1);

        flightChanges.handle(new FlightChange(FlightChange.RESCHEDULED, flightId, "SK100", "IST", "LHR",
                "Europe/Istanbul", "Europe/London", "SCHEDULED", "SCHEDULED", later, DEPARTURE, later.plusHours(4),
                null, null, null, null, DEPARTURE.minusDays(2)));

        assertThat(recipients.activeOnFlight(flightId)).singleElement()
                .satisfies(r -> assertThat(r.departureTime()).isEqualTo(later));
    }

    @Test
    void aFlightMovedOutOfTheOpenWindowIsRemindedAgainWhenTheNewWindowOpens() {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));
        assertThat(remind(DEPARTURE.minusHours(47))).isEqualTo(1);
        LocalDateTime later = DEPARTURE.plusDays(3);

        flightChanges.handle(reschedule(later, DEPARTURE.minusHours(46)));

        assertThat(remind(DEPARTURE.minusHours(46))).as("the new window is not open yet").isZero();
        assertThat(remind(later.minusHours(47))).isEqualTo(1);
        assertThat(remind(later.minusHours(46))).as("only once").isZero();
        assertThat(jdbc.queryForList("SELECT kind FROM notifications WHERE kind = 'CHECK_IN_OPEN'", String.class))
                .hasSize(2);
    }

    @Test
    void aFlightMovedWhileCheckInForTheNewTimeIsOpenIsNotRemindedTwice() {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));
        assertThat(remind(DEPARTURE.minusHours(48).plusMinutes(2))).as("just after the window opened").isEqualTo(1);

        // Moved 30 minutes later at T-47h: check-in for the new time opened at T-47h30, so it is open already.
        flightChanges.handle(reschedule(DEPARTURE.plusMinutes(30), DEPARTURE.minusHours(47)));

        assertThat(remind(DEPARTURE.minusHours(46))).isZero();
        assertThat(jdbc.queryForList("SELECT kind FROM notifications WHERE kind = 'CHECK_IN_OPEN'", String.class))
                .hasSize(1);
    }

    @Test
    void theLongestValidAddressGetsEveryMessage() {
        // 254 characters, the most an address may have: spelled out in a dedupe key it overflowed the column.
        String address = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(57) + ".com";
        assertThat(address).hasSize(254);
        bookingEvents.handle(booked(UUID.randomUUID(), address, item("Ada")));

        flightChanges.handle(change(FlightChange.DELAYED, DEPARTURE.plusMinutes(30), null));
        assertThat(remind(DEPARTURE.minusHours(47))).isEqualTo(1);

        assertThat(jdbc.queryForList("SELECT recipient FROM email_outbox", String.class))
                .hasSize(3).containsOnly(address);
        assertThat(jdbc.queryForList("SELECT length(dedupe_key) FROM email_outbox", Integer.class))
                .allSatisfy(length -> assertThat(length).isLessThanOrEqualTo(255));
    }

    // ------------------------------------------------------------------ check-in reminders

    @Test
    void checkInRemindersGoOutOncePerBookingWhenTheWindowOpens() {
        bookingEvents.handle(booked(UUID.randomUUID(), "family@example.com", item("Ada"), item("Alan")));
        clearMessages();

        assertThat(remind(DEPARTURE.minusHours(49))).as("window not open yet").isZero();
        assertThat(remind(DEPARTURE.minusHours(47))).isEqualTo(2);
        assertThat(remind(DEPARTURE.minusHours(46))).as("only once").isZero();

        assertThat(jdbc.queryForList("SELECT kind FROM notifications", String.class)).containsExactly("CHECK_IN_OPEN");
        Map<String, Object> email = jdbc.queryForMap("SELECT * FROM email_outbox");
        assertThat((String) email.get("subject")).isEqualTo("Check-in is open for SK100");
        assertThat((String) email.get("body")).contains("Ada Lovelace, Alan Lovelace", "http://skyline.test/checkin/");
    }

    @Test
    void noReminderOnceCheckInHasClosed() {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));

        assertThat(remind(DEPARTURE.minusMinutes(30))).isZero();
    }

    // ------------------------------------------------------------------ emails

    @Test
    void queuedEmailsAreSentOnceAndAFailureIsRetriedLater() {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(emailSender.sendPending()).isZero();
        Map<String, Object> failed = jdbc.queryForMap("SELECT * FROM email_outbox");
        assertThat(failed.get("attempts")).isEqualTo(1);
        assertThat(failed.get("sent_at")).isNull();
        assertThat(emailSender.sendPending()).as("backing off").isZero();

        reset(mailSender);
        jdbc.update("UPDATE email_outbox SET next_attempt_at = now()");
        assertThat(emailSender.sendPending()).isEqualTo(1);
        assertThat(emailSender.sendPending()).as("nothing left").isZero();
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("ada@example.com");
        assertThat(mail.getValue().getFrom()).isEqualTo("Skyline <no-reply@skyline.local>");
    }

    // ------------------------------------------------------------------ API

    @Test
    void usersReadAndMarkOnlyTheirOwnNotifications() throws Exception {
        bookingEvents.handle(booked(UUID.randomUUID(), "ada@example.com", item("Ada")));
        flightChanges.handle(change(FlightChange.GATE_CHANGED, null, null));
        String mine = bearer(userId);
        String someoneElse = bearer(UUID.randomUUID());
        UUID gateEntry = jdbc.queryForObject("SELECT id FROM notifications WHERE kind = 'FLIGHT_GATE'", UUID.class);

        mvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].read").value(false));
        mvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, mine))
                .andExpect(jsonPath("$.data.count").value(2));
        mvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, someoneElse))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mvc.perform(post("/api/v1/notifications/" + gateEntry + "/read").header(HttpHeaders.AUTHORIZATION, someoneElse))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/notifications/" + gateEntry + "/read").header(HttpHeaders.AUTHORIZATION, mine))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, mine))
                .andExpect(jsonPath("$.data.count").value(1));
        mvc.perform(post("/api/v1/notifications/read-all").header(HttpHeaders.AUTHORIZATION, mine))
                .andExpect(jsonPath("$.data.count").value(0));
        mvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, mine))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void anonymousCallersGet401() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ------------------------------------------------------------------ fixtures

    /** Runs the reminder job once at {@code nowUtc} exactly as the scheduler does: no transaction around it. */
    int remind(LocalDateTime nowUtc) {
        Clock clock = Clock.fixed(nowUtc.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new CheckInReminders(recipients, inbox, clock, transactions).send();
    }

    void clearMessages() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM email_outbox");
    }

    long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    BookingEvent booked(UUID bookingId, String email, BookingEvent.Item... items) {
        return new BookingEvent(BookingEvent.BOOKED, bookingId, userId, email, List.of(items), DEPARTURE.minusDays(5));
    }

    BookingEvent.Item item(String firstName) {
        return new BookingEvent.Item(UUID.randomUUID(), "P" + firstName.toUpperCase().substring(0, 3) + "23",
                firstName + " Lovelace", flightId, "SK100", "IST", "LHR", "Europe/Istanbul", DEPARTURE);
    }

    /** A change on the test flight; departs 09:00 UTC = 12:00 in Istanbul, gate B12 after the change. */
    FlightChange change(String type, LocalDateTime estimate, String previousGate) {
        return new FlightChange(type, flightId, "SK100", "IST", "LHR", "Europe/Istanbul", "Europe/London",
                estimate != null ? "DELAYED" : "SCHEDULED", "SCHEDULED", DEPARTURE, DEPARTURE, DEPARTURE.plusHours(4),
                estimate, null, "B12", previousGate, DEPARTURE.minusHours(3));
    }

    FlightChange reschedule(LocalDateTime departure, LocalDateTime occurredAt) {
        return new FlightChange(FlightChange.RESCHEDULED, flightId, "SK100", "IST", "LHR", "Europe/Istanbul",
                "Europe/London", "SCHEDULED", "SCHEDULED", departure, DEPARTURE, departure.plusHours(4), null, null,
                null, null, occurredAt);
    }

    FlightChange statusChange(String status) {
        return new FlightChange(FlightChange.STATUS_CHANGED, flightId, "SK100", "IST", "LHR", "Europe/Istanbul",
                "Europe/London", status, "SCHEDULED", DEPARTURE, DEPARTURE, DEPARTURE.plusHours(4), null, null, "B12",
                "B12", DEPARTURE.minusMinutes(40).plusSeconds(status.length()));
    }

    String bearer(UUID user) {
        var details = new SkylineUserDetails(user, "user@skyline.test", null,
                List.of(new SimpleGrantedAuthority("ROLE_PASSENGER")));
        return "Bearer " + jwtService.generateAccessToken(details);
    }
}
