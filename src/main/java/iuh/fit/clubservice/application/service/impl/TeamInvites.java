package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand.LineupItem;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.LineupRole;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Quy tắc thuần cho danh sách người được mời vào đội: không truy cập dữ liệu, dễ test riêng. */
final class TeamInvites {

    private TeamInvites() {
    }

    /**
     * Kiểm tra một lượt mời: không vượt số chỗ còn trống, không tự mời mình, không mời trùng người,
     * số áo 0–999 và không trùng với số áo đã có trong đội.
     */
    static void assertValid(List<LineupItem> invitees, UUID captain, Integer captainShirt, Set<Integer> usedShirts,
                            int slotsLeft) throws ServiceException {
        if (invitees.size() > slotsLeft) {
            throw new ServiceException(slotsLeft <= 0 ? "Đội đã đủ số người tối đa."
                    : "Chỉ mời thêm được tối đa " + slotsLeft + " người.");
        }
        Set<UUID> players = new HashSet<>();
        Set<Integer> shirts = new HashSet<>(usedShirts);
        if (captainShirt != null) shirts.add(captainShirt);
        for (LineupItem invitee : invitees) {
            if (invitee.getPlayerId() == null) throw new ServiceException("Mỗi lời mời phải chọn một người chơi.");
            if (invitee.getPlayerId().equals(captain)) throw new ServiceException("Bạn đã là đội trưởng, không cần tự mời.");
            if (!players.add(invitee.getPlayerId())) throw new ServiceException(label(invitee) + " bị mời hai lần.");
            if (invitee.getShirtNumber() != null) {
                if (invitee.getShirtNumber() < 0 || invitee.getShirtNumber() > 999) {
                    throw new ServiceException("Số áo từ 0 đến 999.");
                }
                if (!shirts.add(invitee.getShirtNumber())) {
                    throw new ServiceException("Số áo " + invitee.getShirtNumber() + " bị trùng trong đội.");
                }
            }
        }
    }

    static TournamentLineup newRow(UUID registrationId, LineupItem invitee) {
        return TournamentLineup.builder().registrationId(registrationId).playerId(invitee.getPlayerId())
                .playerName(TournamentRegistrationSupport.normalize(invitee.getPlayerName())).lineupRole(role(invitee))
                .shirtNumber(invitee.getShirtNumber()).memberStatus(LineupMemberStatus.INVITED).build();
    }

    /** Đội trưởng là người lập đội; người được mời chỉ là thi đấu hoặc dự bị. */
    static LineupRole role(LineupItem invitee) {
        return invitee.getLineupRole() == LineupRole.SUBSTITUTE ? LineupRole.SUBSTITUTE : LineupRole.PLAYER;
    }

    static String label(LineupItem invitee) {
        String name = TournamentRegistrationSupport.normalize(invitee.getPlayerName());
        return name == null ? "Người được mời" : name;
    }
}
