package iuh.fit.clubservice.infrastructure.scheduling;

import iuh.fit.clubservice.application.service.TournamentRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tra suat cua dang ky qua han dong le phi (24 gio) va cua doi chua du nguoi khi het han dang ky.
 * Moi dang ky xu ly trong transaction rieng: mot cai loi khong chan cac cai con lai.
 * ponytail: nhieu instance cung chay se xu ly trung; expire() doc lai sau khi khoa giai nen van dung,
 * chi ton cong. Them ShedLock neu so instance lon.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tournament.expiry-job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TournamentRegistrationExpiryJob {
    private final TournamentRegistrationService registrationService;

    @Scheduled(fixedDelayString = "${tournament.expiry-job.delay-ms:60000}", initialDelay = 30000)
    public void expireOverdueRegistrations() {
        LocalDateTime now = LocalDateTime.now();
        for (UUID registrationId : registrationService.findOverdueRegistrationIds(now)) {
            try {
                registrationService.expire(registrationId, now);
            } catch (RuntimeException exception) {
                log.warn("Không đóng được đăng ký quá hạn {}: {}", registrationId, exception.getMessage());
            }
        }
    }
}
