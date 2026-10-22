package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "tournament_fixtures")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentFixtureEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fixture_id")
    private UUID fixtureId;
    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;
    @Column(name = "round_name")
    private String roundName;
    @Column(name = "round_number")
    private Integer roundNumber;
    @Column(name = "match_number")
    private Integer matchNumber;
    @Column(name = "registration1_id")
    private UUID registration1Id;
    @Column(name = "registration2_id")
    private UUID registration2Id;
    @Column(name = "score1")
    private Integer score1;
    @Column(name = "score2")
    private Integer score2;
    @Column(name = "winner_registration_id")
    private UUID winnerRegistrationId;
    @Column(name = "next_fixture_id")
    private UUID nextFixtureId;
    @Column(name = "reservation_id", unique = true)
    private UUID reservationId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MatchStatus status;
    @Column(name = "rating_synced", nullable = false)
    @Builder.Default
    private Boolean ratingSynced = Boolean.TRUE;
}
