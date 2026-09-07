package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ClubMemberResponseCommand {
    private UUID membershipId;
    private UUID clubId;
    private UUID userId;
    private ClubRole role;
    private ClubMemberStatus status;
    private LocalDateTime joinedAt;
    private UUID respondedBy;
    private LocalDateTime respondedAt;
}
