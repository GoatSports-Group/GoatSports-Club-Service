package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.Tournament;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubConversationSync {
    Optional<UUID> sync(Club club, List<UUID> activeMemberIds);

    Optional<UUID> syncTournament(Tournament tournament, List<UUID> participantIds);
}
