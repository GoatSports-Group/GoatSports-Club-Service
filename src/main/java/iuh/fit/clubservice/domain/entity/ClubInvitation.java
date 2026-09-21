package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClubInvitation extends BaseEntity {
    private UUID invitationId;
    private UUID clubId;
    private UUID inviteeId;
    private UUID invitedBy;
    private ClubInvitationStatus status;
    private String message;
    private LocalDateTime respondedAt;
}
