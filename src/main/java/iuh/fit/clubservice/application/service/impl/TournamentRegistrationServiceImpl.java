package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand;
import iuh.fit.clubservice.application.dto.response.TournamentRegistrationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.application.port.external.TournamentFeePayment;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentPaymentRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.TournamentPaymentEventService;
import iuh.fit.clubservice.application.service.TournamentRegistrationService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.entity.TournamentPayment;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.LineupRole;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.service.ClubAccessPolicy;
import iuh.fit.clubservice.domain.service.SportParticipationPolicy.PlayFormat;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static iuh.fit.clubservice.application.service.impl.TournamentRegistrationSupport.money;
import static iuh.fit.clubservice.application.service.impl.TournamentRegistrationSupport.normalize;

@Service
@RequiredArgsConstructor
public class TournamentRegistrationServiceImpl implements TournamentRegistrationService {
    private final TournamentRegistrationSupport support;
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentLineupRepository lineupRepository;
    private final TournamentPaymentRepository paymentRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final TournamentFeePayment feePayment;
    /** Lazy: TournamentPaymentEventService phụ thuộc ngược lại service này. */
    private final ObjectProvider<TournamentPaymentEventService> paymentEvents;

    // ---------------------------------------------------------------- đăng ký

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentRegistrationResponseCommand register(RegisterTournamentTeamCommand command) throws ServiceException {
        Tournament tournament = support.lockTournament(command.getTournamentId());
        support.assertRegistrationOpen(tournament, "Giải đấu chưa mở hoặc đã đóng đăng ký.");
        PlayFormat format = support.formatOf(tournament);
        // Hình thức thi đấu (theo môn) quyết định cá nhân hay đội; CLUB là kiểu cũ, nay là đội (TEAM) đại diện CLB.
        RegistrationType type = command.getType() == null
                ? (format.isTeam() ? RegistrationType.TEAM : RegistrationType.INDIVIDUAL)
                : command.getType() == RegistrationType.CLUB ? RegistrationType.TEAM : command.getType();
        if (format.isTeam() && type == RegistrationType.INDIVIDUAL) {
            throw new ServiceException("Giải " + format.label().toLowerCase(Locale.ROOT)
                    + " đăng ký theo đội " + format.rosterMin() + "–" + format.rosterMax() + " người.");
        }
        if (!format.isTeam() && type == RegistrationType.TEAM) {
            throw new ServiceException("Giải đánh đơn chỉ nhận đăng ký cá nhân.");
        }

        List<TournamentRegistration> active = support.seatHolders(tournament.getTournamentId());
        if (active.size() >= tournament.getMaxParticipants()) {
            throw new ServiceException("Giải đấu đã đủ số lượng đăng ký.");
        }
        UUID me = command.getRegisteredBy();
        support.assertFreeToPlay(me, active, null, "Bạn");
        support.ensureProfile(tournament, me);
        support.assertEligible(tournament, me, command.getClubId(), "");

        return type == RegistrationType.INDIVIDUAL
                ? registerIndividual(tournament, command)
                : registerTeam(tournament, format, command, active);
    }

    private TournamentRegistrationResponseCommand registerIndividual(Tournament tournament,
                                                                     RegisterTournamentTeamCommand command) {
        TournamentRegistration registration = registrationRepository.save(TournamentRegistration.builder()
                .tournamentId(tournament.getTournamentId()).type(RegistrationType.INDIVIDUAL)
                .playerId(command.getRegisteredBy()).registeredBy(command.getRegisteredBy())
                .teamName(normalize(command.getTeamName())).skillLevel(command.getSkillLevel())
                .status(RegistrationStatus.PENDING_ELIGIBILITY).registeredAt(LocalDateTime.now()).build());
        support.advanceWhenReady(tournament, registration);
        return support.toResponse(registration);
    }

    private TournamentRegistrationResponseCommand registerTeam(Tournament tournament, PlayFormat format,
                                                               RegisterTournamentTeamCommand command,
                                                               List<TournamentRegistration> active)
            throws ServiceException {
        UUID captain = command.getRegisteredBy();
        List<RegisterTournamentTeamCommand.LineupItem> invitees =
                command.getLineups() == null ? List.of() : command.getLineups();
        TeamInvites.assertValid(invitees, captain, command.getCaptainShirtNumber(), Set.of(), format.rosterMax() - 1);

        Club club = command.getClubId() == null ? null : assertClubTeam(tournament, command.getClubId(), captain, active);
        Set<UUID> clubMembers = club == null ? null : support.activeMembers(club);
        for (RegisterTournamentTeamCommand.LineupItem invitee : invitees) {
            if (clubMembers != null && !clubMembers.contains(invitee.getPlayerId())) {
                throw new ServiceException(TeamInvites.label(invitee) + " không phải thành viên đang hoạt động của câu lạc bộ.");
            }
            support.assertFreeToPlay(invitee.getPlayerId(), active, null, TeamInvites.label(invitee));
        }

        String teamName = normalize(command.getTeamName());
        if (teamName == null && club != null) teamName = club.getName();
        if (teamName == null) throw new ServiceException("Đặt tên cho đội của bạn.");
        if (teamName.length() > 100) throw new ServiceException("Tên đội tối đa 100 ký tự.");

        TournamentRegistration registration = registrationRepository.save(TournamentRegistration.builder()
                .tournamentId(tournament.getTournamentId()).type(RegistrationType.TEAM)
                .clubId(club == null ? null : club.getClubId()).teamName(teamName)
                .registeredBy(captain).skillLevel(command.getSkillLevel())
                .status(RegistrationStatus.PENDING_MEMBERS).registeredAt(LocalDateTime.now()).build());

        List<TournamentLineup> rows = new ArrayList<>();
        rows.add(TournamentLineup.builder().registrationId(registration.getRegistrationId()).playerId(captain)
                .lineupRole(LineupRole.CAPTAIN).shirtNumber(command.getCaptainShirtNumber())
                .memberStatus(LineupMemberStatus.ACCEPTED).respondedAt(LocalDateTime.now()).build());
        invitees.forEach(invitee -> rows.add(TeamInvites.newRow(registration.getRegistrationId(), invitee)));
        lineupRepository.saveAll(rows);

        invitees.forEach(invitee -> support.publish(TournamentEventPublisher.TEAM_INVITED, tournament, registration,
                invitee.getPlayerId(), captain, Map.of()));
        // Lúc tạo chỉ có đội trưởng đã xác nhận; đội chờ đồng đội nhận lời cho đủ số tối thiểu.
        if (format.rosterMin() <= 1) support.advanceWhenReady(tournament, registration);
        return support.toResponse(registration);
    }

    /** Đội đại diện CLB: người lập là chủ/quản trị CLB còn hoạt động, cùng môn, CLB chưa có đội khác trong giải. */
    private Club assertClubTeam(Tournament tournament, UUID clubId, UUID captain, List<TournamentRegistration> active)
            throws ServiceException {
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new ServiceException("Không tìm thấy câu lạc bộ."));
        if (Boolean.FALSE.equals(club.getActive())) {
            throw new ServiceException("Câu lạc bộ đã giải tán, không thể đăng ký giải đấu.");
        }
        ClubMember membership = clubMemberRepository.findByClubIdAndUserId(clubId, captain).orElse(null);
        if (!ClubAccessPolicy.isManager(club, membership, captain)) {
            throw new ServiceException("Chỉ chủ hoặc quản trị viên câu lạc bộ mới lập đội đại diện câu lạc bộ.");
        }
        if (club.getSportType() != null && club.getSportType() != tournament.getSportType()) {
            throw new ServiceException("Câu lạc bộ không cùng môn thể thao với giải đấu.");
        }
        if (active.stream().anyMatch(item -> clubId.equals(item.getClubId()))) {
            throw new ServiceException("Câu lạc bộ này đã có đội tham dự giải.");
        }
        return club;
    }

    // ---------------------------------------------------------------- lệ phí

    /**
     * Tạo mã thanh toán lệ phí. Số tiền và người trả do server quyết định (không nhận từ client), payment-service
     * dùng idempotency key theo registrationId nên bấm "thanh toán lại" không tạo khoản thu thứ hai.
     */
    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public FeeCheckout checkout(UUID tournamentId, UUID registrationId, UUID userId) throws ServiceException {
        Tournament tournament = support.requireTournament(tournamentId);
        TournamentRegistration registration = support.requireRegistration(tournament, registrationId);
        if (!registration.getRegisteredBy().equals(userId)) {
            throw new ServiceException("Chỉ người đăng ký (đội trưởng) mới đóng lệ phí được.");
        }
        if (registration.getStatus() == RegistrationStatus.CONFIRMED) throw new ServiceException("Lệ phí đã được thanh toán.");
        if (registration.getStatus() == RegistrationStatus.PENDING_MEMBERS) {
            throw new ServiceException("Đội cần đủ " + support.formatOf(tournament).rosterMin()
                    + " người xác nhận trước khi đóng lệ phí.");
        }
        if (registration.getStatus() != RegistrationStatus.PENDING_PAYMENT) {
            throw new ServiceException("Đăng ký không ở trạng thái chờ thanh toán.");
        }
        if (TournamentLifecyclePolicy.isRosterLocked(tournament)) {
            throw new ServiceException("Giải đã xếp lịch hoặc đã hủy nên không nhận lệ phí nữa.");
        }
        LocalDateTime deadline = registration.getPaymentDeadline();
        if (deadline == null) {
            deadline = LocalDateTime.now().plus(TournamentLifecyclePolicy.PAYMENT_WINDOW);
            registration.setPaymentDeadline(deadline);
            registrationRepository.save(registration);
        }
        if (!deadline.isAfter(LocalDateTime.now())) throw new ServiceException("Đã quá hạn đóng lệ phí của đăng ký này.");

        double amount = tournament.getEntryFee() == null ? 0D : tournament.getEntryFee();
        TournamentPayment payment = paymentRepository.findByRegistrationId(registrationId)
                .orElseGet(() -> TournamentPayment.builder().registrationId(registrationId).amount(amount)
                        .status(FeePaymentStatus.PENDING).build());
        String name = tournament.getName().length() > 150 ? tournament.getName().substring(0, 150) : tournament.getName();
        TournamentFeePayment.Checkout checkout = feePayment.checkout(registrationId, userId, amount,
                "Lệ phí giải " + name, deadline);
        payment.setAmount(amount);
        payment.setPaymentId(checkout.paymentId());
        payment.setStatus(FeePaymentStatus.PENDING);
        paymentRepository.save(payment);
        if ("SUCCEEDED".equals(checkout.status())) {
            // Tiền đã về nhưng sự kiện thanh toán bị lỡ: xác nhận ngay theo đúng luồng của sự kiện.
            paymentEvents.getObject().apply(checkout.paymentId(), userId, registrationId, amount, "VND", true, null);
        }
        return new FeeCheckout(checkout.paymentId(), checkout.status(), checkout.checkoutUrl(), checkout.qrCodeContent(),
                amount, checkout.expiresAt() == null ? deadline : checkout.expiresAt());
    }

    // ---------------------------------------------------------------- rút / từ chối / hết hạn

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public void cancel(UUID tournamentId, UUID registrationId, UUID actorId, String reason) throws ServiceException {
        Tournament tournament = support.requireTournament(tournamentId);
        TournamentRegistration registration = support.requireRegistration(tournament, registrationId);
        // Chỉ người đăng ký tự rút. Ban tổ chức không từ chối người tham gia: ai đủ điều kiện và đóng phí thì được dự.
        if (!registration.getRegisteredBy().equals(actorId)) {
            throw new ServiceException(tournament.getOrganizerId().equals(actorId)
                    ? "Ban tổ chức không thể từ chối người tham gia."
                    : "Bạn không có quyền hủy đăng ký này.");
        }
        if (!TournamentLifecyclePolicy.holdsSeat(registration.getStatus())) return;
        support.assertRegistrationOpen(tournament, "Giải đã đóng đăng ký nên không rút khỏi giải được.");
        close(tournament, registration, RegistrationStatus.CANCELLED, "Người đăng ký rút khỏi giải", actorId);
    }

    @Override
    public void closeAllForCancelledTournament(Tournament tournament, UUID actorId) {
        for (TournamentRegistration registration : support.seatHolders(tournament.getTournamentId())) {
            close(tournament, registration, RegistrationStatus.CANCELLED, "Giải đấu đã bị hủy", actorId);
        }
    }

    @Override
    public void closeUnfinishedBeforeFixtures(Tournament tournament, UUID actorId) {
        for (TournamentRegistration registration : support.seatHolders(tournament.getTournamentId())) {
            if (registration.getStatus() == RegistrationStatus.PENDING_PAYMENT) {
                close(tournament, registration, RegistrationStatus.CANCELLED,
                        "Chưa hoàn tất lệ phí trước khi ban tổ chức xếp lịch thi đấu", actorId);
            } else if (registration.getStatus() == RegistrationStatus.PENDING_MEMBERS
                    || registration.getStatus() == RegistrationStatus.PENDING_ELIGIBILITY) {
                close(tournament, registration, RegistrationStatus.CANCELLED,
                        "Đội chưa đủ người trước khi ban tổ chức xếp lịch thi đấu", actorId);
            }
        }
    }

    @Override
    public List<UUID> findOverdueRegistrationIds(LocalDateTime now) {
        List<UUID> ids = new ArrayList<>();
        registrationRepository.findByStatusAndPaymentDeadlineBefore(RegistrationStatus.PENDING_PAYMENT, now)
                .forEach(item -> ids.add(item.getRegistrationId()));
        for (TournamentRegistration forming : registrationRepository.findByStatus(RegistrationStatus.PENDING_MEMBERS)) {
            tournamentRepository.findById(forming.getTournamentId())
                    .filter(tournament -> TournamentLifecyclePolicy.isFormingExpired(tournament, now.toLocalDate()))
                    .ifPresent(tournament -> ids.add(forming.getRegistrationId()));
        }
        return ids;
    }

    @Override
    @Transactional
    public void expire(UUID registrationId, LocalDateTime now) {
        TournamentRegistration registration = registrationRepository.findById(registrationId).orElse(null);
        if (registration == null) return;
        Tournament tournament = tournamentRepository.findByIdForUpdate(registration.getTournamentId()).orElse(null);
        if (tournament == null) return;
        // Đọc lại sau khi khóa giải: tiền có thể vừa về giữa lúc job quét và lúc xử lý — khi đó không được đóng nữa.
        registration = registrationRepository.findById(registrationId).orElse(registration);
        if (registration.getStatus() == RegistrationStatus.PENDING_PAYMENT
                && registration.getPaymentDeadline() != null && registration.getPaymentDeadline().isBefore(now)) {
            close(tournament, registration, RegistrationStatus.CANCELLED,
                    "Quá hạn đóng lệ phí (" + TournamentLifecyclePolicy.PAYMENT_WINDOW.toHours()
                            + " giờ), suất đã được trả lại", tournament.getOrganizerId());
        } else if (registration.getStatus() == RegistrationStatus.PENDING_MEMBERS
                && TournamentLifecyclePolicy.isFormingExpired(tournament, now.toLocalDate())) {
            close(tournament, registration, RegistrationStatus.CANCELLED,
                    "Đội chưa đủ " + support.formatOf(tournament).rosterMin() + " người xác nhận trước hạn đăng ký",
                    tournament.getOrganizerId());
        }
    }

    @Override
    public List<UUID> findClosedRegistrationsAwaitingRefund() {
        List<UUID> ids = new ArrayList<>();
        for (TournamentPayment payment : paymentRepository.findByStatus(FeePaymentStatus.SUCCEEDED)) {
            registrationRepository.findById(payment.getRegistrationId())
                    .filter(item -> item.getStatus() == RegistrationStatus.CANCELLED || item.getStatus() == RegistrationStatus.REJECTED)
                    .ifPresent(item -> ids.add(item.getRegistrationId()));
        }
        return ids;
    }

    @Override
    @Transactional
    public void retryRefund(UUID registrationId) {
        TournamentRegistration registration = registrationRepository.findById(registrationId).orElse(null);
        if (registration == null || (registration.getStatus() != RegistrationStatus.CANCELLED
                && registration.getStatus() != RegistrationStatus.REJECTED)) return;
        TournamentPayment payment = paymentRepository.findByRegistrationId(registrationId).orElse(null);
        if (payment == null || payment.getStatus() != FeePaymentStatus.SUCCEEDED || payment.getPaymentId() == null) return;
        UUID requestedBy = tournamentRepository.findById(registration.getTournamentId())
                .map(Tournament::getOrganizerId).orElse(registration.getRegisteredBy());
        String pending = " — cần hoàn lệ phí " + money(payment.getAmount()) + ", ban tổ chức sẽ xử lý.";
        String reason = registration.getStatusReason() == null ? "Đăng ký đã đóng" : registration.getStatusReason();
        if (!feePayment.refund(payment.getPaymentId(), registrationId, requestedBy, payment.getAmount(),
                reason.replace(pending, ""))) {
            return;
        }
        payment.setStatus(FeePaymentStatus.REFUND_REQUESTED);
        paymentRepository.save(payment);
        registration.setStatusReason(reason.replace(pending, " — đã gửi yêu cầu hoàn " + money(payment.getAmount()) + "."));
        registrationRepository.save(registration);
    }

    /**
     * Đóng một đăng ký: đặt trạng thái + lý do, hoàn lệ phí nếu đã thu, đồng bộ chat nếu đang ở trong giải,
     * báo cho người đăng ký và đồng đội. Lỗi hoàn phí không chặn việc đóng — ghi rõ để ban tổ chức xử lý tay.
     */
    private void close(Tournament tournament, TournamentRegistration registration, RegistrationStatus status,
                       String reason, UUID actorId) {
        boolean wasConfirmed = registration.getStatus() == RegistrationStatus.CONFIRMED;
        String fullReason = reason + refundNote(registration, reason, actorId);
        registration.setStatus(status);
        registration.setStatusReason(fullReason);
        registrationRepository.save(registration);
        if (wasConfirmed) support.syncConversation(tournament);

        Set<UUID> receivers = new LinkedHashSet<>();
        receivers.add(registration.getRegisteredBy());
        lineupRepository.findByRegistrationId(registration.getRegistrationId()).stream()
                .filter(row -> row.getMemberStatus() == LineupMemberStatus.ACCEPTED)
                .forEach(row -> receivers.add(row.getPlayerId()));
        receivers.forEach(receiver -> support.publish(TournamentEventPublisher.CLOSED, tournament, registration,
                receiver, actorId, Map.of("reason", fullReason)));
    }

    private String refundNote(TournamentRegistration registration, String reason, UUID actorId) {
        TournamentPayment payment = paymentRepository.findByRegistrationId(registration.getRegistrationId()).orElse(null);
        if (payment == null || payment.getStatus() != FeePaymentStatus.SUCCEEDED || payment.getPaymentId() == null) return "";
        boolean requested = feePayment.refund(payment.getPaymentId(), registration.getRegistrationId(), actorId,
                payment.getAmount(), reason);
        if (!requested) return " — cần hoàn lệ phí " + money(payment.getAmount()) + ", ban tổ chức sẽ xử lý.";
        payment.setStatus(FeePaymentStatus.REFUND_REQUESTED);
        paymentRepository.save(payment);
        return " — đã gửi yêu cầu hoàn " + money(payment.getAmount()) + ".";
    }

    // ---------------------------------------------------------------- đọc / chat

    @Override
    public List<TournamentRegistrationResponseCommand> getTeams(UUID tournamentId) {
        return registrationRepository.findByTournamentId(tournamentId).stream().map(support::toResponse).toList();
    }

    @Override
    public void syncConversation(Tournament tournament) {
        support.syncConversation(tournament);
    }
}
