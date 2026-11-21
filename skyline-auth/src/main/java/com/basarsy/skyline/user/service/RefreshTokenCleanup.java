package com.basarsy.skyline.user.service;

import com.basarsy.skyline.user.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Expired refresh tokens can't be used or reused any more, so their rows are only dead weight. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanup {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Scheduled(cron = "${skyline.auth.refresh-token.cleanup-cron:0 17 * * * *}")
    @Transactional
    public void deleteExpired() {
        int deleted = refreshTokenRepository.deleteExpired(LocalDateTime.now(clock));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }
}
