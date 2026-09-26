package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.CreateTournamentCommand;
import iuh.fit.clubservice.application.dto.request.ReserveTournamentVenueCommand;
import iuh.fit.clubservice.application.dto.request.UpdateFixtureResultCommand;
import iuh.fit.clubservice.application.dto.response.TournamentFixtureResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentStandingResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.VenueCourtHold;
import iuh.fit.clubservice.application.port.persistence.*;
import iuh.fit.clubservice.application.service.TournamentRegistrationService;
import iuh.fit.clubservice.domain.entity.*;
import iuh.fit.clubservice.domain.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TournamentServiceImplTest {

    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentFixtureRepository fixtureRepository;
    @Mock private TournamentEligibilityRuleRepository eligibilityRuleRepository;
    @Mock private TournamentVenueReservationRepository reservationRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private VenueCourtHold venueCourtHold;
    @Mock private TournamentRegistrationService registrationService;
    @Mock private iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository lineupRepository;
    @Mock private iuh.fit.clubservice.application.port.external.PlayerProfileProvider playerProfileProvider;

    private TournamentServiceImpl service;
    private TournamentFixtureServiceImpl fixtureService;
    private TournamentScheduleServiceImpl schedule;

    private final UUID tournamentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID organizerId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();
    private final UUID court1 = UUID.randomUUID();
    private final UUID court2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TournamentServiceImpl(tournamentRepository, registrationRepository, fixtureRepository,
                eligibilityRuleRepository, reservationRepository, venueCourtHold, registrationService,
                lineupRepository, playerProfileProvider);
        fixtureService = new TournamentFixtureServiceImpl(tournamentRepository, registrationRepository, fixtureRepository,
                clubRepository, registrationService);
        schedule = new TournamentScheduleServiceImpl(tournamentRepository, fixtureRepository, reservationRepository);

        when(eligibilityRuleRepository.findByTournamentId(any())).thenReturn(List.of());
        when(tournamentRepository.save(any())).thenAnswer(call -> {
            Tournament tournament = call.getArgument(0);
            if (tournament.getTournamentId() == null) tournament.setTournamentId(tournamentId);
            return tournament;
        });
        when(fixtureRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(registrationRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(call -> {
            TournamentVenueReservation reservation = call.getArgument(0);
            if (reservation.getReservationId() == null) reservation.setReservationId(UUID.randomUUID());
            return reservation;
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
                .venueId(venueId).courtIds(List.of(court1, court2))
                .dailyStartTime(LocalTime.of(7, 0)).dailyEndTime(LocalTime.of(21, 0))
                .matchDurationMinutes(60).playFormat("FOOTBALL_7")
                .rules(List.of()).build();
    }

    /** Stub ca doc thuong lan doc co khoa hang, vi service dung ca hai tuy thao tac. */
    private void stubTournament(Tournament tournament) {
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.findByIdForUpdate(tournamentId)).thenReturn(Optional.of(tournament));
    }

    private TournamentRegistration registration(UUID id, UUID clubId) {
        return TournamentRegistration.builder()
                .registrationId(id).tournamentId(tournamentId).registeredBy(UUID.randomUUID())
                .type(RegistrationType.TEAM).status(RegistrationStatus.CONFIRMED)
                .clubId(clubId).build();
    }

    private List<TournamentRegistration> confirmedTeams(int count) {
        List<TournamentRegistration> teams = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            teams.add(registration(new UUID(0L, index + 1L), null));
        }
        return teams;
    }

    private CreateTournamentCommand createCommand(boolean publish, int maxParticipants, List<UUID> courts,
                                                  LocalTime start, LocalTime end) {
        LocalDate today = LocalDate.now();
        return CreateTournamentCommand.builder().organizerId(organizerId).name("Giải sân 7 mùa thu")
                .sportType(SportType.FOOTBALL).format(TournamentFormat.SINGLE_ELIMINATION)
                .maxParticipants(maxParticipants).entryFee(300_000D).prizePool(0D)
                .registrationOpenDate(today).registrationCloseDate(today.plusDays(5))
                .startDate(today.plusDays(6)).endDate(today.plusDays(7))
                .venueId(venueId).courtIds(courts).dailyStartTime(start).dailyEndTime(end)
                .matchDurationMinutes(60).playFormat("FOOTBALL_7").publish(publish).build();
    }

    // ---------- tao giai: chi tai co so cua chu san, du san ----------

    @Test
    @DisplayName("công bố ngay thì giữ sân thật bên venue cho cả thời gian giải")
    void publishingOnCreateHoldsCourts() throws ServiceException {
        service.createTournament(createCommand(true, 8, List.of(court1, court2), LocalTime.of(8, 0), LocalTime.of(20, 0)));

        ArgumentCaptor<Tournament> held = ArgumentCaptor.forClass(Tournament.class);
        verify(venueCourtHold).hold(held.capture(), eq(false));
        assertThat(held.getValue().getCourtIds()).containsExactly(court1, court2);
        assertThat(held.getValue().getVenueId()).isEqualTo(venueId);
    }

    @Test
    @DisplayName("lưu nháp chỉ kiểm tra sân, chưa khóa sân của người chơi")
    void draftOnlyValidatesCourts() throws ServiceException {
        var created = service.createTournament(createCommand(false, 8, List.of(court1), LocalTime.of(8, 0), LocalTime.of(20, 0)));

        assertThat(created.getStatus()).isEqualTo(TournamentStatus.DRAFT);
        verify(venueCourtHold).hold(any(), eq(true));
    }

    @Test
    @DisplayName("không đủ sân cho số trận tối đa thì không tạo giải")
    void notEnoughCourtsIsRejected() throws ServiceException {
        // 32 doi loai truc tiep = 31 tran; 1 san x 2 ngay x 2 tran/ngay = 4 tran.
        assertThatThrownBy(() -> service.createTournament(createCommand(true, 32, List.of(court1),
                LocalTime.of(18, 0), LocalTime.of(20, 0))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Không đủ sân").hasMessageContaining("31 trận");
        verify(tournamentRepository, never()).save(any());
        verify(venueCourtHold, never()).hold(any(), anyBoolean());
    }

    @Test
    @DisplayName("venue từ chối (không phải cơ sở của bạn, sân đã có người đặt...) thì lỗi đi thẳng tới người tạo")
    void venueRejectionReachesOrganizer() throws ServiceException {
        doThrow(new ServiceException("Người chơi đã đặt sân trong thời gian giải: Sân 1 (2 lượt)"))
                .when(venueCourtHold).hold(any(), anyBoolean());

        assertThatThrownBy(() -> service.createTournament(createCommand(true, 8, List.of(court1, court2),
                LocalTime.of(8, 0), LocalTime.of(20, 0))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("Sân 1 (2 lượt)");
    }

    @Test
    @DisplayName("hình thức thi đấu phải thuộc môn của giải")
    void playFormatMustMatchSport() {
        CreateTournamentCommand command = createCommand(true, 8, List.of(court1), LocalTime.of(8, 0), LocalTime.of(20, 0));
        command.setPlayFormat("BADMINTON_DOUBLES");

        assertThatThrownBy(() -> service.createTournament(command))
                .isInstanceOf(ServiceException.class).hasMessageContaining("không thuộc môn");
    }

    // ---------- vong doi: cong bo / huy / ket thuc ----------

    @Test
    @DisplayName("công bố bản nháp thì mới khóa sân")
    void publishingDraftHoldsCourts() throws ServiceException {
        stubTournament(tournament(TournamentStatus.DRAFT, TournamentFormat.SINGLE_ELIMINATION, 8));

        service.changeStatus(tournamentId, TournamentStatus.PUBLISHED, organizerId);

        verify(venueCourtHold).hold(any(), eq(false));
    }

    @Test
    @DisplayName("bản nháp cũ chưa gắn cơ sở thì không công bố được")
    void legacyDraftWithoutVenueCannotPublish() {
        Tournament draft = tournament(TournamentStatus.DRAFT, TournamentFormat.SINGLE_ELIMINATION, 8);
        draft.setVenueId(null);
        stubTournament(draft);

        assertThatThrownBy(() -> service.changeStatus(tournamentId, TournamentStatus.PUBLISHED, organizerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("chọn cơ sở");
    }

    @Test
    @DisplayName("hủy giải trả sân bên venue, đóng lịch trận và đóng mọi đăng ký (kèm hoàn phí)")
    void inProgressTournamentCannotBeCancelled() {
        stubTournament(tournament(TournamentStatus.IN_PROGRESS, TournamentFormat.SINGLE_ELIMINATION, 8));

        assertThatThrownBy(() -> service.changeStatus(tournamentId, TournamentStatus.CANCELLED, organizerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đang diễn ra");
        verify(venueCourtHold, never()).release(any());
    }

    @Test
    @DisplayName("giải đã kết thúc trả về nhà vô địch là đội thắng trận chung kết")
    void completedTournamentReportsChampion() throws ServiceException {
        UUID finalist = UUID.randomUUID();
        UUID winner = UUID.randomUUID();
        stubTournament(tournament(TournamentStatus.COMPLETED, TournamentFormat.SINGLE_ELIMINATION, 2));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(
                TournamentRegistration.builder().registrationId(winner).tournamentId(tournamentId).teamName("Code FC")
                        .registeredBy(UUID.randomUUID()).status(RegistrationStatus.CONFIRMED).build(),
                TournamentRegistration.builder().registrationId(finalist).tournamentId(tournamentId).teamName("Gym FC")
                        .registeredBy(UUID.randomUUID()).status(RegistrationStatus.CONFIRMED).build()));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(TournamentFixture.builder()
                .tournamentId(tournamentId).roundNumber(1).registration1Id(winner).registration2Id(finalist)
                .winnerRegistrationId(winner).status(MatchStatus.COMPLETED).build()));

        var response = service.getTournamentDetails(tournamentId, organizerId);

        assertThat(response.getChampionRegistrationId()).isEqualTo(winner);
        assertThat(response.getChampionName()).isEqualTo("Code FC");
    }

    @Test
    void cancellingReleasesCourtsAndClosesRegistrations() throws ServiceException {
        stubTournament(tournament(TournamentStatus.PUBLISHED, TournamentFormat.SINGLE_ELIMINATION, 8));
        TournamentVenueReservation match = TournamentVenueReservation.builder().reservationId(UUID.randomUUID())
                .tournamentId(tournamentId).courtId(court1).status(ReservationStatus.CONFIRMED).build();
        when(reservationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(match));

        service.changeStatus(tournamentId, TournamentStatus.CANCELLED, organizerId);

        verify(venueCourtHold).release(tournamentId);
        verify(registrationService).closeAllForCancelledTournament(any(), eq(organizerId));
        assertThat(match.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("kết thúc sớm trả lại các ngày còn lại cho người chơi đặt")
    void completingReleasesCourts() throws ServiceException {
        stubTournament(tournament(TournamentStatus.IN_PROGRESS, TournamentFormat.ROUND_ROBIN, 8));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        service.changeStatus(tournamentId, TournamentStatus.COMPLETED, organizerId);

        verify(venueCourtHold).release(tournamentId);
    }

    @Test
    @DisplayName("không thể hoàn thành giải khi còn trận chưa có kết quả")
    void cannotCompleteWithPendingMatches() {
        stubTournament(tournament(TournamentStatus.IN_PROGRESS, TournamentFormat.ROUND_ROBIN, 8));
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
        stubTournament(tournament(TournamentStatus.DRAFT, TournamentFormat.SINGLE_ELIMINATION, 8));

        assertThatThrownBy(() -> service.changeStatus(tournamentId, TournamentStatus.PUBLISHED, UUID.randomUUID()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("giải đã hủy không quay lại trạng thái khác")
    void cancelledTournamentIsTerminal() {
        stubTournament(tournament(TournamentStatus.CANCELLED, TournamentFormat.SINGLE_ELIMINATION, 8));

        assertThatThrownBy(() -> service.changeStatus(tournamentId, TournamentStatus.PUBLISHED, organizerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đã kết thúc hoặc đã hủy");
    }

    @Test
    @DisplayName("không công bố bản nháp khi hạn đăng ký đã qua")
    void cannotPublishDraftAfterRegistrationClosed() {
        Tournament draft = tournament(TournamentStatus.DRAFT, TournamentFormat.SINGLE_ELIMINATION, 8);
        draft.setRegistrationCloseDate(LocalDate.now().minusDays(1));
        stubTournament(draft);

        assertThatThrownBy(() -> service.changeStatus(tournamentId, TournamentStatus.PUBLISHED, organizerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Hạn đăng ký đã qua");
    }

    @Test
    @DisplayName("bản nháp chỉ ban tổ chức xem được")
    void draftIsHiddenFromOthers() throws ServiceException {
        stubTournament(tournament(TournamentStatus.DRAFT, TournamentFormat.SINGLE_ELIMINATION, 8));

        assertThat(service.getTournamentDetails(tournamentId, organizerId).getStatus()).isEqualTo(TournamentStatus.DRAFT);
        assertThatThrownBy(() -> service.getTournamentDetails(tournamentId, UUID.randomUUID()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("response mang hình thức thi đấu và số người đội suy ra từ policy")
    void responseCarriesPlayFormat() throws ServiceException {
        stubTournament(tournament(TournamentStatus.PUBLISHED, TournamentFormat.SINGLE_ELIMINATION, 8));

        var response = service.getTournamentDetails(tournamentId, null);

        assertThat(response.getParticipantType()).isEqualTo(ParticipantType.TEAM);
        assertThat(response.getRosterMin()).isEqualTo(7);
        assertThat(response.getRosterMax()).isEqualTo(14);
    }

    // ---------- sua giai ----------

    private CreateTournamentCommand editCommand(double entryFee, int maxParticipants) {
        LocalDate today = LocalDate.now();
        return CreateTournamentCommand.builder().organizerId(organizerId).name("Giải test (sửa)")
                .sportType(SportType.FOOTBALL).format(TournamentFormat.SINGLE_ELIMINATION)
                .maxParticipants(maxParticipants).entryFee(entryFee).prizePool(0D)
                .registrationOpenDate(today.minusDays(1)).registrationCloseDate(today.plusDays(5))
                .startDate(today.plusDays(6)).endDate(today.plusDays(7))
                .venueId(venueId).courtIds(List.of(court1, court2)).dailyStartTime(LocalTime.of(7, 0))
                .dailyEndTime(LocalTime.of(21, 0)).matchDurationMinutes(60).playFormat("FOOTBALL_7").build();
    }

    @Test
    @DisplayName("giải đã công bố thì không sửa được nội dung, kể cả khi chưa ai đăng ký")
    void publishedTournamentIsReadOnly() throws ServiceException {
        for (TournamentStatus status : List.of(TournamentStatus.PUBLISHED, TournamentStatus.REGISTRATION_OPEN,
                TournamentStatus.IN_PROGRESS, TournamentStatus.CANCELLED)) {
            stubTournament(tournament(status, TournamentFormat.SINGLE_ELIMINATION, 8));
            assertThatThrownBy(() -> service.updateTournament(tournamentId, editCommand(0D, 8)))
                    .isInstanceOf(ServiceException.class).hasMessageContaining("đã công bố");
        }
        verify(tournamentRepository, never()).save(any());
        verify(venueCourtHold, never()).hold(any(), anyBoolean());
    }

    @Test
    @DisplayName("bản nháp sửa được mọi thứ, chỉ kiểm tra sân (dry run) chứ chưa giữ")
    void draftIsFullyEditable() throws ServiceException {
        stubTournament(tournament(TournamentStatus.DRAFT, TournamentFormat.SINGLE_ELIMINATION, 8));
        CreateTournamentCommand command = editCommand(80000D, 16);
        command.setEligibilityRules(List.of(CreateTournamentCommand.EligibilityRuleItem.builder()
                .ruleType(EligibilityRuleType.AGE).operator(EligibilityRuleOperator.LESS_THAN_OR_EQUAL)
                .expectedValue("35").build()));

        var response = service.updateTournament(tournamentId, command);

        assertThat(response.getEntryFee()).isEqualTo(80000D);
        assertThat(response.getMaxParticipants()).isEqualTo(16);
        verify(venueCourtHold).hold(any(), eq(true));
        verify(eligibilityRuleRepository).deleteByTournamentId(tournamentId);
    }

    @Test
    @DisplayName("giải cũ có cột điều lệ null vẫn trả về danh sách rỗng (client đọc rules.length)")
    void legacyNullRulesAreReturnedAsEmptyList() throws ServiceException {
        Tournament legacy = tournament(TournamentStatus.REGISTRATION_OPEN, TournamentFormat.SINGLE_ELIMINATION, 8);
        legacy.setRules(null);
        stubTournament(legacy);

        assertThat(service.getTournamentDetails(tournamentId, organizerId).getRules()).isEmpty();
    }

    @Test
    @DisplayName("người khác không sửa được giải")
    void onlyOrganizerCanEdit() {
        stubTournament(tournament(TournamentStatus.DRAFT, TournamentFormat.SINGLE_ELIMINATION, 8));
        CreateTournamentCommand command = editCommand(0D, 8);
        command.setOrganizerId(UUID.randomUUID());

        assertThatThrownBy(() -> service.updateTournament(tournamentId, command))
                .isInstanceOf(ServiceException.class).hasMessageContaining("người tổ chức");
    }

    // ---------- xep tran vao san cua giai ----------

    private ReserveTournamentVenueCommand reserve(UUID court, LocalDate date, LocalTime start, LocalTime end) {
        return ReserveTournamentVenueCommand.builder().tournamentId(tournamentId).actorUserId(organizerId)
                .courtId(court).playDate(date).startTime(start).endTime(end).build();
    }

    @Test
    @DisplayName("xếp trận vào sân của giải không gọi venue: sân đã được giữ nguyên khối")
    void schedulingMatchUsesHeldCourts() throws ServiceException {
        // Trước khi giải bắt đầu được giữ chỗ trước (không gắn trận); đang diễn ra thì phải gắn trận cụ thể.
        Tournament tournament = tournament(TournamentStatus.PUBLISHED, TournamentFormat.SINGLE_ELIMINATION, 8);
        stubTournament(tournament);
        when(reservationRepository.findActiveOverlaps(any(), any(), any(), any())).thenReturn(List.of());

        var response = schedule.reserveVenue(reserve(court1, tournament.getStartDate(), LocalTime.of(8, 0), LocalTime.of(9, 0)));

        assertThat(response.getVenueId()).isEqualTo(venueId);
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(venueCourtHold, never()).hold(any(), anyBoolean());
    }

    @Test
    @DisplayName("sân ngoài danh sách, ngày ngoài giải hoặc giờ ngoài khung đã giữ đều bị chặn")
    void schedulingOutsideHeldCourtsIsRejected() {
        Tournament tournament = tournament(TournamentStatus.IN_PROGRESS, TournamentFormat.SINGLE_ELIMINATION, 8);
        stubTournament(tournament);

        assertThatThrownBy(() -> schedule.reserveVenue(reserve(UUID.randomUUID(), tournament.getStartDate(),
                LocalTime.of(8, 0), LocalTime.of(9, 0))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("danh sách sân");
        assertThatThrownBy(() -> schedule.reserveVenue(reserve(court1, tournament.getEndDate().plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(9, 0))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("thời gian diễn ra");
        assertThatThrownBy(() -> schedule.reserveVenue(reserve(court1, tournament.getStartDate(),
                LocalTime.of(20, 30), LocalTime.of(21, 30))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("khung");
        assertThatThrownBy(() -> schedule.reserveVenue(reserve(court1, tournament.getStartDate(),
                LocalTime.of(8, 0), LocalTime.of(8, 30))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("thời lượng");
    }

    @Test
    @DisplayName("hai trận không xếp trùng một sân một giờ")
    void overlappingMatchIsRejected() {
        Tournament tournament = tournament(TournamentStatus.IN_PROGRESS, TournamentFormat.SINGLE_ELIMINATION, 8);
        stubTournament(tournament);
        when(reservationRepository.findActiveOverlaps(any(), any(), any(), any())).thenReturn(List.of(
                TournamentVenueReservation.builder().reservationId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> schedule.reserveVenue(reserve(court1, tournament.getStartDate(),
                LocalTime.of(8, 0), LocalTime.of(9, 0))))
                .isInstanceOf(ServiceException.class).hasMessageContaining("trận khác");
    }

    @Test
    @DisplayName("bỏ lịch sân thì gỡ liên kết khỏi trận")
    void releasingReservationDetachesFixture() throws ServiceException {
        UUID reservationId = UUID.randomUUID();
        TournamentVenueReservation reservation = TournamentVenueReservation.builder()
                .reservationId(reservationId).tournamentId(tournamentId).status(ReservationStatus.CONFIRMED).build();
        TournamentFixture fixture = TournamentFixture.builder().fixtureId(UUID.randomUUID()).tournamentId(tournamentId)
                .reservationId(reservationId).status(MatchStatus.SCHEDULED).build();
        stubTournament(tournament(TournamentStatus.PUBLISHED, TournamentFormat.SINGLE_ELIMINATION, 8));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(fixture));

        schedule.releaseReservation(tournamentId, reservationId, organizerId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(fixture.getReservationId()).isNull();
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

        List<TournamentStandingResponseCommand> standings = fixtureService.getStandings(tournamentId);

        assertThat(standings.get(0).getRegistrationId()).isEqualTo(strong);
        assertThat(standings.get(0).getPoints()).isEqualTo(3);
        assertThat(standings.get(1).getPoints()).isZero();
    }

    @Test
    @DisplayName("bằng điểm thì xếp theo hiệu số bàn thắng")
    void standingsBreakTieByGoalDifference() {
        UUID better = new UUID(0L, 1L);
        UUID worse = new UUID(0L, 2L);
        UUID victim = new UUID(0L, 3L);
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(registration(better, null), registration(worse, null), registration(victim, null)));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(
                TournamentFixture.builder().fixtureId(UUID.randomUUID()).tournamentId(tournamentId)
                        .registration1Id(better).registration2Id(victim).score1(5).score2(0)
                        .status(MatchStatus.COMPLETED).build(),
                TournamentFixture.builder().fixtureId(UUID.randomUUID()).tournamentId(tournamentId)
                        .registration1Id(worse).registration2Id(victim).score1(1).score2(0)
                        .status(MatchStatus.COMPLETED).build()));

        List<TournamentStandingResponseCommand> standings = fixtureService.getStandings(tournamentId);

        assertThat(standings.get(0).getRegistrationId()).isEqualTo(better);
        assertThat(standings.get(1).getRegistrationId()).isEqualTo(worse);
    }

    // ---------- TOUR-05: bracket ----------

    @Test
    @DisplayName("cùng một giải sinh lại vẫn ra đúng thứ tự cặp đấu")
    void bracketGenerationIsDeterministic() throws ServiceException {
        stubTournament(tournament(TournamentStatus.REGISTRATION_CLOSED, TournamentFormat.SINGLE_ELIMINATION, 8));
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(confirmedTeams(4));

        List<UUID> first = firstRoundPairs(fixtureService.generateFixtures(tournamentId, organizerId));
        stubTournament(tournament(TournamentStatus.REGISTRATION_CLOSED, TournamentFormat.SINGLE_ELIMINATION, 8));
        List<UUID> second = firstRoundPairs(fixtureService.generateFixtures(tournamentId, organizerId));

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
        stubTournament(tournament(TournamentStatus.REGISTRATION_CLOSED, TournamentFormat.SINGLE_ELIMINATION, 8));
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(confirmedTeams(4));

        List<TournamentFixtureResponseCommand> fixtures = fixtureService.generateFixtures(tournamentId, organizerId);

        assertThat(fixtures).hasSize(3);
        TournamentFixtureResponseCommand finalMatch = fixtures.stream()
                .filter(item -> item.getRoundNumber() == 2).findFirst().orElseThrow();
        assertThat(fixtures.stream().filter(item -> item.getRoundNumber() == 1))
                .allSatisfy(item -> assertThat(item.getNextFixtureId()).isEqualTo(finalMatch.getFixtureId()));
        assertThat(finalMatch.getRoundName()).isEqualTo("Chung kết");
    }

    @Test
    @DisplayName("đội được miễn thi đấu tự vào vòng sau thay vì kẹt lại")
    void byeAdvancesAutomatically() throws ServiceException {
        stubTournament(tournament(TournamentStatus.REGISTRATION_CLOSED, TournamentFormat.SINGLE_ELIMINATION, 8));
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(confirmedTeams(3));

        List<TournamentFixtureResponseCommand> fixtures = fixtureService.generateFixtures(tournamentId, organizerId);
        Map<Integer, List<TournamentFixtureResponseCommand>> byRound = fixtures.stream()
                .collect(Collectors.groupingBy(TournamentFixtureResponseCommand::getRoundNumber));

        TournamentFixtureResponseCommand bye = byRound.get(1).stream()
                .filter(item -> item.getRegistration2Id() == null).findFirst().orElseThrow();
        assertThat(bye.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(bye.getWinnerRegistrationId()).isEqualTo(bye.getRegistration1Id());
    }

    @Test
    @DisplayName("xếp lịch đóng đăng ký chưa đủ người / chưa trả phí trước khi chốt bracket")
    void generatingFixturesClosesUnfinishedRegistrations() throws ServiceException {
        stubTournament(tournament(TournamentStatus.PUBLISHED, TournamentFormat.ROUND_ROBIN, 8));
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(confirmedTeams(3));

        fixtureService.generateFixtures(tournamentId, organizerId);

        verify(registrationService).closeUnfinishedBeforeFixtures(any(), eq(organizerId));
    }

    @Test
    @DisplayName("đã có trận có kết quả thì không xếp lại lịch (sẽ xóa kết quả)")
    void cannotRegenerateAfterResults() {
        stubTournament(tournament(TournamentStatus.IN_PROGRESS, TournamentFormat.ROUND_ROBIN, 8));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(
                TournamentFixture.builder().fixtureId(UUID.randomUUID()).tournamentId(tournamentId)
                        .registration1Id(new UUID(0L, 1L)).registration2Id(new UUID(0L, 2L))
                        .score1(1).score2(0).status(MatchStatus.COMPLETED).build()));

        assertThatThrownBy(() -> fixtureService.generateFixtures(tournamentId, organizerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("xếp lại");
        verify(fixtureRepository, never()).deleteByTournamentId(any());
    }

    @Test
    @DisplayName("không xếp lịch cho bản nháp")
    void draftCannotGenerateFixtures() {
        stubTournament(tournament(TournamentStatus.DRAFT, TournamentFormat.ROUND_ROBIN, 8));

        assertThatThrownBy(() -> fixtureService.generateFixtures(tournamentId, organizerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("công bố");
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
        stubTournament(tournament(TournamentStatus.IN_PROGRESS, TournamentFormat.SINGLE_ELIMINATION, 8));

        fixtureService.updateFixtureResult(UpdateFixtureResultCommand.builder()
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
                .winnerRegistrationId(first).nextFixtureId(nextFixtureId).status(MatchStatus.COMPLETED).build();
        TournamentFixture finalMatch = TournamentFixture.builder()
                .fixtureId(nextFixtureId).tournamentId(tournamentId).roundNumber(2).matchNumber(1)
                .status(MatchStatus.COMPLETED).build();
        when(fixtureRepository.findById(semiFinal.getFixtureId())).thenReturn(Optional.of(semiFinal));
        when(fixtureRepository.findById(nextFixtureId)).thenReturn(Optional.of(finalMatch));
        stubTournament(tournament(TournamentStatus.IN_PROGRESS, TournamentFormat.SINGLE_ELIMINATION, 8));

        assertThatThrownBy(() -> fixtureService.updateFixtureResult(UpdateFixtureResultCommand.builder()
                .fixtureId(semiFinal.getFixtureId()).actorUserId(organizerId).score1(0).score2(4).build()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã có kết quả");
    }

    @Test
    @DisplayName("chung kết loại trực tiếp không chấp nhận tỷ số hòa")
    void eliminationFinalRejectsDraw() {
        TournamentFixture finalMatch = TournamentFixture.builder().fixtureId(UUID.randomUUID())
                .tournamentId(tournamentId).roundNumber(2).matchNumber(1)
                .registration1Id(new UUID(0L, 1L)).registration2Id(new UUID(0L, 2L))
                .status(MatchStatus.SCHEDULED).build();
        when(fixtureRepository.findById(finalMatch.getFixtureId())).thenReturn(Optional.of(finalMatch));
        stubTournament(tournament(TournamentStatus.IN_PROGRESS, TournamentFormat.SINGLE_ELIMINATION, 8));

        assertThatThrownBy(() -> fixtureService.updateFixtureResult(UpdateFixtureResultCommand.builder()
                .fixtureId(finalMatch.getFixtureId()).actorUserId(organizerId).score1(2).score2(2).build()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("hòa");
    }

    @Test
    @DisplayName("giải đã kết thúc thì không sửa tỷ số được nữa")
    void scoreIsLockedOutsideInProgress() {
        TournamentFixture fixture = TournamentFixture.builder().fixtureId(UUID.randomUUID())
                .tournamentId(tournamentId).roundNumber(1).matchNumber(1)
                .registration1Id(new UUID(0L, 1L)).registration2Id(new UUID(0L, 2L))
                .status(MatchStatus.COMPLETED).score1(1).score2(0).build();
        when(fixtureRepository.findById(fixture.getFixtureId())).thenReturn(Optional.of(fixture));
        stubTournament(tournament(TournamentStatus.COMPLETED, TournamentFormat.ROUND_ROBIN, 8));

        assertThatThrownBy(() -> fixtureService.updateFixtureResult(UpdateFixtureResultCommand.builder()
                .fixtureId(fixture.getFixtureId()).actorUserId(organizerId).score1(0).score2(3).build()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đang diễn ra");
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
        stubTournament(tournament(TournamentStatus.IN_PROGRESS, TournamentFormat.ROUND_ROBIN, 8));
        when(registrationRepository.findById(regA)).thenReturn(Optional.of(registration(regA, clubA)));
        when(registrationRepository.findById(regB)).thenReturn(Optional.of(registration(regB, clubB)));
        Map<UUID, Club> clubs = java.util.stream.Stream.of(first, secondClub)
                .collect(Collectors.toMap(Club::getClubId, Function.identity()));
        when(clubRepository.findById(any())).thenAnswer(call -> Optional.ofNullable(clubs.get(call.getArgument(0))));
        when(clubRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        fixtureService.updateFixtureResult(UpdateFixtureResultCommand.builder()
                .fixtureId(fixture.getFixtureId()).actorUserId(organizerId).score1(0).score2(2).build());

        assertThat(first.getWinCount()).isZero();
        assertThat(first.getLossCount()).isEqualTo(1);
        assertThat(secondClub.getWinCount()).isEqualTo(1);
        assertThat(secondClub.getWinRate()).isEqualTo(1D);
    }
}
