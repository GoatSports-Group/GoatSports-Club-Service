package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.response.ClubRecentMatchResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubTournamentResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/** Lịch sử thi đấu của CLB, dựng từ các giải mà đội đại diện CLB tham gia. */
public interface ClubMatchService {
    List<ClubRecentMatchResponseCommand> getClubRecentMatches(UUID clubId, int limit) throws ServiceException;

    Page<ClubRecentMatchResponseCommand> getClubMatchesPage(UUID clubId, UUID tournamentId, Pageable pageable)
            throws ServiceException;

    List<ClubTournamentResponseCommand> getClubTournaments(UUID clubId) throws ServiceException;

    /**
     * Chu CLB hoac quan tri vien khoe thanh tich cua CLB o mot giai len Cong dong, gan the doi da thi dau
     * va cac thanh vien. Tra ve id bai viet.
     */
    UUID shareTournament(UUID clubId, UUID tournamentId, UUID actorId, String title, String content)
            throws ServiceException;
}
