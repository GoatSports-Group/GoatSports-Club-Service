package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubActivity;

import java.util.List;
import java.util.UUID;

public interface ClubEventPublisher {
    void activityCreated(Club club, ClubActivity activity, List<UUID> receiverIds);
}
