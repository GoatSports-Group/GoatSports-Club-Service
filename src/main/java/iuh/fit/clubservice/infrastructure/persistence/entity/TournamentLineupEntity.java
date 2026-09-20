package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.LineupRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "tournament_lineups")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentLineupEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "lineup_id")
    private UUID lineupId;

    @Column(name = "registration_id", nullable = false)
    private UUID registrationId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "player_name")
    private String playerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "lineup_role", nullable = false)
    private LineupRole lineupRole;

    @Column(name = "shirt_number")
    private Integer shirtNumber;
}
