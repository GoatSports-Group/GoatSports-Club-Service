package iuh.fit.clubservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "tournament_standings")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentStandingEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "standing_id")
    private UUID standingId;

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "registration_id", nullable = false, unique = true)
    private UUID registrationId;

    @Column(name = "played", nullable = false)
    private Integer played;

    @Column(name = "won", nullable = false)
    private Integer won;

    @Column(name = "drawn", nullable = false)
    private Integer drawn;

    @Column(name = "lost", nullable = false)
    private Integer lost;

    @Column(name = "score_for", nullable = false)
    private Integer scoreFor;

    @Column(name = "score_against", nullable = false)
    private Integer scoreAgainst;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "rank", nullable = false)
    private Integer rank;
}
