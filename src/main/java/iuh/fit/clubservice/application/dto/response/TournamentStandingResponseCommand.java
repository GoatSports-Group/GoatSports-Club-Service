package iuh.fit.clubservice.application.dto.response;

import lombok.*;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TournamentStandingResponseCommand {
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
