package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand;
import iuh.fit.clubservice.application.dto.response.TournamentRegistrationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.application.port.external.TournamentFeePayment;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentEligibilityRuleRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentPaymentRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.TournamentRegistrationService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.entity.TournamentPayment;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.EligibilityRuleType;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.LineupRole;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.service.ClubAccessPolicy;
import iuh.fit.clubservice.domain.service.EligibilityPolicy;
import iuh.fit.clubservice.domain.service.SportParticipationPolicy;
import iuh.fit.clubservice.domain.service.SportParticipationPolicy.PlayFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TournamentRegistrationServiceImpl implements TournamentRegistrationService {
    /** Thoi gian giu suat cho viec dong le phi, tinh tu luc dang ky du dieu kien thanh toan. */
    static final Duration PAYMENT_WINDOW = Duration.ofHours(24);
    static final Set<RegistrationStatus> HOLDING_SEAT = EnumSet.of(RegistrationStatus.PENDING_MEMBERS,
            RegistrationStatus.PENDING_ELIGIBILITY, RegistrationStatus.PENDING_PAYMENT, RegistrationStatus.CONFIRMED);
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentLineupRepository lineupRepository;
    private final TournamentPaymentRepository paymentRepository;
    private final TournamentEligibilityRuleRepository eligibilityRuleRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final PlayerProfileProvider playerProfileProvider;
    private final ClubConversationSync conversationSync;
    private final TournamentFeePayment feePayment;
    private final TournamentEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentRegistrationResponseCommand register(RegisterTournamentTeamCommand command) throws ServiceException {
        Tournament tournament = lockTournament(command.getTournamentId());
        assertRegistrationOpen(tournament);
        PlayFormat format = formatOf(tournament);
        // CLUB la kieu cu: nay la doi (TEAM) dai dien CLB.
        RegistrationType type = command.getType() == null ? defaultType(format)
                : command.getType() == RegistrationType.CLUB ? RegistrationType.TEAM : command.getType();
        if (format.isTeam() && type == RegistrationType.INDIVIDUAL) {
            throw new ServiceException("Giải " + format.label().toLowerCase(Locale.ROOT)
                    + " đăng ký theo đội " + format.rosterMin() + "–" + format.rosterMax() + " người.");
        }
        if (!format.isTeam() && type == RegistrationType.TEAM) {
            throw new ServiceException("Giải đánh đơn chỉ nhận đăng ký cá nhân.");
        }

        List<TournamentRegistration> active = seatHolders(tournament.getTournamentId());
        if (active.size() >= tournament.getMaxParticipants()) {
            throw new ServiceException("Giải đấu đã đủ số lượng đăng ký.");
        }
        UUID me = command.getRegisteredBy();
        assertFreeToPlay(me, active, null, "Bạn");
        assertEligible(tournament, me, command.getClubId(), "");

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
        advanceWhenReady(tournament, registration);
        return toResponse(registration);
    }

    private TournamentRegistrationResponseCommand registerTeam(Tournament tournament, PlayFormat format,
                                                               RegisterTournamentTeamCommand command,
                                                               List<TournamentRegistration> active)
            throws ServiceException {
        UUID captain = command.getRegisteredBy();
        List<RegisterTournamentTeamCommand.LineupItem> invitees =
                command.getLineups() == null ? List.of() : command.getLineups();
        assertInviteList(invitees, captain, command.getCaptainShirtNumber(), Set.of(), format.rosterMax() - 1);

        Club club = command.getClubId() == null ? null : assertClubTeam(tournament, command.getClubId(), captain, active);
        Set<UUID> clubMembers = club == null ? null : activeMembers(club);
        for (RegisterTournamentTeamCommand.LineupItem invitee : invitees) {
            if (clubMembers != null && !clubMembers.contains(invitee.getPlayerId())) {
                throw new ServiceException(label(invitee) + " không phải thành viên đang hoạt động của câu lạc bộ.");
            }
            assertFreeToPlay(invitee.getPlayerId(), active, null, label(invitee));
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
        invitees.forEach(invitee -> rows.add(inviteRow(registration.getRegistrationId(), invitee)));
        lineupRepository.saveAll(rows);

        invitees.forEach(invitee -> eventPublisher.publish(TournamentEventPublisher.TEAM_INVITED, tournament,
                invitee.getPlayerId(), captain, Map.of("registrationId", registration.getRegistrationId().toString(),
                        "teamName", registration.getTeamName())));
        // Luc tao chi co doi truong da xac nhan; doi cho dong doi nhan loi cho du so toi thieu.
        if (format.rosterMin() <= 1) advanceWhenReady(tournament, registration);
        return toResponse(registration);
    }

    // ---------------------------------------------------------------- loi moi vao doi

    @Override
    public List<TeamInvitation> getMyInvitations(UUID userId) {
        List<TeamInvitation> result = new ArrayList<>();
        for (TournamentLineup invite : lineupRepository.findByPlayerIdAndMemberStatus(userId, LineupMemberStatus.INVITED)) {
            Optional<TournamentRegistration> registration = registrationRepository.findById(invite.getRegistrationId())
                    .filter(item -> HOLDING_SEAT.contains(item.getStatus()));
            if (registration.isEmpty()) continue;
            Optional<Tournament> tournament = tournamentRepository.findById(registration.get().getTournamentId());
            if (tournament.isEmpty() || effectiveStatus(tournament.get()) != TournamentStatus.REGISTRATION_OPEN) continue;
            PlayFormat format = formatOf(tournament.get());
            int accepted = (int) lineupRepository.findByRegistrationId(invite.getRegistrationId()).stream()
                    .filter(row -> row.getMemberStatus() == LineupMemberStatus.ACCEPTED).count();
            result.add(new TeamInvitation(tournament.get().getTournamentId(), tournament.get().getName(),
                    tournament.get().getSportType(), tournament.get().getStartDate(),
                    tournament.get().getRegistrationCloseDate(), invite.getRegistrationId(),
                    registration.get().getTeamName(), registration.get().getRegisteredBy(), registration.get().getClubId(),
                    invite.getLineupRole(), invite.getShirtNumber(), accepted, format.rosterMin(), format.rosterMax()));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentRegistrationResponseCommand respondInvitation(UUID tournamentId, UUID registrationId, UUID userId,
                                                                   boolean accept) throws ServiceException {
        Tournament tournament = lockTournament(tournamentId);
        TournamentRegistration registration = requireRegistration(tournament, registrationId);
        List<TournamentLineup> lineup = lineupRepository.findByRegistrationId(registrationId);
        TournamentLineup mine = lineup.stream()
                .filter(row -> userId.equals(row.getPlayerId()) && row.getMemberStatus() == LineupMemberStatus.INVITED)
                .findFirst().orElseThrow(() -> new ServiceException("Bạn không có lời mời đang chờ trong đội này."));
        if (!HOLDING_SEAT.contains(registration.getStatus())) {
            throw new ServiceException("Đội này không còn tham gia giải.");
        }

        if (!accept) {
            mine.setMemberStatus(LineupMemberStatus.DECLINED);
            mine.setRespondedAt(LocalDateTime.now());
            lineupRepository.save(mine);
            eventPublisher.publish(TournamentEventPublisher.TEAM_ANSWERED, tournament, registration.getRegisteredBy(),
                    userId, Map.of("registrationId", registrationId.toString(), "teamName", registration.getTeamName(),
                            "accepted", "false"));
            return toResponse(registration);
        }

        if (effectiveStatus(tournament) != TournamentStatus.REGISTRATION_OPEN) {
            throw new ServiceException("Giải đã đóng đăng ký nên không nhận thêm thành viên.");
        }
        PlayFormat format = formatOf(tournament);
        long accepted = lineup.stream().filter(row -> row.getMemberStatus() == LineupMemberStatus.ACCEPTED).count();
        if (accepted >= format.rosterMax()) {
            throw new ServiceException("Đội đã đủ tối đa " + format.rosterMax() + " người.");
        }
        assertFreeToPlay(userId, seatHolders(tournamentId), registrationId, "Bạn");
        if (registration.getClubId() != null) {
            Club club = clubRepository.findById(registration.getClubId()).orElse(null);
            if (club == null || !activeMembers(club).contains(userId)) {
                throw new ServiceException("Bạn không còn là thành viên của câu lạc bộ đại diện đội này.");
            }
        }
        assertEligible(tournament, userId, registration.getClubId(), "");

        mine.setMemberStatus(LineupMemberStatus.ACCEPTED);
        mine.setRespondedAt(LocalDateTime.now());
        lineupRepository.save(mine);
        declineOtherInvitations(tournament, userId, registrationId);
        eventPublisher.publish(TournamentEventPublisher.TEAM_ANSWERED, tournament, registration.getRegisteredBy(), userId,
                Map.of("registrationId", registrationId.toString(), "teamName", registration.getTeamName(),
                        "accepted", "true"));

        if (registration.getStatus() == RegistrationStatus.PENDING_MEMBERS && accepted + 1 >= format.rosterMin()) {
            advanceWhenReady(tournament, registration);
        } else if (registration.getStatus() == RegistrationStatus.CONFIRMED) {
            syncConversation(tournament);
        }
        return toResponse(registration);
    }

    /** Da vao mot doi thi cac loi moi khac cung giai khong con y nghia. */
    private void declineOtherInvitations(Tournament tournament, UUID userId, UUID keepRegistrationId) {
        for (TournamentLineup other : lineupRepository.findByPlayerIdAndMemberStatus(userId, LineupMemberStatus.INVITED)) {
            if (other.getRegistrationId().equals(keepRegistrationId)) continue;
            boolean sameTournament = registrationRepository.findById(other.getRegistrationId())
                    .map(item -> item.getTournamentId().equals(tournament.getTournamentId())).orElse(false);
            if (!sameTournament) continue;
            other.setMemberStatus(LineupMemberStatus.DECLINED);
            other.setRespondedAt(LocalDateTime.now());
            lineupRepository.save(other);
        }
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentRegistrationResponseCommand inviteMembers(UUID tournamentId, UUID registrationId, UUID captainId,
                                                               List<RegisterTournamentTeamCommand.LineupItem> invitees)
            throws ServiceException {
        Tournament tournament = lockTournament(tournamentId);
        TournamentRegistration registration = requireRegistration(tournament, registrationId);
        assertCaptainManagesTeam(registration, captainId);
        if (effectiveStatus(tournament) != TournamentStatus.REGISTRATION_OPEN) {
            throw new ServiceException("Giải đã đóng đăng ký nên không mời thêm được.");
        }
        if (invitees == null || invitees.isEmpty()) throw new ServiceException("Chọn ít nhất một người để mời.");
        PlayFormat format = formatOf(tournament);
        List<TournamentLineup> lineup = lineupRepository.findByRegistrationId(registrationId);
        Map<UUID, TournamentLineup> byPlayer = new HashMap<>();
        lineup.forEach(row -> byPlayer.put(row.getPlayerId(), row));
        long taken = lineup.stream().filter(row -> row.getMemberStatus() != LineupMemberStatus.DECLINED).count();
        Set<Integer> usedShirts = new HashSet<>();
        lineup.stream().filter(row -> row.getMemberStatus() != LineupMemberStatus.DECLINED && row.getShirtNumber() != null)
                .forEach(row -> usedShirts.add(row.getShirtNumber()));
        assertInviteList(invitees, captainId, null, usedShirts, (int) (format.rosterMax() - taken));

        List<TournamentRegistration> active = seatHolders(tournamentId);
        Set<UUID> clubMembers = registration.getClubId() == null ? null
                : clubRepository.findById(registration.getClubId()).map(this::activeMembers).orElse(Set.of());
        List<TournamentLineup> changed = new ArrayList<>();
        for (RegisterTournamentTeamCommand.LineupItem invitee : invitees) {
            TournamentLineup existing = byPlayer.get(invitee.getPlayerId());
            if (existing != null && existing.getMemberStatus() != LineupMemberStatus.DECLINED) {
                throw new ServiceException(label(invitee) + " đã có trong đội hoặc đang được mời.");
            }
            if (clubMembers != null && !clubMembers.contains(invitee.getPlayerId())) {
                throw new ServiceException(label(invitee) + " không phải thành viên đang hoạt động của câu lạc bộ.");
            }
            assertFreeToPlay(invitee.getPlayerId(), active, null, label(invitee));
            if (existing != null) {
                // Nguoi da tu choi duoc moi lai: dung lai dong cu (unique registration + player).
                existing.setMemberStatus(LineupMemberStatus.INVITED);
                existing.setRespondedAt(null);
                existing.setLineupRole(inviteRole(invitee));
                existing.setShirtNumber(invitee.getShirtNumber());
                existing.setPlayerName(invitee.getPlayerName());
                changed.add(existing);
            } else {
                changed.add(inviteRow(registrationId, invitee));
            }
        }
        lineupRepository.saveAll(changed);
        invitees.forEach(invitee -> eventPublisher.publish(TournamentEventPublisher.TEAM_INVITED, tournament,
                invitee.getPlayerId(), captainId, Map.of("registrationId", registrationId.toString(),
                        "teamName", registration.getTeamName())));
        return toResponse(registration);
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentRegistrationResponseCommand removeMember(UUID tournamentId, UUID registrationId, UUID actorId,
                                                              UUID playerId) throws ServiceException {
        Tournament tournament = lockTournament(tournamentId);
        TournamentRegistration registration = requireRegistration(tournament, registrationId);
        boolean captain = registration.getRegisteredBy().equals(actorId);
        boolean leaving = actorId.equals(playerId);
        if (!captain && !leaving) throw new ServiceException("Chỉ đội trưởng mới bỏ được thành viên khỏi đội.");
        if (captain && leaving) throw new ServiceException("Đội trưởng không rời đội được; hãy rút đăng ký của đội.");
        if (!HOLDING_SEAT.contains(registration.getStatus())) throw new ServiceException("Đội này không còn tham gia giải.");
        if (isStarted(tournament)) throw new ServiceException("Giải đã bắt đầu nên không đổi đội hình được.");

        List<TournamentLineup> lineup = lineupRepository.findByRegistrationId(registrationId);
        TournamentLineup target = lineup.stream()
                .filter(row -> playerId.equals(row.getPlayerId()) && row.getMemberStatus() != LineupMemberStatus.DECLINED)
                .findFirst().orElseThrow(() -> new ServiceException("Người này không có trong đội."));
        if (target.getMemberStatus() == LineupMemberStatus.ACCEPTED
                && (registration.getStatus() == RegistrationStatus.PENDING_PAYMENT
                || registration.getStatus() == RegistrationStatus.CONFIRMED)) {
            PlayFormat format = formatOf(tournament);
            long remaining = lineup.stream().filter(row -> row.getMemberStatus() == LineupMemberStatus.ACCEPTED).count() - 1;
            if (remaining < format.rosterMin()) {
                throw new ServiceException("Đội sẽ chỉ còn " + remaining + "/" + format.rosterMin()
                        + " người tối thiểu. Mời người thay thế trước khi bỏ bớt.");
            }
        }
        lineupRepository.deleteById(target.getLineupId());
        if (registration.getStatus() == RegistrationStatus.CONFIRMED) syncConversation(tournament);
        return toResponse(registration);
    }

    // ---------------------------------------------------------------- le phi

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public FeeCheckout checkout(UUID tournamentId, UUID registrationId, UUID userId) throws ServiceException {
        Tournament tournament = requireTournament(tournamentId);
        TournamentRegistration registration = requireRegistration(tournament, registrationId);
        if (!registration.getRegisteredBy().equals(userId)) {
            throw new ServiceException("Chỉ người đăng ký (đội trưởng) mới đóng lệ phí được.");
        }
        if (registration.getStatus() == RegistrationStatus.CONFIRMED) throw new ServiceException("Lệ phí đã được thanh toán.");
        if (registration.getStatus() == RegistrationStatus.PENDING_MEMBERS) {
            throw new ServiceException("Đội cần đủ " + formatOf(tournament).rosterMin() + " người xác nhận trước khi đóng lệ phí.");
        }
        if (registration.getStatus() != RegistrationStatus.PENDING_PAYMENT) {
            throw new ServiceException("Đăng ký không ở trạng thái chờ thanh toán.");
        }
        if (isStarted(tournament) || tournament.getStatus() == TournamentStatus.CANCELLED) {
            throw new ServiceException("Giải đã xếp lịch hoặc đã hủy nên không nhận lệ phí nữa.");
        }
        LocalDateTime deadline = registration.getPaymentDeadline();
        if (deadline == null) {
            deadline = LocalDateTime.now().plus(PAYMENT_WINDOW);
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
        return new FeeCheckout(checkout.paymentId(), checkout.checkoutUrl(), checkout.qrCodeContent(), amount,
                checkout.expiresAt() == null ? deadline : checkout.expiresAt());
    }

    // ---------------------------------------------------------------- rut / tu choi / het han

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public void cancel(UUID tournamentId, UUID registrationId, UUID actorId, String reason) throws ServiceException {
        Tournament tournament = requireTournament(tournamentId);
        TournamentRegistration registration = requireRegistration(tournament, registrationId);
        boolean registrant = registration.getRegisteredBy().equals(actorId);
        boolean organizer = tournament.getOrganizerId().equals(actorId);
        if (!registrant && !organizer) throw new ServiceException("Bạn không có quyền hủy đăng ký này.");
        if (!HOLDING_SEAT.contains(registration.getStatus())) return;
        if (isStarted(tournament)) throw new ServiceException("Không thể hủy đăng ký khi giải đấu đã bắt đầu.");
        String note = normalize(reason);
        close(tournament, registration,
                registrant ? RegistrationStatus.CANCELLED : RegistrationStatus.REJECTED,
                registrant ? "Người đăng ký rút khỏi giải" : "Ban tổ chức từ chối" + (note == null ? "" : ": " + note),
                actorId);
    }

    @Override
    public void closeAllForCancelledTournament(Tournament tournament, UUID actorId) {
        for (TournamentRegistration registration : seatHolders(tournament.getTournamentId())) {
            close(tournament, registration, RegistrationStatus.CANCELLED, "Giải đấu đã bị hủy", actorId);
        }
    }

    @Override
    public void closeUnfinishedBeforeFixtures(Tournament tournament, UUID actorId) {
        for (TournamentRegistration registration : seatHolders(tournament.getTournamentId())) {
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
                    .filter(tournament -> formingExpired(tournament, now.toLocalDate()))
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
        // Doc lai sau khi khoa giai: tien co the vua ve giua luc job quet va luc xu ly.
        registration = registrationRepository.findById(registrationId).orElse(registration);
        if (registration.getStatus() == RegistrationStatus.PENDING_PAYMENT
                && registration.getPaymentDeadline() != null && registration.getPaymentDeadline().isBefore(now)) {
            close(tournament, registration, RegistrationStatus.CANCELLED,
                    "Quá hạn đóng lệ phí (" + PAYMENT_WINDOW.toHours() + " giờ), suất đã được trả lại", tournament.getOrganizerId());
        } else if (registration.getStatus() == RegistrationStatus.PENDING_MEMBERS
                && formingExpired(tournament, now.toLocalDate())) {
            close(tournament, registration, RegistrationStatus.CANCELLED,
                    "Đội chưa đủ " + formatOf(tournament).rosterMin() + " người xác nhận trước hạn đăng ký",
                    tournament.getOrganizerId());
        }
    }

    private boolean formingExpired(Tournament tournament, LocalDate today) {
        return today.isAfter(tournament.getRegistrationCloseDate()) || isStarted(tournament)
                || tournament.getStatus() == TournamentStatus.CANCELLED;
    }

    /**
     * Dong mot dang ky: dat trang thai + ly do, hoan le phi neu da thu, dong bo chat neu dang o trong giai,
     * bao cho nguoi dang ky va dong doi. Loi hoan phi khong chan viec dong (ghi ro de ban to chuc xu ly tay).
     */
    private void close(Tournament tournament, TournamentRegistration registration, RegistrationStatus status,
                       String reason, UUID actorId) {
        boolean wasConfirmed = registration.getStatus() == RegistrationStatus.CONFIRMED;
        String fullReason = reason + refundNote(registration, reason, actorId);
        registration.setStatus(status);
        registration.setStatusReason(fullReason);
        registrationRepository.save(registration);
        if (wasConfirmed) syncConversation(tournament);

        Set<UUID> receivers = new LinkedHashSet<>();
        receivers.add(registration.getRegisteredBy());
        lineupRepository.findByRegistrationId(registration.getRegistrationId()).stream()
                .filter(row -> row.getMemberStatus() == LineupMemberStatus.ACCEPTED)
                .forEach(row -> receivers.add(row.getPlayerId()));
        receivers.forEach(receiver -> eventPublisher.publish(TournamentEventPublisher.CLOSED, tournament, receiver, actorId,
                Map.of("registrationId", registration.getRegistrationId().toString(),
                        "teamName", String.valueOf(registration.getTeamName()), "reason", fullReason)));
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

    // ---------------------------------------------------------------- doc / chat

    @Override
    public List<TournamentRegistrationResponseCommand> getTeams(UUID tournamentId) {
        return registrationRepository.findByTournamentId(tournamentId).stream().map(this::toResponse).toList();
    }

    @Override
    public void syncConversation(Tournament tournament) {
        Set<UUID> participants = new LinkedHashSet<>();
        for (TournamentRegistration registration : registrationRepository
                .findByTournamentIdAndStatus(tournament.getTournamentId(), RegistrationStatus.CONFIRMED)) {
            participants.add(registration.getRegisteredBy());
            lineupRepository.findByRegistrationId(registration.getRegistrationId()).stream()
                    .filter(row -> row.getMemberStatus() == null || row.getMemberStatus() == LineupMemberStatus.ACCEPTED)
                    .forEach(row -> participants.add(row.getPlayerId()));
        }
        participants.remove(null);
        conversationSync.syncTournament(tournament, new ArrayList<>(participants));
    }

    // ---------------------------------------------------------------- quy tac dung chung

    /** Du dieu kien thanh toan: co phi thi giu suat 24 gio cho dong phi, mien phi thi xac nhan ngay. */
    private void advanceWhenReady(Tournament tournament, TournamentRegistration registration) {
        boolean paid = tournament.getEntryFee() != null && tournament.getEntryFee() > 0D;
        if (paid) {
            LocalDateTime deadline = LocalDateTime.now().plus(PAYMENT_WINDOW);
            registration.setStatus(RegistrationStatus.PENDING_PAYMENT);
            registration.setPaymentDeadline(deadline);
            registrationRepository.save(registration);
            if (paymentRepository.findByRegistrationId(registration.getRegistrationId()).isEmpty()) {
                paymentRepository.save(TournamentPayment.builder().registrationId(registration.getRegistrationId())
                        .amount(tournament.getEntryFee()).status(FeePaymentStatus.PENDING).build());
            }
            eventPublisher.publish(TournamentEventPublisher.PAYMENT_DUE, tournament, registration.getRegisteredBy(), null,
                    Map.of("registrationId", registration.getRegistrationId().toString(),
                            "teamName", String.valueOf(registration.getTeamName()),
                            "amount", money(tournament.getEntryFee()), "deadline", deadline.format(DEADLINE)));
            return;
        }
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setConfirmedAt(LocalDateTime.now());
        registrationRepository.save(registration);
        syncConversation(tournament);
        eventPublisher.publish(TournamentEventPublisher.CONFIRMED, tournament, registration.getRegisteredBy(), null,
                Map.of("registrationId", registration.getRegistrationId().toString(),
                        "teamName", String.valueOf(registration.getTeamName())));
    }

    /** Mot nguoi chi thi dau cho mot dang ky trong cung giai: la nguoi dang ky, hoac da nhan loi vao doi. */
    private void assertFreeToPlay(UUID userId, List<TournamentRegistration> active, UUID exceptRegistrationId,
                                  String who) throws ServiceException {
        for (TournamentRegistration registration : active) {
            if (registration.getRegistrationId().equals(exceptRegistrationId)) continue;
            boolean registrant = userId.equals(registration.getRegisteredBy()) || userId.equals(registration.getPlayerId());
            boolean member = registration.getType() != RegistrationType.INDIVIDUAL
                    && lineupRepository.findByRegistrationId(registration.getRegistrationId()).stream()
                    .anyMatch(row -> userId.equals(row.getPlayerId())
                            && (row.getMemberStatus() == null || row.getMemberStatus() == LineupMemberStatus.ACCEPTED));
            if (registrant || member) {
                throw new ServiceException(who + " đã tham gia giải này trong một đăng ký khác.");
            }
        }
    }

    private void assertInviteList(List<RegisterTournamentTeamCommand.LineupItem> invitees, UUID captain,
                                  Integer captainShirt, Set<Integer> usedShirts, int slotsLeft) throws ServiceException {
        if (invitees.size() > slotsLeft) {
            throw new ServiceException(slotsLeft <= 0 ? "Đội đã đủ số người tối đa."
                    : "Chỉ mời thêm được tối đa " + slotsLeft + " người.");
        }
        Set<UUID> players = new HashSet<>();
        Set<Integer> shirts = new HashSet<>(usedShirts);
        if (captainShirt != null) shirts.add(captainShirt);
        for (RegisterTournamentTeamCommand.LineupItem invitee : invitees) {
            if (invitee.getPlayerId() == null) throw new ServiceException("Mỗi lời mời phải chọn một người chơi.");
            if (invitee.getPlayerId().equals(captain)) throw new ServiceException("Bạn đã là đội trưởng, không cần tự mời.");
            if (!players.add(invitee.getPlayerId())) throw new ServiceException(label(invitee) + " bị mời hai lần.");
            if (invitee.getShirtNumber() != null) {
                if (invitee.getShirtNumber() < 0 || invitee.getShirtNumber() > 999) {
                    throw new ServiceException("Số áo từ 0 đến 999.");
                }
                if (!shirts.add(invitee.getShirtNumber())) {
                    throw new ServiceException("Số áo " + invitee.getShirtNumber() + " bị trùng trong đội.");
                }
            }
        }
    }

    /** Doi dai dien CLB: nguoi lap la chu/quan tri CLB con hoat dong, cung mon, CLB chua co doi khac trong giai. */
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

    private Set<UUID> activeMembers(Club club) {
        Set<UUID> members = new HashSet<>();
        clubMemberRepository.findByClubIdAndStatus(club.getClubId(), ClubMemberStatus.ACTIVE)
                .forEach(member -> members.add(member.getUserId()));
        members.add(club.getOwnerId());
        return members;
    }

    private void assertCaptainManagesTeam(TournamentRegistration registration, UUID captainId) throws ServiceException {
        if (registration.getType() == RegistrationType.INDIVIDUAL) throw new ServiceException("Đăng ký cá nhân không có đội.");
        if (!registration.getRegisteredBy().equals(captainId)) {
            throw new ServiceException("Chỉ đội trưởng mới mời được thành viên.");
        }
        if (!HOLDING_SEAT.contains(registration.getStatus())) throw new ServiceException("Đội này không còn tham gia giải.");
    }

    /**
     * Ho so lay tu auth-service, khong lay tu du lieu tu khai: tu khai se cho phep lach dieu kien tuoi/ELO.
     * So nguoi doi hinh do hinh thuc thi dau quy dinh, nen bo qua luat TEAM_SIZE cu khi xet tung nguoi.
     */
    private void assertEligible(Tournament tournament, UUID userId, UUID clubId, String who) throws ServiceException {
        List<TournamentEligibilityRule> rules = eligibilityRuleRepository.findByTournamentId(tournament.getTournamentId())
                .stream().filter(rule -> rule.getRuleType() != EligibilityRuleType.TEAM_SIZE).toList();
        if (rules.isEmpty()) return;
        boolean needsProfile = rules.stream().anyMatch(rule -> rule.getRuleType() != EligibilityRuleType.CLUB_MEMBERSHIP);
        PlayerProfileProvider.PlayerProfileView profile = needsProfile
                ? playerProfileProvider.findProfile(userId, tournament.getSportType()).orElse(null) : null;
        Integer age = profile == null || profile.dateOfBirth() == null ? null
                : Period.between(profile.dateOfBirth(), LocalDate.now()).getYears();
        EligibilityPolicy.Candidate candidate = new EligibilityPolicy.Candidate(age,
                profile == null ? null : profile.gender(), profile == null ? null : profile.skillLevel(),
                profile == null ? null : profile.eloRating(), clubId, 0);
        Optional<String> violation = EligibilityPolicy.firstViolation(rules, candidate);
        if (violation.isPresent()) throw new ServiceException(who + violation.get());
    }

    private TournamentLineup inviteRow(UUID registrationId, RegisterTournamentTeamCommand.LineupItem invitee) {
        return TournamentLineup.builder().registrationId(registrationId).playerId(invitee.getPlayerId())
                .playerName(normalize(invitee.getPlayerName())).lineupRole(inviteRole(invitee))
                .shirtNumber(invitee.getShirtNumber()).memberStatus(LineupMemberStatus.INVITED).build();
    }

    /** Doi truong la nguoi lap doi; nguoi duoc moi chi la thi dau hoac du bi. */
    private LineupRole inviteRole(RegisterTournamentTeamCommand.LineupItem invitee) {
        return invitee.getLineupRole() == LineupRole.SUBSTITUTE ? LineupRole.SUBSTITUTE : LineupRole.PLAYER;
    }

    private String label(RegisterTournamentTeamCommand.LineupItem invitee) {
        String name = normalize(invitee.getPlayerName());
        return name == null ? "Người được mời" : name;
    }

    private RegistrationType defaultType(PlayFormat format) {
        return format.isTeam() ? RegistrationType.TEAM : RegistrationType.INDIVIDUAL;
    }

    private PlayFormat formatOf(Tournament tournament) {
        return SportParticipationPolicy.resolve(tournament.getSportType(), tournament.getPlayFormat());
    }

    private void assertRegistrationOpen(Tournament tournament) throws ServiceException {
        if (effectiveStatus(tournament) != TournamentStatus.REGISTRATION_OPEN) {
            throw new ServiceException("Giải đấu chưa mở hoặc đã đóng đăng ký.");
        }
    }

    private boolean isStarted(Tournament tournament) {
        return tournament.getStatus() == TournamentStatus.IN_PROGRESS || tournament.getStatus() == TournamentStatus.COMPLETED;
    }

    private List<TournamentRegistration> seatHolders(UUID tournamentId) {
        return registrationRepository.findByTournamentId(tournamentId).stream()
                .filter(item -> HOLDING_SEAT.contains(item.getStatus())).toList();
    }

    private Tournament lockTournament(UUID tournamentId) throws ServiceException {
        return tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
    }

    private Tournament requireTournament(UUID tournamentId) throws ServiceException {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
    }

    private TournamentRegistration requireRegistration(Tournament tournament, UUID registrationId) throws ServiceException {
        return registrationRepository.findById(registrationId)
                .filter(item -> item.getTournamentId().equals(tournament.getTournamentId()))
                .orElseThrow(() -> new ServiceException("Không tìm thấy đăng ký giải đấu."));
    }

    static TournamentStatus effectiveStatus(Tournament tournament) {
        TournamentStatus stored = tournament.getStatus();
        if (stored == TournamentStatus.DRAFT || stored == TournamentStatus.CANCELLED
                || stored == TournamentStatus.IN_PROGRESS || stored == TournamentStatus.COMPLETED) return stored;
        LocalDate today = LocalDate.now();
        if (today.isBefore(tournament.getRegistrationOpenDate())) return TournamentStatus.PUBLISHED;
        if (!today.isAfter(tournament.getRegistrationCloseDate())) return TournamentStatus.REGISTRATION_OPEN;
        return TournamentStatus.REGISTRATION_CLOSED;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String money(Double amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount == null ? 0 : amount) + " đ";
    }

    TournamentRegistrationResponseCommand toResponse(TournamentRegistration registration) {
        List<TournamentLineup> lineup = lineupRepository.findByRegistrationId(registration.getRegistrationId());
        TournamentPayment payment = paymentRepository.findByRegistrationId(registration.getRegistrationId()).orElse(null);
        return TournamentRegistrationResponseCommand.builder().registrationId(registration.getRegistrationId())
                .tournamentId(registration.getTournamentId()).type(registration.getType())
                .playerId(registration.getPlayerId()).clubId(registration.getClubId())
                .teamName(registration.getTeamName()).registeredBy(registration.getRegisteredBy())
                .skillLevel(registration.getSkillLevel()).status(registration.getStatus())
                .statusReason(registration.getStatusReason()).registeredAt(registration.getRegisteredAt())
                .confirmedAt(registration.getConfirmedAt()).paymentDeadline(registration.getPaymentDeadline())
                .paymentStatus(payment == null ? null : payment.getStatus())
                .feeAmount(payment == null ? null : payment.getAmount())
                .lineups(lineup.stream().map(row -> TournamentRegistrationResponseCommand.LineupResponseItem.builder()
                        .lineupId(row.getLineupId()).playerId(row.getPlayerId()).playerName(row.getPlayerName())
                        .lineupRole(row.getLineupRole()).shirtNumber(row.getShirtNumber())
                        .memberStatus(row.getMemberStatus() == null ? LineupMemberStatus.ACCEPTED : row.getMemberStatus())
                        .respondedAt(row.getRespondedAt()).build()).toList())
                .build();
    }
}
