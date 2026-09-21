package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubInvitationRepository {
    ClubInvitation save(ClubInvitation invitation);

    Optional<ClubInvitation> findById(UUID invitationId);

    List<ClubInvitation> findByInviteeIdAndStatus(UUID inviteeId, ClubInvitationStatus status);

    List<ClubInvitation> findByClubIdAndStatus(UUID clubId, ClubInvitationStatus status);

    Optional<ClubInvitation> findByClubIdAndInviteeIdAndStatus(
            UUID clubId, UUID inviteeId, ClubInvitationStatus status);
}
