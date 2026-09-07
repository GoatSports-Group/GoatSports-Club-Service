package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.*;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RegisterTournamentTeamRequest {
    private UUID playerId;
    private UUID clubId;
    private String teamName;
    private RegistrationType type;
    private SkillLevel skillLevel;
    private List<LineupItemRequest> lineups;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LineupItemRequest {
        private UUID playerId;
        private String playerName;
        private LineupRole lineupRole;
        private Integer shirtNumber;
    }
}
