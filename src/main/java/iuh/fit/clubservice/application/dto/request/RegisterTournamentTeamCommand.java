package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.application.exception.ServiceException;
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

    public void validate(RegistrationType registrationType) throws ServiceException {
        if (registrationType == RegistrationType.CLUB && clubId == null) {
            throw new ServiceException("Đăng ký theo câu lạc bộ cần clubId.");
        }
        if (registrationType == RegistrationType.INDIVIDUAL
                && playerId != null && !playerId.equals(registeredBy)) {
            throw new ServiceException("Người chơi chỉ có thể tự đăng ký cá nhân.");
        }
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LineupItem {
        private UUID playerId;
        private String playerName;
        private LineupRole lineupRole;
        private Integer shirtNumber;
    }
}
