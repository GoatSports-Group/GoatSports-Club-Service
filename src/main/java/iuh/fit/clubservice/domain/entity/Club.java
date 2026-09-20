package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.domain.enums.SportType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Club extends BaseEntity {
    private UUID clubId;
    private String name;
    private String description;
    private String logoUrl;
    /** Ma tinh/thanh de loc, vi du "ho-chi-minh". */
    private String city;
    /** Dong dia diem hien thi, vi du "Quan 7, TP. Ho Chi Minh". */
    private String location;
    private SportType sportType;
    private UUID ownerId;
    private ClubApprovalMode approvalMode;
    private ClubPrivacy privacy;
    private UUID conversationId;
    private Boolean active;
    private Integer winCount;
    private Integer lossCount;
    private Integer drawCount;
    private Integer matchCount;
    private Double winRate;
}
