package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ClubResponseCommand {
    private UUID clubId;
    private UUID ownerId;
    private String name;
    private String description;
    private String logoUrl;
    private String city;
    private String location;
    private List<String> tags;
    private SportType sportType;
    private ClubPrivacy privacy;
    private ClubApprovalMode approvalMode;
    private UUID conversationId;
    private Boolean active;
    private Integer winCount;
    private Integer lossCount;
    private Integer drawCount;
    private Integer matchCount;
    private Double winRate;
    private long memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
