package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.external.SportRatingPort;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.TournamentRatingService;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.MatchStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentRatingServiceImpl implements TournamentRatingService {
    static final int JOB_BATCH = 50;

    private final TournamentFixtureRepository fixtureRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentLineupRepository lineupRepository;
    private final PlayerProfileProvider playerProfileProvider;
    private final SportRatingPort sportRatingPort;
    private final TournamentEventPublisher eventPublisher;

    /** REQUIRES_NEW: được gọi cả từ afterCommit của lần lưu tỉ số, lúc đó transaction cũ đã đóng. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean syncFixture(UUID fixtureId) {
        TournamentFixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null || !Boolean.FALSE.equals(fixture.getRatingSynced())) return true;
        Tournament tournament = tournamentRepository.findById(fixture.getTournamentId()).orElse(null);
        Participants first = participants(fixture.getRegistration1Id());
        Participants second = participants(fixture.getRegistration2Id());
        // Giải đã hủy, trận chưa đủ hai bên hay đội không còn ai: không có gì để tính.
        if (tournament == null || tournament.getStatus() == TournamentStatus.CANCELLED
                || fixture.getStatus() != MatchStatus.COMPLETED || fixture.getScore1() == null || fixture.getScore2() == null
                || first.playerIds().isEmpty() || second.playerIds().isEmpty()) {
            fixtureRepository.markRatingSynced(fixtureId, fixture.getScore1(), fixture.getScore2());
            return true;
        }

        String title = fixture.getRoundName() + " · Trận " + fixture.getMatchNumber() + " · " + tournament.getName();
        List<SportRatingPort.RatedPlayer> rated;
        try {
            rated = sportRatingPort.rateTournamentMatch(new SportRatingPort.TournamentMatch(
                    fixtureId, tournament.getTournamentId(), tournament.getSportType(), title,
                    new SportRatingPort.Side(first.name(), fixture.getScore1(), first.playerIds()),
                    new SportRatingPort.Side(second.name(), fixture.getScore2(), second.playerIds())));
        } catch (ServiceException exception) {
            log.warn("Trận {} chưa tính được ELO, sẽ thử lại: {}", fixtureId, exception.getMessage());
            return false;
        }
        // Tỉ số đã bị sửa trong lúc tính: giữ cờ chờ, lần đồng bộ sau (do lần sửa đó kích hoạt) tính theo tỉ số mới.
        if (!fixtureRepository.markRatingSynced(fixtureId, fixture.getScore1(), fixture.getScore2())) return false;

        Map<UUID, String[]> perspective = new HashMap<>();
        first.playerIds().forEach(id -> perspective.put(id, new String[]{
                fixture.getScore1() + "–" + fixture.getScore2(), second.name()}));
        second.playerIds().forEach(id -> perspective.put(id, new String[]{
                fixture.getScore2() + "–" + fixture.getScore1(), first.name()}));
        for (SportRatingPort.RatedPlayer player : rated) {
            if (!player.changed()) continue;
            String[] view = perspective.getOrDefault(player.userId(), new String[]{"", ""});
            eventPublisher.publish(TournamentEventPublisher.MATCH_RATED, tournament, player.userId(), null, Map.of(
                    "fixtureId", fixtureId.toString(),
                    "matchTitle", title,
                    "outcome", player.outcome(),
                    "score", view[0],
                    "opponentName", view[1],
                    "oldElo", String.valueOf(player.oldElo()),
                    "newElo", String.valueOf(player.newElo()),
                    "delta", String.valueOf(player.delta())));
        }
        return true;
    }

    @Override
    public List<UUID> findPendingFixtureIds() {
        return fixtureRepository.findRatingPending(JOB_BATCH).stream().map(TournamentFixture::getFixtureId).toList();
    }

    /** Người thi đấu của một bên: đăng ký cá nhân là chính người đó, đội là các thành viên đã nhận lời. */
    private Participants participants(UUID registrationId) {
        if (registrationId == null) return new Participants("", List.of());
        TournamentRegistration registration = registrationRepository.findById(registrationId).orElse(null);
        if (registration == null) return new Participants("", List.of());
        List<UUID> players = registration.getType() == RegistrationType.INDIVIDUAL
                ? (registration.getPlayerId() == null ? List.of() : List.of(registration.getPlayerId()))
                : lineupRepository.findByRegistrationId(registrationId).stream()
                        .filter(row -> row.getMemberStatus() == LineupMemberStatus.ACCEPTED)
                        .map(TournamentLineup::getPlayerId).distinct().toList();
        String name = registration.getTeamName();
        if ((name == null || name.isBlank()) && registration.getPlayerId() != null) {
            name = playerProfileProvider.findFullName(registration.getPlayerId()).orElse(null);
        }
        return new Participants(name == null || name.isBlank() ? "Đối thủ" : name, players);
    }

    private record Participants(String name, List<UUID> playerIds) { }
}
