package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MyClubMembershipResponseCommand {
    private UUID membershipId;
    private ClubRole role;
    private ClubMemberStatus status;
    private LocalDateTime joinedAt;
    private LocalDateTime requestedAt;
    private ClubResponseCommand club;
}
