package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.domain.enums.SportType;
import lombok.*;

import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateClubCommand {
    private UUID ownerId;
    private String name;
    private String description;
    private String logoUrl;
    private String city;
    private String location;
    private SportType sportType;
    private ClubPrivacy privacy;
    private ClubApprovalMode approvalMode;
}
