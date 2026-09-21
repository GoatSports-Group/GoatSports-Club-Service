package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "club_members")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClubMemberEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "membership_id")
    private UUID membershipId;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ClubRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClubMemberStatus status;

    @Column(name = "intro_message", length = 1000)
    private String introMessage;

    @Column(name = "applied_at")
    private LocalDateTime requestedAt;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "responded_by")
    private UUID respondedBy;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

}
