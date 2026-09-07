package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TournamentRegistrationResponseCommand {
    private UUID registrationId;
    private UUID tournamentId;
    private RegistrationType type;
    private UUID playerId;
    private UUID clubId;
    private String teamName;
    private UUID registeredBy;
    private SkillLevel skillLevel;
    private RegistrationStatus status;
    private String statusReason;
    private LocalDateTime registeredAt;
    private LocalDateTime confirmedAt;
    private List<LineupResponseItem> lineups;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LineupResponseItem {
        private UUID lineupId;
        private UUID playerId;
        private String playerName;
        private LineupRole lineupRole;
        private Integer shirtNumber;
    }
}
