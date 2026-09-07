package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.MatchStatus;
import lombok.*;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TournamentFixtureResponseCommand {
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
}
