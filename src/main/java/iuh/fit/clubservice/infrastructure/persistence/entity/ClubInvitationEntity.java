package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "club_invitations")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClubInvitationEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "invitation_id")
    private UUID invitationId;
    @Column(name = "club_id", nullable = false)
    private UUID clubId;
    @Column(name = "invitee_id", nullable = false)
    private UUID inviteeId;
    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ClubInvitationStatus status;
    @Column(name = "message", length = 500)
    private String message;
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
