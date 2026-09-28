package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.UpdateFixtureResultCommand;
import iuh.fit.clubservice.application.dto.response.TournamentFixtureResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentStandingResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.TournamentFixtureService;
import iuh.fit.clubservice.application.service.TournamentRatingService;
import iuh.fit.clubservice.application.service.TournamentRegistrationService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.MatchStatus;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.service.BracketPolicy;
import iuh.fit.clubservice.domain.service.ClubRecordPolicy;
import iuh.fit.clubservice.domain.service.StandingsCalculator;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TournamentFixtureServiceImpl implements TournamentFixtureService {
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentFixtureRepository fixtureRepository;
    private final ClubRepository clubRepository;
    private final TournamentRegistrationService registrationService;
    private final TournamentRatingService ratingService;

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public List<TournamentFixtureResponseCommand> generateFixtures(UUID tournamentId, UUID actorUserId) throws ServiceException {
        // Khóa hàng giải để đăng ký đang chạy song song không lọt vào sau khi danh sách đã chốt.
        Tournament tournament = tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
        TournamentGuards.assertOrganizer(tournament, actorUserId);
        TournamentStatus status = TournamentLifecyclePolicy.effectiveStatus(tournament, LocalDate.now());
        if (status == TournamentStatus.DRAFT) throw new ServiceException("Hãy công bố giải trước khi xếp lịch.");
        if (status == TournamentStatus.COMPLETED || status == TournamentStatus.CANCELLED) {
            throw new ServiceException("Giải đấu đã kết thúc hoặc đã hủy.");
        }
        // Xếp lại sẽ xóa kết quả đã nhập và làm lệch thành tích CLB đã cộng.
        if (fixtureRepository.findByTournamentId(tournamentId).stream().anyMatch(BracketPolicy::hasPlayedResult)) {
            throw new ServiceException("Đã có trận có kết quả, không thể xếp lại lịch thi đấu.");
        }
        // Sắp theo mã đăng ký để lịch không phụ thuộc thứ tự DB trả về.
        List<UUID> teams = registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED)
                .stream().map(TournamentRegistration::getRegistrationId)
                .sorted(Comparator.comparing(UUID::toString)).toList();
        if (teams.size() < 2) throw new ServiceException("Cần ít nhất 2 đăng ký đã xác nhận để tạo lịch đấu.");
        // Bracket chốt từ danh sách CONFIRMED lúc này; đội chưa đủ người / chưa đóng phí không còn chỗ nữa.
        registrationService.closeUnfinishedBeforeFixtures(tournament, actorUserId);
        fixtureRepository.deleteByTournamentId(tournamentId);

        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        tournamentRepository.save(tournament);

        if (tournament.getFormat() == TournamentFormat.ROUND_ROBIN) return generateRoundRobin(tournamentId, teams);
        return generateEliminationBracket(tournamentId, BracketPolicy.seed(teams, tournamentId));
    }

    @Override
    public List<TournamentFixtureResponseCommand> getFixtures(UUID tournamentId) {
        return fixtureRepository.findByTournamentId(tournamentId).stream().map(TournamentFixtureServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentFixtureResponseCommand updateFixtureResult(UpdateFixtureResultCommand command) throws ServiceException {
        command.validate();
        TournamentFixture fixture = fixtureRepository.findById(command.getFixtureId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy trận đấu."));
        Tournament tournament = tournamentRepository.findById(fixture.getTournamentId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
        TournamentGuards.assertOrganizer(tournament, command.getActorUserId());
        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            throw new ServiceException("Chỉ cập nhật tỷ số khi giải đấu đang diễn ra.");
        }
        if (fixture.getRegistration2Id() == null) throw new ServiceException("Trận đấu được miễn thi đấu không cần cập nhật tỷ số.");
        if (fixture.getRegistration1Id() == null) throw new ServiceException("Trận đấu chưa đủ hai đội.");
        // Trước đây chỉ chặn hòa khi có trận kế tiếp, nên chung kết hòa lọt qua và giải không có nhà vô địch.
        if (tournament.getFormat() == TournamentFormat.SINGLE_ELIMINATION
                && command.getScore1().equals(command.getScore2())) {
            throw new ServiceException("Vòng loại trực tiếp không chấp nhận tỷ số hòa.");
        }

        UUID previousWinner = fixture.getWinnerRegistrationId();
        boolean wasCompleted = fixture.getStatus() == MatchStatus.COMPLETED;
        Integer previousScore1 = fixture.getScore1();
        Integer previousScore2 = fixture.getScore2();
        fixture.setScore1(command.getScore1());
        fixture.setScore2(command.getScore2());
        fixture.setWinnerRegistrationId(command.getScore1().equals(command.getScore2()) ? null
                : command.getScore1() > command.getScore2() ? fixture.getRegistration1Id() : fixture.getRegistration2Id());
        fixture.setStatus(MatchStatus.COMPLETED);

        if (fixture.getNextFixtureId() != null) {
            TournamentFixture next = fixtureRepository.findById(fixture.getNextFixtureId())
                    .orElseThrow(() -> new ServiceException("Không tìm thấy trận kế tiếp."));
            // Đổi đội thắng khi vòng sau đã đá xong sẽ làm sai cả nhánh còn lại.
            if (next.getStatus() == MatchStatus.COMPLETED
                    && !Objects.equals(previousWinner, fixture.getWinnerRegistrationId())) {
                throw new ServiceException("Trận kế tiếp đã có kết quả, không thể đổi đội thắng trận này.");
            }
            BracketPolicy.placeWinner(next, fixture.getMatchNumber(), fixture.getWinnerRegistrationId());
            fixtureRepository.save(next);
        }
        if (wasCompleted) applyClubRecords(fixture, previousScore1, previousScore2, -1);
        applyClubRecords(fixture, fixture.getScore1(), fixture.getScore2(), 1);
        fixture.setRatingSynced(false);
        TournamentFixture saved = fixtureRepository.save(fixture);
        rateAfterCommit(saved.getFixtureId());
        return toResponse(saved);
    }

    /** ELO tính sau khi tỉ số đã lưu chắc chắn; lỗi thì cờ ratingSynced=false để job thử lại. */
    private void rateAfterCommit(UUID fixtureId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ratingService.syncFixture(fixtureId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    ratingService.syncFixture(fixtureId);
                } catch (RuntimeException exception) {
                    // Tỉ số đã lưu; job TournamentRatingJob sẽ tính lại.
                }
            }
        });
    }

    @Override
    public List<TournamentStandingResponseCommand> getStandings(UUID tournamentId) {
        List<UUID> teams = registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED)
                .stream().map(TournamentRegistration::getRegistrationId).toList();
        return StandingsCalculator.calculate(teams, fixtureRepository.findByTournamentId(tournamentId)).stream()
                .map(s -> TournamentStandingResponseCommand.builder().tournamentId(tournamentId)
                        .registrationId(s.registrationId()).played(s.played()).won(s.won()).drawn(s.drawn())
                        .lost(s.lost()).scoreFor(s.scoreFor()).scoreAgainst(s.scoreAgainst()).points(s.points())
                        .rank(s.rank()).build())
                .toList();
    }

    // ---------------------------------------------------------------- sinh lịch

    private List<TournamentFixtureResponseCommand> generateRoundRobin(UUID tournamentId, List<UUID> teams) {
        List<TournamentFixture> fixtures = new ArrayList<>();
        int match = 1;
        for (int i = 0; i < teams.size(); i++) {
            for (int j = i + 1; j < teams.size(); j++) {
                fixtures.add(TournamentFixture.builder().tournamentId(tournamentId).roundName("Vòng bảng")
                        .roundNumber(1).matchNumber(match++).registration1Id(teams.get(i)).registration2Id(teams.get(j))
                        .status(MatchStatus.SCHEDULED).build());
            }
        }
        return fixtureRepository.saveAll(fixtures).stream().map(TournamentFixtureServiceImpl::toResponse).toList();
    }

    /**
     * Sinh cây loại trực tiếp đầy đủ: mỗi trận đều biết trận kế tiếp, bye được đẩy lên ngay.
     * Sinh ngược từ chung kết về vòng 1 vì trận vòng trước cần id (do DB sinh) của trận kế tiếp.
     */
    private List<TournamentFixtureResponseCommand> generateEliminationBracket(UUID tournamentId, List<UUID> teams) {
        int bracketSize = BracketPolicy.bracketSize(teams.size());
        int totalRounds = Integer.numberOfTrailingZeros(bracketSize);

        List<TournamentFixture> nextRound = null;
        List<List<TournamentFixture>> allRounds = new ArrayList<>();
        for (int round = totalRounds; round >= 1; round--) {
            int matches = bracketSize >> round;
            List<TournamentFixture> current = new ArrayList<>();
            for (int index = 0; index < matches; index++) {
                var builder = TournamentFixture.builder()
                        .tournamentId(tournamentId)
                        .roundName(BracketPolicy.roundName(round, totalRounds))
                        .roundNumber(round)
                        .matchNumber(index + 1)
                        .nextFixtureId(nextRound == null ? null : nextRound.get(index / 2).getFixtureId())
                        .status(MatchStatus.SCHEDULED);
                if (round == 1) {
                    builder.registration1Id(seatAt(teams, index * 2)).registration2Id(seatAt(teams, index * 2 + 1));
                }
                current.add(builder.build());
            }
            current = fixtureRepository.saveAll(current);
            allRounds.add(0, current);
            nextRound = current;
        }

        if (totalRounds > 1) advanceByes(allRounds.get(0), allRounds.get(1));
        return allRounds.stream().flatMap(List::stream).map(TournamentFixtureServiceImpl::toResponse).toList();
    }

    /** Trận chỉ có một đội (bye) được xử thắng ngay để vòng sau không kẹt vĩnh viễn. */
    private void advanceByes(List<TournamentFixture> firstRound, List<TournamentFixture> secondRound) {
        Map<UUID, TournamentFixture> next = new LinkedHashMap<>();
        secondRound.forEach(item -> next.put(item.getFixtureId(), item));
        List<TournamentFixture> touched = new ArrayList<>();
        for (TournamentFixture fixture : firstRound) {
            if (fixture.getRegistration1Id() == null || fixture.getRegistration2Id() != null) continue;
            fixture.setWinnerRegistrationId(fixture.getRegistration1Id());
            fixture.setStatus(MatchStatus.COMPLETED);
            touched.add(fixture);
            TournamentFixture target = next.get(fixture.getNextFixtureId());
            if (target != null) BracketPolicy.placeWinner(target, fixture.getMatchNumber(), fixture.getRegistration1Id());
        }
        if (touched.isEmpty()) return;
        fixtureRepository.saveAll(touched);
        fixtureRepository.saveAll(new ArrayList<>(next.values()));
    }

    private static UUID seatAt(List<UUID> teams, int index) {
        return index < teams.size() ? teams.get(index) : null;
    }

    // ---------------------------------------------------------------- thành tích CLB

    private void applyClubRecords(TournamentFixture fixture, Integer score1, Integer score2, int sign) {
        if (score1 == null || score2 == null) return;
        int comparison = Integer.compare(score1, score2);
        applyClubRecord(clubOf(fixture.getRegistration1Id()), comparison, sign);
        applyClubRecord(clubOf(fixture.getRegistration2Id()), -comparison, sign);
    }

    private UUID clubOf(UUID registrationId) {
        if (registrationId == null) return null;
        return registrationRepository.findById(registrationId).map(TournamentRegistration::getClubId).orElse(null);
    }

    private void applyClubRecord(UUID clubId, int comparison, int sign) {
        if (clubId == null) return;
        clubRepository.findById(clubId).ifPresent(club -> {
            ClubRecordPolicy.apply(club, comparison, sign);
            clubRepository.save(club);
        });
    }

    private static TournamentFixtureResponseCommand toResponse(TournamentFixture f) {
        return TournamentFixtureResponseCommand.builder().fixtureId(f.getFixtureId()).tournamentId(f.getTournamentId())
                .roundName(f.getRoundName()).roundNumber(f.getRoundNumber()).matchNumber(f.getMatchNumber())
                .registration1Id(f.getRegistration1Id()).registration2Id(f.getRegistration2Id())
                .score1(f.getScore1()).score2(f.getScore2()).winnerRegistrationId(f.getWinnerRegistrationId())
                .nextFixtureId(f.getNextFixtureId()).reservationId(f.getReservationId()).status(f.getStatus()).build();
    }
}
