package iuh.fit.clubservice.presentation.response;

import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubResponse {
    private UUID clubId;
    private String name;
    private String description;
    private String avatarUrl;
    private String coverUrl;
    private String sportType;
    private String region;
    private String district;
    private String city;
    private UUID ownerId;
    private String ownerName;
    private ClubApprovalMode approvalMode;
    private ClubPrivacy privacy;
    private BigDecimal membershipFee;
    private Integer memberCount;
    private Integer maxMembers;
    private UUID groupChatId;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
