package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClubMember extends BaseEntity {
    private UUID membershipId;
    private UUID clubId;
    private UUID userId;
    private ClubRole role;
    private ClubMemberStatus status;
    private String introMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime joinedAt;
    private UUID respondedBy;
    private LocalDateTime respondedAt;
}
