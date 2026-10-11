package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.AdminCommunityService;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCommunityServiceImpl implements AdminCommunityService {
    private static final int MAX_PAGE_SIZE = 50;

    private final ClubRepository clubRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ClubRow> getClubs(ClubState state, SportType sportType, String keyword, int page, int size) {
        Set<SportType> sportTypes = sportType == null ? EnumSet.allOf(SportType.class) : EnumSet.of(sportType);
        return clubRepository.searchForAdmin(state != ClubState.DISBANDED, state != ClubState.ACTIVE, sportTypes,
                keyword, PageRequest.of(Math.max(page, 0), pageSize(size)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TournamentRow> getTournaments(TournamentStatus status, SportType sportType, String keyword,
                                              int page, int size) {
        LocalDate today = LocalDate.now();
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<Tournament> tournaments = tournamentRepository.searchForAdmin(status, sportType, normalized, today,
                PageRequest.of(Math.max(page, 0), pageSize(size),
                        Sort.by(Sort.Order.desc("startDate"), Sort.Order.asc("tournamentId"))));
        // Một truy vấn gộp cho cả trang thay vì đếm đăng ký từng giải.
        Map<UUID, Long> seats = registrationRepository.countByTournamentIdsAndStatuses(
                tournaments.map(Tournament::getTournamentId).getContent(), TournamentLifecyclePolicy.HOLDING_SEAT);
        return tournaments.map(tournament -> new TournamentRow(
                tournament.getTournamentId(), tournament.getName(), tournament.getSportType(), tournament.getFormat(),
                TournamentLifecyclePolicy.effectiveStatus(tournament, today), tournament.getOrganizerId(),
                tournament.getVenueId(), tournament.getMaxParticipants(),
                seats.getOrDefault(tournament.getTournamentId(), 0L), tournament.getEntryFee(),
                tournament.getPrizePool(), tournament.getRegistrationOpenDate(), tournament.getRegistrationCloseDate(),
                tournament.getStartDate(), tournament.getEndDate(), tournament.getCreatedAt()));
    }

    private static int pageSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }
}
