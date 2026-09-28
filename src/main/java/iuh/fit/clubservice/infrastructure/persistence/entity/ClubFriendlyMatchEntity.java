package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.SportType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "club_friendly_matches")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClubFriendlyMatchEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "friendly_match_id")
    private UUID friendlyMatchId;
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    @Column(name = "club_id", nullable = false)
    private UUID clubId;
    @Column(name = "opponent_club_id", nullable = false)
    private UUID opponentClubId;
    @Column(name = "opponent_name", nullable = false, length = 150)
    private String opponentName;
    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false, length = 30)
    private SportType sportType;
    @Column(name = "play_format", length = 40)
    private String playFormat;
    @Column(name = "club_score", nullable = false)
    private Integer clubScore;
    @Column(name = "opponent_score", nullable = false)
    private Integer opponentScore;
    @Column(name = "result", nullable = false, length = 10)
    private String result;
    @Column(name = "elo_before", nullable = false)
    private Integer eloBefore;
    @Column(name = "elo_after", nullable = false)
    private Integer eloAfter;
    @Column(name = "played_at", nullable = false)
    private LocalDateTime playedAt;
}
