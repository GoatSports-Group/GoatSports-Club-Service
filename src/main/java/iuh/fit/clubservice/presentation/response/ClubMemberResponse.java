package iuh.fit.clubservice.presentation.response;

import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubMemberResponse {
    private UUID clubMemberId;
    private UUID clubId;
    private UUID userId;
    private String userName;
    private String userAvatar;
    private String userPhone;
    private ClubRole role;
    private ClubMemberStatus status;
    private String introMessage;
    private LocalDateTime appliedAt;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
}
