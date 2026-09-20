package iuh.fit.clubservice.infrastructure.external.kafka;

import iuh.fit.clubservice.application.port.external.ClubEventPublisher;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubActivity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;

@Configuration
public class NoopClubEventPublisher {
    @Bean
    @ConditionalOnMissingBean(ClubEventPublisher.class)
    ClubEventPublisher noopClubEventPublisher() {
        return new ClubEventPublisher() {
            @Override
            public void activityCreated(Club club, ClubActivity activity, List<UUID> receiverIds) {
                // khong lam gi
            }
        };
    }
}
