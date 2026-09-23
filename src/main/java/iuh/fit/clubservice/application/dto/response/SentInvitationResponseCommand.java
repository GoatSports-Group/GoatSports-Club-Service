package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SentInvitationResponseCommand {
    private UUID invitationId;
    private ClubInvitationStatus status;
    private String message;
    private UUID invitedBy;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private PlayerClubRelation relation;
    private ScoutedPlayerResponseCommand player;
}
