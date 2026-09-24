package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.response.ClubRecentMatchResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.ClubMatchService;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.MatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClubMatchServiceImpl implements ClubMatchService {
    private final ClubSupport support;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentFixtureRepository fixtureRepository;
    private final TournamentRepository tournamentRepository;

    @Override
    public List<ClubRecentMatchResponseCommand> getClubRecentMatches(UUID clubId, int limit) throws ServiceException {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return collectClubMatches(clubId).stream().limit(safeLimit).toList();
    }

    @Override
    public Page<ClubRecentMatchResponseCommand> getClubMatchesPage(UUID clubId, Pageable pageable) throws ServiceException {
        List<ClubRecentMatchResponseCommand> matches = collectClubMatches(clubId);
        int start = Math.toIntExact(Math.min(pageable.getOffset(), matches.size()));
        int end = Math.min(start + pageable.getPageSize(), matches.size());
        return new PageImpl<>(matches.subList(start, end), pageable, matches.size());
    }

    /**
     * Ghép lịch sử trận từ các giải CLB đã tham gia, chỉ lấy trận đã có kết quả, mới nhất trước.
     * ponytail: phân trang trong bộ nhớ, đủ cho số trận của một CLB; chuyển sang truy vấn SQL nếu lịch sử lên hàng nghìn trận.
     */
    private List<ClubRecentMatchResponseCommand> collectClubMatches(UUID clubId) throws ServiceException {
        support.requireClub(clubId);
        List<ClubRecentMatchResponseCommand> matches = new ArrayList<>();
        for (TournamentRegistration clubRegistration : registrationRepository.findByClubId(clubId)) {
            for (TournamentFixture fixture : fixtureRepository.findByTournamentId(clubRegistration.getTournamentId())) {
                if (fixture.getStatus() != MatchStatus.COMPLETED
                        || fixture.getScore1() == null || fixture.getScore2() == null) continue;
                boolean clubIsFirst = clubRegistration.getRegistrationId().equals(fixture.getRegistration1Id());
                boolean clubIsSecond = clubRegistration.getRegistrationId().equals(fixture.getRegistration2Id());
                if (!clubIsFirst && !clubIsSecond) continue;
                matches.add(toMatch(fixture, clubIsFirst));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparing(ClubRecentMatchResponseCommand::getPlayedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private ClubRecentMatchResponseCommand toMatch(TournamentFixture fixture, boolean clubIsFirst) {
        UUID opponentRegistrationId = clubIsFirst ? fixture.getRegistration2Id() : fixture.getRegistration1Id();
        String opponentName = opponentRegistrationId == null ? "Đối thủ chờ xác định"
                : registrationRepository.findById(opponentRegistrationId)
                .map(TournamentRegistration::getTeamName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("Đội bạn");
        int clubScore = clubIsFirst ? fixture.getScore1() : fixture.getScore2();
        int opponentScore = clubIsFirst ? fixture.getScore2() : fixture.getScore1();
        String result = clubScore > opponentScore ? "WIN" : clubScore < opponentScore ? "LOSS" : "DRAW";
        LocalDateTime playedAt = fixture.getCreatedAt() != null ? fixture.getCreatedAt() : fixture.getUpdatedAt();
        String tournamentName = tournamentRepository.findById(fixture.getTournamentId())
                .map(Tournament::getName).orElse("Giải đấu GOAT Sports");
        return ClubRecentMatchResponseCommand.builder()
                .matchId(fixture.getFixtureId())
                .tournamentId(fixture.getTournamentId())
                .tournamentName(tournamentName)
                .opponentName(opponentName)
                .playedAt(playedAt)
                .clubScore(clubScore)
                .opponentScore(opponentScore)
                .result(result)
                .build();
    }
}
