package iuh.fit.clubservice.presentation.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentStandingResponse {
    private UUID registrationId;
    private String teamName;
    private Integer matchesPlayed;
    private Integer won;
    private Integer drawn;
    private Integer lost;
    private Integer points;
    private Integer scoreFor;
    private Integer scoreAgainst;
    private Integer scoreDifference;
}
