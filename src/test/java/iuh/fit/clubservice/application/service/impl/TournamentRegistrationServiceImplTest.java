package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.application.port.external.TournamentFeePayment;
import iuh.fit.clubservice.application.port.persistence.*;
import iuh.fit.clubservice.domain.entity.*;
import iuh.fit.clubservice.domain.enums.*;
import iuh.fit.clubservice.application.service.TournamentPaymentEventService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Luu tru gia lap bang Map de cac buoc dang ky -> moi -> nhan loi -> dong phi chay noi tiep nhu that,
 * thay vi stub tung lan goi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TournamentRegistrationServiceImplTest {
    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentLineupRepository lineupRepository;
    @Mock private TournamentPaymentRepository paymentRepository;
    @Mock private TournamentEligibilityRuleRepository eligibilityRuleRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private ClubMemberRepository clubMemberRepository;
    @Mock private PlayerProfileProvider playerProfileProvider;
    @Mock private ClubConversationSync conversationSync;
    @Mock private TournamentFeePayment feePayment;
    @Mock private TournamentEventPublisher eventPublisher;
    @Mock private TournamentPaymentEventService paymentEventService;
    @Mock private ObjectProvider<TournamentPaymentEventService> paymentEvents;

    private TournamentRegistrationServiceImpl service;
    private TournamentTeamServiceImpl teams;
    @Mock private iuh.fit.clubservice.application.port.external.SportRatingPort sportRatingPort;
    private final UUID tournamentId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final Map<UUID, TournamentRegistration> registrations = new LinkedHashMap<>();
    private final Map<UUID, TournamentLineup> lineups = new LinkedHashMap<>();
    private final Map<UUID, TournamentPayment> payments = new LinkedHashMap<>();
    private Tournament tournament;

    @BeforeEach
    void setUp() {
        TournamentRegistrationSupport support = new TournamentRegistrationSupport(tournamentRepository,
                registrationRepository, lineupRepository, paymentRepository, eligibilityRuleRepository, clubMemberRepository,
                playerProfileProvider, conversationSync, eventPublisher, sportRatingPort);
        service = new TournamentRegistrationServiceImpl(support, tournamentRepository, registrationRepository,
                lineupRepository, paymentRepository, clubRepository, clubMemberRepository, feePayment, paymentEvents);
        when(paymentEvents.getObject()).thenReturn(paymentEventService);
        teams = new TournamentTeamServiceImpl(support, tournamentRepository, registrationRepository, lineupRepository,
                clubRepository);
        useTournament("BADMINTON_SINGLES", 150_000D, 8);

        when(eligibilityRuleRepository.findByTournamentId(any())).thenReturn(List.of());
        when(registrationRepository.save(any())).thenAnswer(call -> {
            TournamentRegistration registration = call.getArgument(0);
            if (registration.getRegistrationId() == null) registration.setRegistrationId(UUID.randomUUID());
            registrations.put(registration.getRegistrationId(), registration);
            return registration;
        });
        when(registrationRepository.findById(any())).thenAnswer(call -> Optional.ofNullable(registrations.get(call.getArgument(0))));
        when(registrationRepository.findByTournamentId(any())).thenAnswer(call -> new ArrayList<>(registrations.values()));
        when(registrationRepository.findByTournamentIdAndStatus(any(), any())).thenAnswer(call -> registrations.values().stream()
                .filter(item -> item.getStatus() == call.getArgument(1)).toList());
        when(lineupRepository.saveAll(any())).thenAnswer(call -> {
            List<TournamentLineup> rows = call.getArgument(0);
            rows.forEach(this::storeLineup);
            return rows;
        });
        when(lineupRepository.save(any())).thenAnswer(call -> storeLineup(call.getArgument(0)));
        when(lineupRepository.findByRegistrationId(any())).thenAnswer(call -> lineups.values().stream()
                .filter(row -> row.getRegistrationId().equals(call.getArgument(0))).toList());
        when(lineupRepository.findByPlayerIdAndMemberStatus(any(), any())).thenAnswer(call -> lineups.values().stream()
                .filter(row -> row.getPlayerId().equals(call.getArgument(0)) && row.getMemberStatus() == call.getArgument(1))
                .toList());
        org.mockito.Mockito.doAnswer(call -> lineups.remove(call.<UUID>getArgument(0)))
                .when(lineupRepository).deleteById(any());
        when(paymentRepository.save(any())).thenAnswer(call -> {
            TournamentPayment payment = call.getArgument(0);
            payments.put(payment.getRegistrationId(), payment);
            return payment;
        });
        when(paymentRepository.findByRegistrationId(any())).thenAnswer(call -> Optional.ofNullable(payments.get(call.getArgument(0))));
        when(feePayment.refund(any(), any(), any(), anyDouble(), anyString())).thenReturn(true);
    }

    private TournamentLineup storeLineup(TournamentLineup row) {
        if (row.getLineupId() == null) row.setLineupId(UUID.randomUUID());
        lineups.put(row.getLineupId(), row);
        return row;
    }

    private void useTournament(String playFormat, double fee, int maxParticipants) {
        SportType sport = playFormat.startsWith("BADMINTON") ? SportType.BADMINTON : SportType.FOOTBALL;
        tournament = Tournament.builder().tournamentId(tournamentId).organizerId(organizerId).name("Giải test")
                .sportType(sport).format(TournamentFormat.SINGLE_ELIMINATION).status(TournamentStatus.PUBLISHED)
                .maxParticipants(maxParticipants).entryFee(fee).prizePool(0D)
                .registrationOpenDate(LocalDate.now().minusDays(1)).registrationCloseDate(LocalDate.now().plusDays(3))
                .startDate(LocalDate.now().plusDays(5)).endDate(LocalDate.now().plusDays(6))
                .venueId(UUID.randomUUID()).courtIds(List.of(UUID.randomUUID()))
                .dailyStartTime(LocalTime.of(7, 0)).dailyEndTime(LocalTime.of(21, 0)).matchDurationMinutes(45)
                .playFormat(playFormat).build();
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.findByIdForUpdate(tournamentId)).thenReturn(Optional.of(tournament));
    }

    private RegisterTournamentTeamCommand individual(UUID player) {
        return RegisterTournamentTeamCommand.builder().tournamentId(tournamentId).registeredBy(player)
                .type(RegistrationType.INDIVIDUAL).build();
    }

    private RegisterTournamentTeamCommand team(UUID captain, String name, UUID... invitees) {
        List<RegisterTournamentTeamCommand.LineupItem> items = new ArrayList<>();
        for (UUID invitee : invitees) {
            items.add(RegisterTournamentTeamCommand.LineupItem.builder().playerId(invitee)
                    .playerName("Người " + items.size()).lineupRole(LineupRole.PLAYER).build());
        }
        return RegisterTournamentTeamCommand.builder().tournamentId(tournamentId).registeredBy(captain)
                .type(RegistrationType.TEAM).teamName(name).lineups(items).build();
    }

    // ---------- ca nhan / doi theo mon ----------

    @Test
    @DisplayName("đánh đơn có phí: đăng ký cá nhân giữ suất 24 giờ chờ đóng lệ phí")
    void singlesWithFeeWaitsForPaymentWithin24Hours() throws ServiceException {
        UUID player = UUID.randomUUID();

        var response = service.register(individual(player));

        assertThat(response.getStatus()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
        assertThat(response.getPaymentDeadline())
                .isCloseTo(LocalDateTime.now().plus(Duration.ofHours(24)), within(Duration.ofMinutes(1)));
        assertThat(payments.get(response.getRegistrationId()).getAmount()).isEqualTo(150_000D);
        verify(eventPublisher).publish(eq(TournamentEventPublisher.PAYMENT_DUE), any(), eq(player), any(), any());
    }

    @Test
    @DisplayName("giải miễn phí xác nhận ngay và đưa người chơi vào chat nhóm")
    void freeTournamentConfirmsImmediately() throws ServiceException {
        useTournament("BADMINTON_SINGLES", 0D, 8);

        var response = service.register(individual(UUID.randomUUID()));

        assertThat(response.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        verify(conversationSync).syncTournament(any(), any());
    }

    @Test
    @DisplayName("giải đánh đơn không nhận đăng ký theo đội")
    void singlesRejectsTeams() {
        assertThatThrownBy(() -> service.register(team(UUID.randomUUID(), "Đôi A", UUID.randomUUID())))
                .isInstanceOf(ServiceException.class).hasMessageContaining("cá nhân");
    }

    @Test
    @DisplayName("bóng đá 7 người chỉ nhận đăng ký theo đội 7–14 người")
    void footballRejectsIndividuals() {
        useTournament("FOOTBALL_7", 0D, 8);

        assertThatThrownBy(() -> service.register(individual(UUID.randomUUID())))
                .isInstanceOf(ServiceException.class).hasMessageContaining("theo đội 7–14");
    }

    @Test
    @DisplayName("không đăng ký cá nhân hai lần một giải")
    void duplicateIndividualIsRejected() throws ServiceException {
        UUID player = UUID.randomUUID();
        service.register(individual(player));

        assertThatThrownBy(() -> service.register(individual(player)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đăng ký khác");
    }

    @Test
    @DisplayName("hết suất thì không nhận thêm")
    void fullTournamentRejects() throws ServiceException {
        useTournament("BADMINTON_SINGLES", 0D, 2);
        service.register(individual(UUID.randomUUID()));
        service.register(individual(UUID.randomUUID()));

        assertThatThrownBy(() -> service.register(individual(UUID.randomUUID())))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đủ số lượng");
    }

    @Test
    @DisplayName("điều kiện tuổi xét theo hồ sơ thật của người đăng ký")
    void eligibilityUsesRealProfile() {
        when(eligibilityRuleRepository.findByTournamentId(tournamentId)).thenReturn(List.of(
                TournamentEligibilityRule.builder().ruleType(EligibilityRuleType.AGE)
                        .operator(EligibilityRuleOperator.GREATER_THAN_OR_EQUAL).expectedValue("18").build()));
        when(playerProfileProvider.findProfile(any(), any())).thenReturn(Optional.of(new PlayerProfileProvider.PlayerProfileView(
                UUID.randomUUID(), LocalDate.now().minusYears(15), "MALE", SkillLevel.BEGINNER, 1000)));

        assertThatThrownBy(() -> service.register(individual(UUID.randomUUID())))
                .isInstanceOf(ServiceException.class).hasMessageContaining("Tuổi");
    }

    // ---------- lap doi bang loi moi ----------

    @Test
    @DisplayName("đội đôi: tạo đội chờ đồng đội xác nhận, đồng đội nhận lời thì đủ người và chờ đóng phí")
    void doublesTeamFormsThroughConfirmation() throws ServiceException {
        useTournament("BADMINTON_DOUBLES", 200_000D, 8);
        UUID captain = UUID.randomUUID();
        UUID partner = UUID.randomUUID();

        var created = service.register(team(captain, "Đôi Sấm Sét", partner));
        assertThat(created.getStatus()).isEqualTo(RegistrationStatus.PENDING_MEMBERS);
        assertThat(created.getLineups()).extracting("memberStatus")
                .containsExactlyInAnyOrder(LineupMemberStatus.ACCEPTED, LineupMemberStatus.INVITED);
        verify(eventPublisher).publish(eq(TournamentEventPublisher.TEAM_INVITED), any(), eq(partner), eq(captain), any());
        assertThat(teams.getMyInvitations(partner)).hasSize(1);

        var accepted = teams.respondInvitation(tournamentId, created.getRegistrationId(), partner, true);

        assertThat(accepted.getStatus()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
        assertThat(accepted.getPaymentDeadline()).isNotNull();
        assertThat(teams.getMyInvitations(partner)).isEmpty();
        // Ai thi đấu cũng có hồ sơ môn của giải (chưa có thì tạo ở trình độ thấp nhất) để tính ELO.
        verify(sportRatingPort).ensureProfile(captain, tournament.getSportType());
        verify(sportRatingPort).ensureProfile(partner, tournament.getSportType());
    }

    @Test
    @DisplayName("auth-service lỗi khi tạo hồ sơ thể thao không chặn đăng ký giải")
    void profileServiceOutageDoesNotBlockRegistration() throws ServiceException {
        useTournament("BADMINTON_SINGLES", 0D, 8);
        UUID player = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ServiceException("down")).when(sportRatingPort).ensureProfile(any(), any());

        var response = service.register(individual(player));

        assertThat(response.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        verify(sportRatingPort).ensureProfile(player, tournament.getSportType());
    }

    @Test
    @DisplayName("đồng đội từ chối thì đội vẫn chờ, đội trưởng được báo")
    void declinedInvitationKeepsTeamForming() throws ServiceException {
        useTournament("BADMINTON_DOUBLES", 0D, 8);
        UUID captain = UUID.randomUUID();
        UUID partner = UUID.randomUUID();
        var created = service.register(team(captain, "Đôi B", partner));

        var answered = teams.respondInvitation(tournamentId, created.getRegistrationId(), partner, false);

        assertThat(answered.getStatus()).isEqualTo(RegistrationStatus.PENDING_MEMBERS);
        verify(eventPublisher).publish(eq(TournamentEventPublisher.TEAM_ANSWERED), any(), eq(captain), eq(partner), any());
    }

    @Test
    @DisplayName("người đã ở đội khác không nhận lời vào đội thứ hai; nhận lời một đội thì các lời mời khác tự hủy")
    void playerCannotJoinTwoTeams() throws ServiceException {
        useTournament("BADMINTON_DOUBLES", 0D, 8);
        UUID shared = UUID.randomUUID();
        var first = service.register(team(UUID.randomUUID(), "Đôi 1", shared));
        var second = service.register(team(UUID.randomUUID(), "Đôi 2", shared));

        teams.respondInvitation(tournamentId, first.getRegistrationId(), shared, true);

        assertThat(lineups.values().stream().filter(row -> row.getRegistrationId().equals(second.getRegistrationId())
                && row.getPlayerId().equals(shared)).findFirst().orElseThrow().getMemberStatus())
                .isEqualTo(LineupMemberStatus.DECLINED);
        assertThatThrownBy(() -> service.register(team(UUID.randomUUID(), "Đôi 3", shared)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đăng ký khác");
    }

    @Test
    @DisplayName("mời vượt số người tối đa của hình thức thi đấu bị chặn")
    void inviteCannotExceedRoster() {
        useTournament("BADMINTON_DOUBLES", 0D, 8);

        assertThatThrownBy(() -> service.register(team(UUID.randomUUID(), "Đôi đông", UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(ServiceException.class).hasMessageContaining("tối đa 1 người");
    }

    @Test
    @DisplayName("đội phải có tên, trừ khi đại diện câu lạc bộ")
    void teamNeedsName() {
        useTournament("FOOTBALL_7", 0D, 8);

        assertThatThrownBy(() -> service.register(team(UUID.randomUUID(), " ", UUID.randomUUID())))
                .isInstanceOf(ServiceException.class).hasMessageContaining("tên");
    }

    @Test
    @DisplayName("đội đại diện CLB: chỉ quản lý CLB lập, và chỉ mời thành viên đang hoạt động")
    void clubTeamRules() {
        useTournament("FOOTBALL_7", 0D, 8);
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(Club.builder().clubId(clubId).ownerId(owner)
                .name("CLB Sấm").sportType(SportType.FOOTBALL).active(true).build()));
        when(clubMemberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)).thenReturn(List.of(
                ClubMember.builder().clubId(clubId).userId(member).role(ClubRole.MEMBER).status(ClubMemberStatus.ACTIVE).build()));
        when(clubMemberRepository.findByClubIdAndUserId(clubId, member)).thenReturn(Optional.of(
                ClubMember.builder().clubId(clubId).userId(member).role(ClubRole.MEMBER).status(ClubMemberStatus.ACTIVE).build()));

        RegisterTournamentTeamCommand byMember = team(member, null, owner);
        byMember.setClubId(clubId);
        assertThatThrownBy(() -> service.register(byMember))
                .isInstanceOf(ServiceException.class).hasMessageContaining("Chỉ chủ hoặc quản trị viên");

        RegisterTournamentTeamCommand withOutsider = team(owner, null, member, outsider);
        withOutsider.setClubId(clubId);
        assertThatThrownBy(() -> service.register(withOutsider))
                .isInstanceOf(ServiceException.class).hasMessageContaining("không phải thành viên");
    }

    @Test
    @DisplayName("bỏ thành viên đã xác nhận khiến đội dưới số tối thiểu thì bị chặn")
    void removingBelowMinimumIsBlocked() throws ServiceException {
        useTournament("BADMINTON_DOUBLES", 0D, 8);
        UUID captain = UUID.randomUUID();
        UUID partner = UUID.randomUUID();
        var created = service.register(team(captain, "Đôi C", partner));
        teams.respondInvitation(tournamentId, created.getRegistrationId(), partner, true);

        assertThatThrownBy(() -> teams.removeMember(tournamentId, created.getRegistrationId(), captain, partner))
                .isInstanceOf(ServiceException.class).hasMessageContaining("tối thiểu");
        assertThatThrownBy(() -> teams.removeMember(tournamentId, created.getRegistrationId(), captain, captain))
                .isInstanceOf(ServiceException.class).hasMessageContaining("rút đăng ký");
    }

    @Test
    @DisplayName("đóng đăng ký rồi thì không bỏ thành viên, không rời đội, không rút đội được")
    void rosterIsFrozenOnceRegistrationCloses() throws ServiceException {
        useTournament("BADMINTON_DOUBLES", 0D, 8);
        UUID captain = UUID.randomUUID();
        UUID partner = UUID.randomUUID();
        var created = service.register(team(captain, "Đôi E", partner));
        teams.respondInvitation(tournamentId, created.getRegistrationId(), partner, true);
        tournament.setRegistrationCloseDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> teams.removeMember(tournamentId, created.getRegistrationId(), captain, partner))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đóng đăng ký");
        assertThatThrownBy(() -> teams.removeMember(tournamentId, created.getRegistrationId(), partner, partner))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đóng đăng ký");
        assertThatThrownBy(() -> service.cancel(tournamentId, created.getRegistrationId(), captain, null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đóng đăng ký");
    }

    // ---------- le phi ----------

    @Test
    @DisplayName("checkout: payment-service báo đã trả (lỡ sự kiện) thì xác nhận đăng ký ngay")
    void checkoutConfirmsWhenPaymentAlreadySucceeded() throws ServiceException {
        UUID player = UUID.randomUUID();
        var registration = service.register(individual(player));
        UUID paymentId = UUID.randomUUID();
        when(feePayment.checkout(any(), any(), anyDouble(), anyString(), any())).thenAnswer(call ->
                new TournamentFeePayment.Checkout(paymentId, "SUCCEEDED", null, null, call.getArgument(4)));

        var checkout = service.checkout(tournamentId, registration.getRegistrationId(), player);

        assertThat(checkout.status()).isEqualTo("SUCCEEDED");
        verify(paymentEventService).apply(eq(paymentId), eq(player), eq(registration.getRegistrationId()),
                eq(150_000D), eq("VND"), eq(true), isNull());
    }

    @Test
    @DisplayName("checkout: số tiền lấy từ giải, hạn là hạn giữ suất, chỉ người đăng ký được trả")
    void checkoutUsesServerSidePriceAndDeadline() throws ServiceException {
        UUID player = UUID.randomUUID();
        var registration = service.register(individual(player));
        when(feePayment.checkout(any(), any(), anyDouble(), anyString(), any())).thenAnswer(call ->
                new TournamentFeePayment.Checkout(UUID.randomUUID(), "PENDING", "https://pay/x", "qr", call.getArgument(4)));

        var checkout = service.checkout(tournamentId, registration.getRegistrationId(), player);

        assertThat(checkout.amount()).isEqualTo(150_000D);
        assertThat(checkout.expiresAt()).isEqualTo(registration.getPaymentDeadline());
        verify(feePayment).checkout(eq(registration.getRegistrationId()), eq(player), eq(150_000D), anyString(),
                eq(registration.getPaymentDeadline()));
        assertThatThrownBy(() -> service.checkout(tournamentId, registration.getRegistrationId(), UUID.randomUUID()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("Chỉ người đăng ký");
    }

    @Test
    @DisplayName("quá hạn 24 giờ thì không tạo mã thanh toán nữa")
    void checkoutAfterDeadlineIsRefused() throws ServiceException {
        UUID player = UUID.randomUUID();
        var registration = service.register(individual(player));
        registrations.get(registration.getRegistrationId()).setPaymentDeadline(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.checkout(tournamentId, registration.getRegistrationId(), player))
                .isInstanceOf(ServiceException.class).hasMessageContaining("quá hạn");
    }

    @Test
    @DisplayName("đội chưa đủ người thì chưa đóng phí được")
    void formingTeamCannotPay() throws ServiceException {
        useTournament("BADMINTON_DOUBLES", 200_000D, 8);
        UUID captain = UUID.randomUUID();
        var created = service.register(team(captain, "Đôi D", UUID.randomUUID()));

        assertThatThrownBy(() -> service.checkout(tournamentId, created.getRegistrationId(), captain))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đủ 2 người");
    }

    @Test
    @DisplayName("rút đăng ký đã đóng phí: tự gửi yêu cầu hoàn và báo cho người đăng ký")
    void withdrawingPaidRegistrationRequestsRefund() throws ServiceException {
        UUID player = UUID.randomUUID();
        var registration = service.register(individual(player));
        TournamentPayment payment = payments.get(registration.getRegistrationId());
        payment.setStatus(FeePaymentStatus.SUCCEEDED);
        payment.setPaymentId(UUID.randomUUID());
        registrations.get(registration.getRegistrationId()).setStatus(RegistrationStatus.CONFIRMED);

        service.cancel(tournamentId, registration.getRegistrationId(), player, null);

        TournamentRegistration closed = registrations.get(registration.getRegistrationId());
        assertThat(closed.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(closed.getStatusReason()).contains("đã gửi yêu cầu hoàn 150.000 đ");
        assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.REFUND_REQUESTED);
        verify(feePayment).refund(eq(payment.getPaymentId()), eq(registration.getRegistrationId()), eq(player),
                eq(150_000D), anyString());
        verify(eventPublisher).publish(eq(TournamentEventPublisher.CLOSED), any(), eq(player), any(), any());
    }

    @Test
    @DisplayName("gửi yêu cầu hoàn lỗi lúc rút: job gửi lại tới khi được, không hoàn hai lần")
    void failedRefundRequestIsRetriedLater() throws ServiceException {
        UUID player = UUID.randomUUID();
        var registration = service.register(individual(player));
        TournamentPayment payment = payments.get(registration.getRegistrationId());
        payment.setStatus(FeePaymentStatus.SUCCEEDED);
        payment.setPaymentId(UUID.randomUUID());
        registrations.get(registration.getRegistrationId()).setStatus(RegistrationStatus.CONFIRMED);
        when(paymentRepository.findByStatus(any())).thenAnswer(call -> payments.values().stream()
                .filter(item -> item.getStatus() == call.getArgument(0)).toList());
        when(feePayment.refund(any(), any(), any(), anyDouble(), anyString())).thenReturn(false);
        service.cancel(tournamentId, registration.getRegistrationId(), player, null);
        assertThat(service.findClosedRegistrationsAwaitingRefund()).containsExactly(registration.getRegistrationId());

        when(feePayment.refund(any(), any(), any(), anyDouble(), anyString())).thenReturn(true);
        service.retryRefund(registration.getRegistrationId());

        assertThat(payment.getStatus()).isEqualTo(FeePaymentStatus.REFUND_REQUESTED);
        assertThat(registrations.get(registration.getRegistrationId()).getStatusReason())
                .contains("đã gửi yêu cầu hoàn 150.000 đ").doesNotContain("ban tổ chức sẽ xử lý");
        assertThat(service.findClosedRegistrationsAwaitingRefund()).isEmpty();
    }

    @Test
    @DisplayName("ban tổ chức không từ chối được người tham gia")
    void organizerCannotRejectRegistration() throws ServiceException {
        var registration = service.register(individual(UUID.randomUUID()));

        assertThatThrownBy(() -> service.cancel(tournamentId, registration.getRegistrationId(), organizerId, "Sai hạng cân"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("không thể từ chối");
        assertThat(registrations.get(registration.getRegistrationId()).getStatus()).isNotEqualTo(RegistrationStatus.REJECTED);
    }

    @Test
    @DisplayName("người ngoài không hủy được đăng ký của người khác")
    void strangerCannotCancel() throws ServiceException {
        var registration = service.register(individual(UUID.randomUUID()));

        assertThatThrownBy(() -> service.cancel(tournamentId, registration.getRegistrationId(), UUID.randomUUID(), null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("không có quyền");
    }

    // ---------- het han ----------

    @Test
    @DisplayName("quá hạn đóng phí: job trả suất và ghi lý do")
    void overduePaymentExpires() throws ServiceException {
        var registration = service.register(individual(UUID.randomUUID()));
        TournamentRegistration stored = registrations.get(registration.getRegistrationId());
        stored.setPaymentDeadline(LocalDateTime.now().minusMinutes(5));
        when(registrationRepository.findByStatusAndPaymentDeadlineBefore(eq(RegistrationStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(stored));

        List<UUID> overdue = service.findOverdueRegistrationIds(LocalDateTime.now());
        overdue.forEach(id -> service.expire(id, LocalDateTime.now()));

        assertThat(overdue).containsExactly(stored.getRegistrationId());
        assertThat(stored.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(stored.getStatusReason()).contains("Quá hạn đóng lệ phí");
    }

    @Test
    @DisplayName("đội chưa đủ người khi hết hạn đăng ký thì bị đóng")
    void formingTeamExpiresAfterRegistrationClose() throws ServiceException {
        useTournament("BADMINTON_DOUBLES", 0D, 8);
        var created = service.register(team(UUID.randomUUID(), "Đôi E", UUID.randomUUID()));
        tournament.setRegistrationCloseDate(LocalDate.now().minusDays(1));
        when(registrationRepository.findByStatus(RegistrationStatus.PENDING_MEMBERS))
                .thenReturn(List.of(registrations.get(created.getRegistrationId())));

        service.findOverdueRegistrationIds(LocalDateTime.now()).forEach(id -> service.expire(id, LocalDateTime.now()));

        assertThat(registrations.get(created.getRegistrationId()).getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
    }

    @Test
    @DisplayName("hủy giải: mọi đăng ký giữ suất bị đóng, ai đã đóng phí được hoàn")
    void cancelledTournamentClosesEveryone() throws ServiceException {
        var paid = service.register(individual(UUID.randomUUID()));
        payments.get(paid.getRegistrationId()).setStatus(FeePaymentStatus.SUCCEEDED);
        payments.get(paid.getRegistrationId()).setPaymentId(UUID.randomUUID());
        var unpaid = service.register(individual(UUID.randomUUID()));

        service.closeAllForCancelledTournament(tournament, organizerId);

        assertThat(registrations.get(paid.getRegistrationId()).getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(registrations.get(unpaid.getRegistrationId()).getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(payments.get(paid.getRegistrationId()).getStatus()).isEqualTo(FeePaymentStatus.REFUND_REQUESTED);
    }
}
