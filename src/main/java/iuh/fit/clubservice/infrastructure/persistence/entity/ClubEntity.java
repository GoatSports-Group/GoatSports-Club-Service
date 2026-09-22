package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.domain.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clubs")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClubEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "club_id")
    private UUID clubId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 3000)
    private String description;

    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "banner_url", length = 1000)
    private String bannerUrl;

    @Column(name = "city", length = 64)
    private String city;

    @Column(name = "location")
    private String location;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "club_tags", joinColumns = @JoinColumn(name = "club_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "tag", nullable = false, length = 24)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false)
    private SportType sportType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode", nullable = false)
    private ClubApprovalMode approvalMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy", nullable = false)
    private ClubPrivacy privacy;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "disbanded_at")
    private LocalDateTime disbandedAt;

    @Column(name = "disbanded_by")
    private UUID disbandedBy;

    @Column(name = "win_count", nullable = false)
    private Integer winCount;

    @Column(name = "loss_count", nullable = false)
    private Integer lossCount;

    @Column(name = "draw_count", nullable = false)
    private Integer drawCount;

    @Column(name = "match_count", nullable = false)
    private Integer matchCount;

    @Column(name = "win_rate", nullable = false)
    private Double winRate;

}
