package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.response.ClubRecentMatchResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubTournamentResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.ClubMatchService;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.MatchStatus;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.service.ChampionPolicy;
import iuh.fit.clubservice.domain.service.StandingsCalculator;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
        return collectClubMatches(clubId, null).stream().limit(safeLimit).toList();
    }

    @Override
    public Page<ClubRecentMatchResponseCommand> getClubMatchesPage(UUID clubId, UUID tournamentId, Pageable pageable)
            throws ServiceException {
        List<ClubRecentMatchResponseCommand> matches = collectClubMatches(clubId, tournamentId);
        int start = Math.toIntExact(Math.min(pageable.getOffset(), matches.size()));
        int end = Math.min(start + pageable.getPageSize(), matches.size());
        return new PageImpl<>(matches.subList(start, end), pageable, matches.size());
    }

    /**
     * Mọi giải câu lạc bộ đã tham gia (đội CLB được xác nhận), mới nhất trước, kèm thắng/hòa/thua và hạng của đội CLB.
     * Giải chưa đá trận nào vẫn hiện (trước đây tab chỉ dựng từ trận gần nhất nên chỉ thấy đúng một giải).
     */
    @Override
    public List<ClubTournamentResponseCommand> getClubTournaments(UUID clubId) throws ServiceException {
        support.requireClub(clubId);
        LocalDate today = LocalDate.now();
        List<ClubTournamentResponseCommand> result = new ArrayList<>();
        for (TournamentRegistration registration : registrationRepository.findByClubId(clubId)) {
            if (registration.getStatus() != RegistrationStatus.CONFIRMED) continue;
            Tournament tournament = tournamentRepository.findById(registration.getTournamentId()).orElse(null);
            if (tournament == null) continue;
            List<TournamentFixture> fixtures = fixtureRepository.findByTournamentId(tournament.getTournamentId());
            List<UUID> teams = registrationRepository.findByTournamentIdAndStatus(
                            tournament.getTournamentId(), RegistrationStatus.CONFIRMED)
                    .stream().map(TournamentRegistration::getRegistrationId).toList();
            TournamentStatus status = TournamentLifecyclePolicy.effectiveStatus(tournament, today);
            int won = 0, drawn = 0, lost = 0;
            for (TournamentFixture fixture : fixtures) {
                Integer comparison = clubComparison(fixture, registration.getRegistrationId());
                if (comparison == null) continue;
                if (comparison > 0) won++;
                else if (comparison < 0) lost++;
                else drawn++;
            }
            boolean hasResults = won + drawn + lost > 0;
            Integer rank = !hasResults || tournament.getFormat() != TournamentFormat.ROUND_ROBIN ? null
                    : StandingsCalculator.calculate(teams, fixtures).stream()
                            .filter(row -> row.registrationId().equals(registration.getRegistrationId()))
                            .map(StandingsCalculator.Standing::rank).findFirst().orElse(null);
            boolean champion = status == TournamentStatus.COMPLETED
                    && ChampionPolicy.champion(tournament.getFormat(), teams, fixtures)
                            .map(registration.getRegistrationId()::equals).orElse(false);
            result.add(ClubTournamentResponseCommand.builder()
                    .tournamentId(tournament.getTournamentId()).name(tournament.getName())
                    .sportType(tournament.getSportType()).format(tournament.getFormat()).status(status)
                    .startDate(tournament.getStartDate()).endDate(tournament.getEndDate())
                    .registrationId(registration.getRegistrationId()).teamName(registration.getTeamName())
                    .teamCount(teams.size()).played(won + drawn + lost).won(won).drawn(drawn).lost(lost)
                    .rank(rank).champion(champion).build());
        }
        result.sort(Comparator.comparing(ClubTournamentResponseCommand::getStartDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /**
     * Ghép lịch sử trận từ các giải CLB đã tham gia, chỉ lấy trận đã có kết quả, mới nhất trước.
     * ponytail: phân trang trong bộ nhớ, đủ cho số trận của một CLB; chuyển sang truy vấn SQL nếu lịch sử lên hàng nghìn trận.
     */
    private List<ClubRecentMatchResponseCommand> collectClubMatches(UUID clubId, UUID tournamentId) throws ServiceException {
        support.requireClub(clubId);
        List<ClubRecentMatchResponseCommand> matches = new ArrayList<>();
        for (TournamentRegistration clubRegistration : registrationRepository.findByClubId(clubId)) {
            if (tournamentId != null && !tournamentId.equals(clubRegistration.getTournamentId())) continue;
            for (TournamentFixture fixture : fixtureRepository.findByTournamentId(clubRegistration.getTournamentId())) {
                if (clubComparison(fixture, clubRegistration.getRegistrationId()) == null) continue;
                matches.add(toMatch(fixture, clubRegistration.getRegistrationId().equals(fixture.getRegistration1Id())));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparing(ClubRecentMatchResponseCommand::getPlayedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** So sánh tỉ số theo góc nhìn đội CLB; null nếu trận chưa có kết quả hoặc không có đội CLB. */
    private static Integer clubComparison(TournamentFixture fixture, UUID registrationId) {
        if (fixture.getStatus() != MatchStatus.COMPLETED || fixture.getScore1() == null || fixture.getScore2() == null) {
            return null;
        }
        if (Objects.equals(registrationId, fixture.getRegistration1Id())) {
            return Integer.compare(fixture.getScore1(), fixture.getScore2());
        }
        if (Objects.equals(registrationId, fixture.getRegistration2Id())) {
            return Integer.compare(fixture.getScore2(), fixture.getScore1());
        }
        return null;
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
        // Trận được tạo cùng lúc khi xếp lịch; lần cập nhật cuối là lúc nhập tỉ số nên gần ngày đá hơn.
        LocalDateTime playedAt = fixture.getUpdatedAt() != null ? fixture.getUpdatedAt() : fixture.getCreatedAt();
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
