package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.domain.enums.LineupRole;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RegisterTournamentTeamCommand {
    private UUID tournamentId;
    private UUID registeredBy;
    private UUID playerId;
    private UUID clubId;
    private String teamName;
    private RegistrationType type;
    private SkillLevel skillLevel;
    private List<LineupItem> lineups;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LineupItem {
        private UUID playerId;
        private String playerName;
        private LineupRole lineupRole;
        private Integer shirtNumber;
    }
}
