package iuh.fit.clubservice.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentStanding extends BaseEntity {
    private UUID standingId;
    private UUID tournamentId;
    private UUID registrationId;
    private Integer played;
    private Integer won;
    private Integer drawn;
    private Integer lost;
    private Integer scoreFor;
    private Integer scoreAgainst;
    private Integer points;
    private Integer rank;
}
