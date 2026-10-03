package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.SportType;

import java.util.List;
import java.util.UUID;

/** Dang bai "khoe" thanh tich giai dau len Cong dong (social-service), thay nguoi da duoc club-service xac nhan. */
public interface CommunityHighlightPublisher {
    /** @return id bai viet vua dang */
    UUID publishTournament(TournamentHighlight highlight) throws ServiceException;

    record TournamentHighlight(UUID authorId, SportType sport, String content, UUID tournamentId, String title,
                               String headline, String subtitle, String result, List<Stat> stats, UUID clubId,
                               String clubName, List<UUID> tagged) {
    }

    record Stat(String label, String value) {
    }
}
