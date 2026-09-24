package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.LineupRole;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import lombok.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterTournamentTeamCommand {
    private UUID tournamentId;
    private UUID registeredBy;
    private UUID playerId;
    private UUID clubId;
    private String teamName;
    private RegistrationType type;
    private SkillLevel skillLevel;
    private List<LineupItem> lineups;
    private Integer captainShirtNumber;

    public void validate(RegistrationType registrationType) throws ServiceException {
        if (registrationType == RegistrationType.CLUB && clubId == null) {
            throw new ServiceException("Đăng ký theo câu lạc bộ cần clubId.");
        }
        if (registrationType == RegistrationType.INDIVIDUAL
                && playerId != null && !playerId.equals(registeredBy)) {
            throw new ServiceException("Người chơi chỉ có thể tự đăng ký cá nhân.");
        }
        validateLineups();
    }

    private void validateLineups() throws ServiceException {
        if (lineups == null || lineups.isEmpty()) return;

        Set<UUID> players = new HashSet<>();
        Set<Integer> shirtNumbers = new HashSet<>();
        int captains = 0;
        for (LineupItem item : lineups) {
            if (item.getPlayerId() == null) {
                throw new ServiceException("Mỗi vị trí trong đội hình phải có người chơi.");
            }
            if (!players.add(item.getPlayerId())) {
                throw new ServiceException("Một người chơi không thể xuất hiện hai lần trong đội hình.");
            }
            if (item.getShirtNumber() != null && !shirtNumbers.add(item.getShirtNumber())) {
                throw new ServiceException("Số áo " + item.getShirtNumber() + " bị trùng trong đội hình.");
            }
            if (item.getLineupRole() == LineupRole.CAPTAIN) captains++;
        }
        if (captains > 1) throw new ServiceException("Đội hình chỉ được có một đội trưởng.");
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineupItem {
        private UUID playerId;
        private String playerName;
        private LineupRole lineupRole;
        private Integer shirtNumber;
    }
}
