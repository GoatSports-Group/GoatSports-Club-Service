package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.AdminStatsService;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {
    private final ClubRepository clubRepository;
    private final TournamentRepository tournamentRepository;

    @Override
    public CommunityStats getCommunityStats() {
        Map<TournamentStatus, Long> byStatus = new EnumMap<>(TournamentStatus.class);
        for (TournamentStatus status : TournamentStatus.values()) byStatus.put(status, 0L);
        // ponytail: đếm trong bộ nhớ vì trạng thái hiển thị phụ thuộc ngày; đủ cho vài nghìn giải,
        // chuyển sang câu SQL theo cùng luật (như searchOrganized) nếu số giải lớn hơn nhiều.
        List<Tournament> tournaments = tournamentRepository.findAll();
        LocalDate today = LocalDate.now();
        tournaments.forEach(tournament ->
                byStatus.merge(TournamentLifecyclePolicy.effectiveStatus(tournament, today), 1L, Long::sum));
        return new CommunityStats(clubRepository.countActive(), clubRepository.countDisbanded(),
                tournaments.size(), byStatus);
    }
}
