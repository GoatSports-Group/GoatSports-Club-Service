package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubActivity;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;

import java.util.List;
import java.util.UUID;

public interface ClubEventPublisher {
    void activityCreated(Club club, ClubActivity activity, List<UUID> receiverIds);

    void membershipRequested(Club club, ClubMember member, String requesterName, List<UUID> managerIds);

    void membershipResponded(Club club, ClubMember member, boolean accepted);

    void invitationSent(Club club, ClubInvitation invitation);

    void invitationAnswered(Club club, ClubInvitation invitation, boolean accepted);
}
