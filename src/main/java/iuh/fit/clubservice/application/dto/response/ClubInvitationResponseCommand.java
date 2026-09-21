package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ClubInvitationResponseCommand {
    private UUID invitationId;
    private UUID inviteeId;
    private UUID invitedBy;
    private ClubInvitationStatus status;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private ClubResponseCommand club;
}
