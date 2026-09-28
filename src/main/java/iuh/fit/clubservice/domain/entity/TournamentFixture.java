package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.MatchStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentFixture extends BaseEntity {
    private UUID fixtureId;
    private UUID tournamentId;
    private String roundName;
    private Integer roundNumber;
    private Integer matchNumber;
    private UUID registration1Id;
    private UUID registration2Id;
    private Integer score1;
    private Integer score2;
    private UUID winnerRegistrationId;
    private UUID nextFixtureId;
    private UUID reservationId;
    private MatchStatus status;
    /** false: tỉ số mới/sửa chưa được tính ELO bên auth-service (job sẽ thử lại). */
    @lombok.Builder.Default
    private Boolean ratingSynced = Boolean.TRUE;
}
