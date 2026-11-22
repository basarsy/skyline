package com.basarsy.skyline.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the outbox publisher and its cleanup on a timer; off with {@code skyline.outbox.scheduling-enabled=false}.
 * A failed run (e.g. the database is down) is logged by Spring's scheduler and simply tried again next time.
 */
@Slf4j
@RequiredArgsConstructor

class OutboxScheduler {

    private final OutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${skyline.outbox.publish-interval}")
    void publish() {
        publisher.publishPending();
    }

    @Scheduled(fixedDelayString = "${skyline.outbox.cleanup-interval}",
            initialDelayString = "${skyline.outbox.cleanup-interval}")
    void cleanup() {
        int deleted = publisher.deletePublished();
        if (deleted > 0) {
            log.info("Deleted {} published outbox event(s) past retention", deleted);
        }
    }
}
