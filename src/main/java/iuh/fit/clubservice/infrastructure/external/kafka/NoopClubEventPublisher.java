package iuh.fit.clubservice.infrastructure.external.kafka;

import iuh.fit.clubservice.application.port.external.ClubEventPublisher;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubActivity;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;

@Configuration
public class NoopClubEventPublisher {
    @Bean
    @ConditionalOnMissingBean(TournamentEventPublisher.class)
    TournamentEventPublisher noopTournamentEventPublisher() {
        return (eventType, tournament, receiverId, actorId, payload) -> { };
    }

    @Bean
    @ConditionalOnMissingBean(ClubEventPublisher.class)
    ClubEventPublisher noopClubEventPublisher() {
        return new ClubEventPublisher() {
            @Override
            public void activityCreated(Club club, ClubActivity activity, List<UUID> receiverIds) {
            }

            @Override
            public void membershipRequested(Club club, ClubMember member, String requesterName,
                                            List<UUID> managerIds) {
            }

            @Override
            public void invitationSent(Club club, ClubInvitation invitation) {
            }

            @Override
            public void invitationAnswered(Club club, ClubInvitation invitation, boolean accepted) {
            }

            @Override
            public void membershipResponded(Club club, ClubMember member, boolean accepted) {
            }

            @Override
            public void clubDisbanded(Club club, UUID actorId, List<UUID> receiverIds) {
            }

            @Override
            public void ownershipTransferred(Club club, UUID previousOwnerId, UUID newOwnerId) {
            }
        };
    }
}
