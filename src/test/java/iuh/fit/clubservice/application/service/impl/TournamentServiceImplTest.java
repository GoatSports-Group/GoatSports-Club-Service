package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand;
import iuh.fit.clubservice.application.dto.request.ReserveTournamentVenueCommand;
import iuh.fit.clubservice.application.dto.request.UpdateFixtureResultCommand;
import iuh.fit.clubservice.application.dto.response.TournamentFixtureResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentStandingResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.persistence.*;
import iuh.fit.clubservice.domain.entity.*;
import iuh.fit.clubservice.domain.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TournamentServiceImplTest {

    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentLineupRepository lineupRepository;
    @Mock private TournamentFixtureRepository fixtureRepository;
    @Mock private TournamentPaymentRepository paymentRepository;
    @Mock private TournamentEligibilityRuleRepository eligibilityRuleRepository;
    @Mock private PlayerProfileProvider playerProfileProvider;
    @Mock private TournamentVenueReservationRepository reservationRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private iuh.fit.clubservice.application.port.external.VenueSlotReservation venueSlotReservation;
    @Mock private iuh.fit.clubservice.application.port.external.ClubConversationSync conversationSync;

    private TournamentServiceImpl service;

    private final UUID tournamentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID organizerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TournamentServiceImpl(
                tournamentRepository, registrationRepository, lineupRepository, fixtureRepository,
                paymentRepository, eligibilityRuleRepository, playerProfileProvider,
                reservationRepository, clubRepository, venueSlotReservation, conversationSync);

        when(eligibilityRuleRepository.findByTournamentId(any())).thenReturn(List.of());
        when(tournamentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(fixtureRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(registrationRepository.save(any())).thenAnswer(call -> {
            TournamentRegistration registration = call.getArgument(0);
            if (registration.getRegistrationId() == null) registration.setRegistrationId(UUID.randomUUID());
            return registration;
        });
        // saveAll cap id nhu database that, vi bracket can id cua vong sau de noi nextFixtureId
        when(fixtureRepository.saveAll(any())).thenAnswer(call -> {
            List<TournamentFixture> fixtures = call.getArgument(0);
            fixtures.forEach(fixture -> {
                if (fixture.getFixtureId() == null) fixture.setFixtureId(UUID.randomUUID());
            });
            return fixtures;
        });
    }

    private Tournament tournament(TournamentStatus status, TournamentFormat format, int maxParticipants) {
        LocalDate today = LocalDate.now();
        return Tournament.builder()
                .tournamentId(tournamentId).organizerId(organizerId).name("Giải test")
                .sportType(SportType.FOOTBALL).format(format).status(status)
                .maxParticipants(maxParticipants).entryFee(0D).prizePool(0D)
                .registrationOpenDate(today.minusDays(1)).registrationCloseDate(today.plusDays(5))
                .startDate(today.plusDays(6)).endDate(today.plusDays(7))
                .rules(List.of()).build();
    }

    private TournamentRegistration registration(UUID id, UUID clubId) {
        return TournamentRegistration.builder()
                .registrationId(id).tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.INDIVIDUAL).status(RegistrationStatus.CONFIRMED)
                .clubId(clubId).build();
    }

    private List<TournamentRegistration> confirmedTeams(int count) {
        List<TournamentRegistration> teams = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            teams.add(registration(new UUID(0L, index + 1L), null));
        }
        return teams;
    }

    // ---------- TOUR-06: bang xep hang ----------

    @Test
    @DisplayName("đội nhiều điểm nhất phải đứng hạng 1")
    void standingsRankBestTeamFirst() {
        UUID strong = new UUID(0L, 1L);
        UUID weak = new UUID(0L, 2L);
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(registration(strong, null), registration(weak, null)));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(
                TournamentFixture.builder().fixtureId(UUID.randomUUID()).tournamentId(tournamentId)
                        .registration1Id(strong).registration2Id(weak).score1(5).score2(0)
                        .winnerRegistrationId(strong).status(MatchStatus.COMPLETED).build()));

        List<TournamentStandingResponseCommand> standings = service.getStandings(tournamentId);

        assertThat(standings).hasSize(2);
        assertThat(standings.get(0).getRegistrationId()).isEqualTo(strong);
        assertThat(standings.get(0).getRank()).isEqualTo(1);
        assertThat(standings.get(0).getPoints()).isEqualTo(3);
        assertThat(standings.get(1).getRegistrationId()).isEqualTo(weak);
        assertThat(standings.get(1).getPoints()).isZero();
    }

    @Test
    @DisplayName("bằng điểm thì xếp theo hiệu số bàn thắng")
    void standingsBreakTieByGoalDifference() {
        UUID better = new UUID(0L, 1L);
        UUID worse = new UUID(0L, 2L);
        UUID victim = new UUID(0L, 3L);
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(registration(better, null), registration(worse, null),
                        registration(victim, null)));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(
                TournamentFixture.builder().fixtureId(UUID.randomUUID()).tournamentId(tournamentId)
                        .registration1Id(better).registration2Id(victim).score1(5).score2(0)
                        .status(MatchStatus.COMPLETED).build(),
                TournamentFixture.builder().fixtureId(UUID.randomUUID()).tournamentId(tournamentId)
                        .registration1Id(worse).registration2Id(victim).score1(1).score2(0)
                        .status(MatchStatus.COMPLETED).build()));

        List<TournamentStandingResponseCommand> standings = service.getStandings(tournamentId);

        assertThat(standings.get(0).getRegistrationId()).isEqualTo(better);
        assertThat(standings.get(1).getRegistrationId()).isEqualTo(worse);
    }

    // ---------- TOUR-02: capacity va duplicate ----------

    @Test
    @DisplayName("đăng ký khi đã đủ chỗ bị từ chối")
    void registrationIsRejectedWhenFull() {
        when(tournamentRepository.findByIdForUpdate(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 2)));
        when(registrationRepository.findByTournamentId(tournamentId))
                .thenReturn(List.of(registration(UUID.randomUUID(), null),
                        registration(UUID.randomUUID(), null)));

        assertThatThrownBy(() -> service.registerTeam(RegisterTournamentTeamCommand.builder()
                .tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.INDIVIDUAL).build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đủ số lượng");

        verify(registrationRepository, never()).save(any());
    }

    @Test
    @DisplayName("đăng ký phải đi qua bản ghi giải đã khóa để chặn tranh chấp chỗ")
    void registrationLocksTournamentRow() throws ServiceException {
        when(tournamentRepository.findByIdForUpdate(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        service.registerTeam(RegisterTournamentTeamCommand.builder()
                .tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.INDIVIDUAL).build());

        verify(tournamentRepository).findByIdForUpdate(tournamentId);
    }

    @Test
    @DisplayName("một người không đăng ký hai lần")
    void duplicateRegistrationIsRejected() {
        UUID player = UUID.randomUUID();
        TournamentRegistration existing = registration(UUID.randomUUID(), null);
        existing.setRegisteredBy(player);
        when(tournamentRepository.findByIdForUpdate(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.registerTeam(RegisterTournamentTeamCommand.builder()
                .tournamentId(tournamentId).registeredBy(player)
                .type(RegistrationType.INDIVIDUAL).build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đã đăng ký");
    }

    @Test
    @DisplayName("đăng ký bị chặn khi không thỏa điều kiện tham dự")
    void registrationIsRejectedWhenIneligible() {
        when(tournamentRepository.findByIdForUpdate(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());
        when(eligibilityRuleRepository.findByTournamentId(tournamentId)).thenReturn(List.of(
                TournamentEligibilityRule.builder().ruleId(UUID.randomUUID()).tournamentId(tournamentId)
                        .ruleType(EligibilityRuleType.AGE)
                        .operator(EligibilityRuleOperator.GREATER_THAN_OR_EQUAL)
                        .expectedValue("18").build()));
        when(playerProfileProvider.findProfile(any(), any())).thenReturn(Optional.of(
                new PlayerProfileProvider.PlayerProfileView(UUID.randomUUID(),
                        LocalDate.now().minusYears(15), "MALE", SkillLevel.BEGINNER, 1000)));

        assertThatThrownBy(() -> service.registerTeam(RegisterTournamentTeamCommand.builder()
                .tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.INDIVIDUAL).build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Tuổi");

        verify(registrationRepository, never()).save(any());
    }

    @Test
    @DisplayName("một người không thể đứng hai vị trí trong đội hình")
    void duplicateAthleteInLineupIsRejected() {
        UUID player = UUID.randomUUID();
        when(tournamentRepository.findByIdForUpdate(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.registerTeam(RegisterTournamentTeamCommand.builder()
                .tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.CLUB).clubId(UUID.randomUUID()).teamName("Đội A")
                .lineups(List.of(
                        RegisterTournamentTeamCommand.LineupItem.builder()
                                .playerId(player).lineupRole(LineupRole.CAPTAIN).build(),
                        RegisterTournamentTeamCommand.LineupItem.builder()
                                .playerId(player).lineupRole(LineupRole.PLAYER).build()))
                .build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("hai lần trong đội hình");

        verify(registrationRepository, never()).save(any());
    }

    @Test
    @DisplayName("đội hình không được có hai đội trưởng")
    void twoCaptainsAreRejected() {
        when(tournamentRepository.findByIdForUpdate(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.registerTeam(RegisterTournamentTeamCommand.builder()
                .tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.CLUB).clubId(UUID.randomUUID()).teamName("Đội B")
                .lineups(List.of(
                        RegisterTournamentTeamCommand.LineupItem.builder()
                                .playerId(UUID.randomUUID()).lineupRole(LineupRole.CAPTAIN).build(),
                        RegisterTournamentTeamCommand.LineupItem.builder()
                                .playerId(UUID.randomUUID()).lineupRole(LineupRole.CAPTAIN).build()))
                .build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("một đội trưởng");
    }

    @Test
    @DisplayName("số áo trùng trong đội hình bị chặn")
    void duplicateShirtNumberIsRejected() {
        when(tournamentRepository.findByIdForUpdate(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.registerTeam(RegisterTournamentTeamCommand.builder()
                .tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.CLUB).clubId(UUID.randomUUID()).teamName("Đội C")
                .lineups(List.of(
                        RegisterTournamentTeamCommand.LineupItem.builder()
                                .playerId(UUID.randomUUID()).shirtNumber(10)
                                .lineupRole(LineupRole.PLAYER).build(),
                        RegisterTournamentTeamCommand.LineupItem.builder()
                                .playerId(UUID.randomUUID()).shirtNumber(10)
                                .lineupRole(LineupRole.PLAYER).build()))
                .build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Số áo 10");
    }

    @Test
    @DisplayName("đội hình hợp lệ được lưu kèm đăng ký")
    void validLineupIsPersisted() throws ServiceException {
        when(tournamentRepository.findByIdForUpdate(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());
        when(lineupRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        service.registerTeam(RegisterTournamentTeamCommand.builder()
                .tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.CLUB).clubId(UUID.randomUUID()).teamName("Đội D")
                .lineups(List.of(
                        RegisterTournamentTeamCommand.LineupItem.builder()
                                .playerId(UUID.randomUUID()).shirtNumber(7)
                                .lineupRole(LineupRole.CAPTAIN).build(),
                        RegisterTournamentTeamCommand.LineupItem.builder()
                                .playerId(UUID.randomUUID()).shirtNumber(9)
                                .lineupRole(LineupRole.SUBSTITUTE).build()))
                .build());

        verify(lineupRepository).saveAll(any());
    }

    // ---------- TOUR-05: bracket ----------

    @Test
    @DisplayName("cùng một giải sinh lại vẫn ra đúng thứ tự cặp đấu")
    void bracketGenerationIsDeterministic() throws ServiceException {
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_CLOSED,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(confirmedTeams(4));

        List<UUID> first = firstRoundPairs(service.generateFixtures(tournamentId, organizerId));
        List<UUID> second = firstRoundPairs(service.generateFixtures(tournamentId, organizerId));

        assertThat(first).isEqualTo(second);
    }

    private List<UUID> firstRoundPairs(List<TournamentFixtureResponseCommand> fixtures) {
        return fixtures.stream()
                .filter(item -> item.getRoundNumber() == 1)
                .sorted((a, b) -> Integer.compare(a.getMatchNumber(), b.getMatchNumber()))
                .flatMap(item -> java.util.stream.Stream.of(item.getRegistration1Id(), item.getRegistration2Id()))
                .toList();
    }

    @Test
    @DisplayName("bốn đội sinh đủ bán kết và chung kết, mỗi trận biết trận kế tiếp")
    void bracketLinksEveryRound() throws ServiceException {
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_CLOSED,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(confirmedTeams(4));

        List<TournamentFixtureResponseCommand> fixtures = service.generateFixtures(tournamentId, organizerId);

        assertThat(fixtures).hasSize(3);
        List<TournamentFixtureResponseCommand> round1 = fixtures.stream()
                .filter(item -> item.getRoundNumber() == 1).toList();
        TournamentFixtureResponseCommand finalMatch = fixtures.stream()
                .filter(item -> item.getRoundNumber() == 2).findFirst().orElseThrow();

        assertThat(round1).hasSize(2);
        assertThat(round1).allSatisfy(item ->
                assertThat(item.getNextFixtureId()).isEqualTo(finalMatch.getFixtureId()));
        assertThat(finalMatch.getNextFixtureId()).isNull();
        assertThat(finalMatch.getRoundName()).isEqualTo("Chung kết");
    }

    @Test
    @DisplayName("đội được miễn thi đấu tự vào vòng sau thay vì kẹt lại")
    void byeAdvancesAutomatically() throws ServiceException {
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_CLOSED,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(confirmedTeams(3));

        List<TournamentFixtureResponseCommand> fixtures = service.generateFixtures(tournamentId, organizerId);
        Map<Integer, List<TournamentFixtureResponseCommand>> byRound = fixtures.stream()
                .collect(Collectors.groupingBy(TournamentFixtureResponseCommand::getRoundNumber));

        TournamentFixtureResponseCommand bye = byRound.get(1).stream()
                .filter(item -> item.getRegistration2Id() == null).findFirst().orElseThrow();
        assertThat(bye.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(bye.getWinnerRegistrationId()).isEqualTo(bye.getRegistration1Id());
    }

    @Test
    @DisplayName("thắng vòng trước được đẩy vào đúng nhánh của trận kế tiếp")
    void winnerIsPushedIntoNextFixture() throws ServiceException {
        UUID winner = new UUID(0L, 1L);
        UUID loser = new UUID(0L, 2L);
        UUID nextFixtureId = UUID.randomUUID();
        TournamentFixture semiFinal = TournamentFixture.builder()
                .fixtureId(UUID.randomUUID()).tournamentId(tournamentId).roundNumber(1).matchNumber(1)
                .registration1Id(winner).registration2Id(loser)
                .nextFixtureId(nextFixtureId).status(MatchStatus.SCHEDULED).build();
        TournamentFixture finalMatch = TournamentFixture.builder()
                .fixtureId(nextFixtureId).tournamentId(tournamentId).roundNumber(2).matchNumber(1)
                .status(MatchStatus.SCHEDULED).build();

        when(fixtureRepository.findById(semiFinal.getFixtureId())).thenReturn(Optional.of(semiFinal));
        when(fixtureRepository.findById(nextFixtureId)).thenReturn(Optional.of(finalMatch));
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findById(any())).thenReturn(Optional.empty());

        service.updateFixtureResult(UpdateFixtureResultCommand.builder()
                .fixtureId(semiFinal.getFixtureId()).actorUserId(organizerId).score1(3).score2(1).build());

        assertThat(finalMatch.getRegistration1Id()).isEqualTo(winner);
    }

    @Test
    @DisplayName("không cho đổi đội thắng khi trận kế tiếp đã có kết quả")
    void cannotChangeWinnerAfterNextRoundPlayed() {
        UUID first = new UUID(0L, 1L);
        UUID second = new UUID(0L, 2L);
        UUID nextFixtureId = UUID.randomUUID();
        TournamentFixture semiFinal = TournamentFixture.builder()
                .fixtureId(UUID.randomUUID()).tournamentId(tournamentId).roundNumber(1).matchNumber(1)
                .registration1Id(first).registration2Id(second).score1(3).score2(1)
                .winnerRegistrationId(first).nextFixtureId(nextFixtureId)
                .status(MatchStatus.COMPLETED).build();
        TournamentFixture finalMatch = TournamentFixture.builder()
                .fixtureId(nextFixtureId).tournamentId(tournamentId).roundNumber(2).matchNumber(1)
                .status(MatchStatus.COMPLETED).build();

        when(fixtureRepository.findById(semiFinal.getFixtureId())).thenReturn(Optional.of(semiFinal));
        when(fixtureRepository.findById(nextFixtureId)).thenReturn(Optional.of(finalMatch));
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));

        assertThatThrownBy(() -> service.updateFixtureResult(UpdateFixtureResultCommand.builder()
                .fixtureId(semiFinal.getFixtureId()).actorUserId(organizerId).score1(0).score2(4).build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đã có kết quả");
    }

    // ---------- CLUB-04: thanh tich CLB ----------

    @Test
    @DisplayName("sửa tỷ số không được cộng dồn thành tích cũ")
    void correctingScoreDoesNotDoubleCountClubStats() throws ServiceException {
        UUID clubA = UUID.randomUUID();
        UUID clubB = UUID.randomUUID();
        UUID regA = new UUID(0L, 1L);
        UUID regB = new UUID(0L, 2L);
        Club first = Club.builder().clubId(clubA).ownerId(UUID.randomUUID()).name("A")
                .winCount(1).lossCount(0).drawCount(0).matchCount(1).winRate(1D).build();
        Club secondClub = Club.builder().clubId(clubB).ownerId(UUID.randomUUID()).name("B")
                .winCount(0).lossCount(1).drawCount(0).matchCount(1).winRate(0D).build();

        TournamentFixture fixture = TournamentFixture.builder()
                .fixtureId(UUID.randomUUID()).tournamentId(tournamentId).roundNumber(1).matchNumber(1)
                .registration1Id(regA).registration2Id(regB).score1(3).score2(1)
                .winnerRegistrationId(regA).status(MatchStatus.COMPLETED).build();

        when(fixtureRepository.findById(fixture.getFixtureId())).thenReturn(Optional.of(fixture));
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS,
                        TournamentFormat.ROUND_ROBIN, 8)));
        when(registrationRepository.findById(regA)).thenReturn(Optional.of(registration(regA, clubA)));
        when(registrationRepository.findById(regB)).thenReturn(Optional.of(registration(regB, clubB)));
        Map<UUID, Club> clubs = java.util.stream.Stream.of(first, secondClub)
                .collect(Collectors.toMap(Club::getClubId, Function.identity()));
        when(clubRepository.findById(any())).thenAnswer(call -> Optional.ofNullable(clubs.get(call.getArgument(0))));
        when(clubRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // Sua lai thanh B thang
        service.updateFixtureResult(UpdateFixtureResultCommand.builder()
                .fixtureId(fixture.getFixtureId()).actorUserId(organizerId).score1(0).score2(2).build());

        assertThat(first.getWinCount()).isZero();
        assertThat(first.getLossCount()).isEqualTo(1);
        assertThat(first.getMatchCount()).isEqualTo(1);
        assertThat(secondClub.getWinCount()).isEqualTo(1);
        assertThat(secondClub.getLossCount()).isZero();
        assertThat(secondClub.getMatchCount()).isEqualTo(1);
        assertThat(secondClub.getWinRate()).isEqualTo(1D);
    }

    // ---------- TOUR-04: dat san ----------

    @Test
    @DisplayName("đặt sân trùng khung giờ bị từ chối")
    void overlappingReservationIsRejected() {
        UUID courtId = UUID.randomUUID();
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(reservationRepository.findActiveOverlaps(any(), any(), any(), any()))
                .thenReturn(List.of(TournamentVenueReservation.builder()
                        .reservationId(UUID.randomUUID()).tournamentId(UUID.randomUUID())
                        .courtId(courtId).status(ReservationStatus.CONFIRMED).build()));

        assertThatThrownBy(() -> service.reserveVenue(ReserveTournamentVenueCommand.builder()
                .tournamentId(tournamentId).actorUserId(organizerId)
                .venueId(UUID.randomUUID()).courtId(courtId).playDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(20, 0)).build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đã được giữ");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("trả sân thì gỡ liên kết khỏi trận đấu")
    void releasingReservationDetachesFixture() throws ServiceException {
        UUID reservationId = UUID.randomUUID();
        TournamentVenueReservation reservation = TournamentVenueReservation.builder()
                .reservationId(reservationId).tournamentId(tournamentId)
                .status(ReservationStatus.CONFIRMED).build();
        TournamentFixture fixture = TournamentFixture.builder()
                .fixtureId(UUID.randomUUID()).tournamentId(tournamentId)
                .reservationId(reservationId).status(MatchStatus.SCHEDULED).build();

        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(fixture));

        service.releaseReservation(tournamentId, reservationId, organizerId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(fixture.getReservationId()).isNull();
    }

    @Test
    @DisplayName("giữ sân phải đi qua venue-service, không tự ghi vào lịch sân")
    void reservationHoldsRealSlotAtVenue() throws ServiceException {
        UUID courtId = UUID.randomUUID();
        LocalDate playDate = LocalDate.now().plusDays(1);
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(reservationRepository.findActiveOverlaps(any(), any(), any(), any())).thenReturn(List.of());
        when(reservationRepository.save(any())).thenAnswer(call -> {
            TournamentVenueReservation reservation = call.getArgument(0);
            if (reservation.getReservationId() == null) reservation.setReservationId(UUID.randomUUID());
            return reservation;
        });

        service.reserveVenue(ReserveTournamentVenueCommand.builder()
                .tournamentId(tournamentId).actorUserId(organizerId)
                .venueId(UUID.randomUUID()).courtId(courtId).playDate(playDate)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(20, 0)).build());

        verify(venueSlotReservation).hold(courtId, playDate, LocalTime.of(18, 0), LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("người chơi thường đã đặt giờ thì giải không giữ được và không ghi đặt chỗ")
    void reservationIsRejectedWhenVenueSlotTaken() throws ServiceException {
        UUID courtId = UUID.randomUUID();
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(reservationRepository.findActiveOverlaps(any(), any(), any(), any())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new ServiceException("Không giữ được khung giờ này tại cơ sở."))
                .when(venueSlotReservation).hold(any(), any(), any(), any());

        assertThatThrownBy(() -> service.reserveVenue(ReserveTournamentVenueCommand.builder()
                .tournamentId(tournamentId).actorUserId(organizerId)
                .venueId(UUID.randomUUID()).courtId(courtId).playDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(20, 0)).build()))
                .isInstanceOf(ServiceException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("trả sân cũng trả lại khung giờ bên venue-service")
    void releasingReservationReleasesVenueSlot() throws ServiceException {
        UUID reservationId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        LocalDate playDate = LocalDate.now().plusDays(2);
        TournamentVenueReservation reservation = TournamentVenueReservation.builder()
                .reservationId(reservationId).tournamentId(tournamentId).courtId(courtId)
                .playDate(playDate).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(11, 0))
                .status(ReservationStatus.CONFIRMED).build();

        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        service.releaseReservation(tournamentId, reservationId, organizerId);

        verify(venueSlotReservation).release(courtId, playDate, LocalTime.of(9, 0), LocalTime.of(11, 0));
    }

    // ---------- TOUR-01: vong doi trang thai ----------

    @Test
    @DisplayName("không thể hoàn thành giải khi còn trận chưa có kết quả")
    void cannotCompleteWithPendingMatches() {
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.IN_PROGRESS,
                        TournamentFormat.ROUND_ROBIN, 8)));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(
                TournamentFixture.builder().fixtureId(UUID.randomUUID()).tournamentId(tournamentId)
                        .status(MatchStatus.SCHEDULED).build()));

        assertThatThrownBy(() -> service.changeStatus(tournamentId, TournamentStatus.COMPLETED, organizerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("chưa có kết quả");
    }

    @Test
    @DisplayName("chỉ ban tổ chức được đổi trạng thái giải")
    void onlyOrganizerCanChangeStatus() {
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.DRAFT,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));

        assertThatThrownBy(() ->
                service.changeStatus(tournamentId, TournamentStatus.PUBLISHED, UUID.randomUUID()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("giải đã hủy không quay lại trạng thái khác")
    void cancelledTournamentIsTerminal() {
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.CANCELLED,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));

        assertThatThrownBy(() ->
                service.changeStatus(tournamentId, TournamentStatus.PUBLISHED, organizerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đã kết thúc hoặc đã hủy");
    }

    // ---------- SOC-03: chat nhom giai dau ----------

    @Test
    @DisplayName("đăng ký xong thì đồng bộ chat nhóm giải, chỉ gồm người đã xác nhận")
    void registrationSyncsTournamentConversationWithConfirmedOnly() throws ServiceException {
        UUID confirmedUser = UUID.randomUUID();
        TournamentRegistration confirmed = registration(UUID.randomUUID(), null);
        confirmed.setRegisteredBy(confirmedUser);
        TournamentRegistration pending = registration(UUID.randomUUID(), null);
        pending.setStatus(RegistrationStatus.PENDING_PAYMENT);

        when(tournamentRepository.findByIdForUpdate(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentId(tournamentId))
                .thenReturn(List.of(confirmed, pending));

        service.registerTeam(RegisterTournamentTeamCommand.builder()
                .tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.INDIVIDUAL).build());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<UUID>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(conversationSync).syncTournament(any(), captor.capture());
        assertThat(captor.getValue()).containsExactly(confirmedUser);
    }

    @Test
    @DisplayName("hủy đăng ký cũng đồng bộ lại chat nhóm giải")
    void cancellingRegistrationSyncsTournamentConversation() throws ServiceException {
        UUID actor = UUID.randomUUID();
        TournamentRegistration mine = registration(UUID.randomUUID(), null);
        mine.setRegisteredBy(actor);
        when(tournamentRepository.findById(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findById(mine.getRegistrationId())).thenReturn(Optional.of(mine));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        service.cancelRegistration(tournamentId, mine.getRegistrationId(), actor);

        verify(conversationSync).syncTournament(any(), any());
    }

    @Test
    @DisplayName("social-service lỗi không làm hỏng việc đăng ký")
    void conversationSyncFailureDoesNotBreakRegistration() throws ServiceException {
        when(tournamentRepository.findByIdForUpdate(tournamentId))
                .thenReturn(Optional.of(tournament(TournamentStatus.REGISTRATION_OPEN,
                        TournamentFormat.SINGLE_ELIMINATION, 8)));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of());
        when(conversationSync.syncTournament(any(), any())).thenReturn(Optional.empty());

        service.registerTeam(RegisterTournamentTeamCommand.builder()
                .tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.INDIVIDUAL).build());

        verify(registrationRepository).save(any());
    }
}
