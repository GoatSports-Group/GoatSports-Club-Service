package iuh.fit.clubservice.presentation.response;

import iuh.fit.clubservice.domain.enums.MatchStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentFixtureResponse {
    private UUID fixtureId;
    private UUID tournamentId;
    private String roundName;
    private Integer roundNumber;
    private Integer matchNumber;
    private UUID registration1Id;
    private String team1Name;
    private UUID registration2Id;
    private String team2Name;
    private Integer score1;
    private Integer score2;
    private UUID winnerRegistrationId;
    private LocalDateTime scheduledTime;
    private String courtName;
    private MatchStatus status;
}
