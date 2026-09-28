package iuh.fit.clubservice.infrastructure.scheduling;

import iuh.fit.clubservice.application.service.TournamentRatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

/** Tính lại ELO cho các trận đã có tỉ số mà lần tính ngay sau khi lưu bị lỗi (auth-service tạm không phản hồi). */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tournament.rating-job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TournamentRatingJob {
    private final TournamentRatingService ratingService;

    @Scheduled(fixedDelayString = "${tournament.rating-job.delay-ms:120000}", initialDelay = 45000)
    public void ratePendingFixtures() {
        for (UUID fixtureId : ratingService.findPendingFixtureIds()) {
            try {
                ratingService.syncFixture(fixtureId);
            } catch (RuntimeException exception) {
                log.warn("Chưa tính được ELO cho trận {}: {}", fixtureId, exception.getMessage());
            }
        }
    }
}
