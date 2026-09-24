package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.response.ClubRecentMatchResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/** Lịch sử trận đấu của CLB, dựng từ các trận giải đấu đã có kết quả mà đội của CLB tham gia. */
public interface ClubMatchService {

    List<ClubRecentMatchResponseCommand> getClubRecentMatches(UUID clubId, int limit) throws ServiceException;

    Page<ClubRecentMatchResponseCommand> getClubMatchesPage(UUID clubId, Pageable pageable) throws ServiceException;
}
