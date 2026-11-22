package com.basarsy.skyline.notification.service;

import com.basarsy.skyline.notification.repository.Recipient;
import com.basarsy.skyline.notification.repository.RecipientRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * "Check-in is open": once per booking and flight, when the check-in window (48h → 1h before the scheduled
 * departure, the check-in service's rule) opens. A booking made inside the window is reminded right away.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInReminders {

    static final Duration OPENS_BEFORE = Duration.ofHours(48);
    static final Duration CLOSES_BEFORE = Duration.ofHours(1);
    static final int BATCH = 200;

    private final RecipientRepository recipients;
    private final Inbox inbox;
    private final Clock clock;
    private final TransactionOperations transactions;

    @Scheduled(fixedDelayString = "${skyline.notification.check-in-reminder-interval}")
    void run() {
        send();
    }

    /**
     * Reminds one batch of due bookings and returns how many reservations it covered. The claim, the messages
     * and marking them reminded share one transaction (opened here, not by a proxy: the scheduler calls this
     * from {@link #run()} on the bean itself).
     */
    public int send() {
        Integer reminded = transactions.execute(status -> {
            List<Recipient> due = recipients.claimDueReminders(LocalDateTime.now(clock), OPENS_BEFORE, CLOSES_BEFORE,
                    BATCH);
            for (List<Recipient> booking : FlightChangeHandler.groupBy(due, r -> r.bookingId() + ":" + r.flightId())) {
                Recipient first = booking.get(0);
                Message message = Messages.checkInOpen(booking, "/checkin/" + first.pnr());
                String key = "check-in:" + first.bookingId() + ":" + first.flightId();
                inbox.toUser(first.userId(), message, key);
                inbox.toEmail(first.contactEmail(), message, key);
            }
            recipients.markReminded(due.stream().map(Recipient::reservationId).toList());
            return due.size();
        });
        int count = reminded == null ? 0 : reminded;
        if (count > 0) {
            log.info("Sent check-in reminders for {} reservation(s)", count);
        }
        return count;
    }
}
