package com.basarsy.skyline.notification.service;

import com.basarsy.skyline.notification.config.NotificationProperties;
import com.basarsy.skyline.notification.repository.EmailOutboxRepository;
import com.basarsy.skyline.notification.repository.EmailOutboxRepository.PendingEmail;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Sends queued emails over SMTP (Mailpit in the local stack). A failure is recorded on the email with an
 * exponential backoff and ends the run, since the server is most likely unreachable for the rest of the batch
 * too; emails are retried until they go out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSender {

    private final EmailOutboxRepository emails;
    private final JavaMailSender mailSender;
    private final TransactionOperations transactions;
    private final NotificationProperties properties;

    @Scheduled(fixedDelayString = "${skyline.notification.email-send-interval}")
    void run() {
        sendPending();
    }

    /** Sends one batch of due emails and returns how many went out. */
    public int sendPending() {
        Integer sent = transactions.execute(status -> {
            List<PendingEmail> batch = emails.claimDue(properties.emailBatchSize());
            int count = 0;
            for (PendingEmail email : batch) {
                try {
                    mailSender.send(mail(email));
                    emails.markSent(email.id());
                    count++;
                } catch (MailException e) {
                    int attempts = email.attempts() + 1;
                    Duration backoff = properties.backoffAfter(attempts);
                    emails.markFailed(email.id(), attempts, e.toString(), backoff);
                    log.warn("Sending email {} failed (attempt {}), retrying in {}: {}", email.id(), attempts,
                            backoff, e.getMessage());
                    break;
                }
            }
            return count;
        });
        if (sent != null && sent > 0) {
            log.info("Sent {} email(s)", sent);
        }
        return sent == null ? 0 : sent;
    }

    private SimpleMailMessage mail(PendingEmail email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email.recipient());
        message.setSubject(email.subject());
        message.setText(email.body());
        return message;
    }
}
