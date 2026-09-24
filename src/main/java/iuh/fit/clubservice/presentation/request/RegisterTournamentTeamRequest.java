package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 30, message = "Mời tối đa 30 người")
    @Valid
    private List<LineupItemRequest> lineups;
    @Min(0)
    @Max(999)
    private Integer captainShirtNumber;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LineupItemRequest {
        @NotNull(message = "Chọn người chơi")
        private UUID playerId;
        @Size(max = 120) private String playerName;
        private LineupRole lineupRole;
        private Integer shirtNumber;
    }
}
