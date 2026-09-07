package iuh.fit.clubservice.presentation.response;

import iuh.fit.clubservice.domain.enums.LineupRole;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentRegistrationResponse {
    private UUID registrationId;
    private UUID tournamentId;
    private UUID playerId;
    private String playerName;
    private String playerPhone;
    private UUID clubId;
    private String clubName;
    private String teamName;
    private RegistrationType type;
    private RegistrationStatus status;
    private String athleteIdCode;
    private String idNumber;
    private String skillLevel;
    private UUID paymentId;
    private LocalDateTime registeredAt;
    private List<LineupResponseItem> lineups;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineupResponseItem {
        private UUID lineupId;
        private UUID playerId;
        private String playerName;
        private String playerPhone;
        private LineupRole lineupRole;
        private Boolean isStarter;
    }
}
