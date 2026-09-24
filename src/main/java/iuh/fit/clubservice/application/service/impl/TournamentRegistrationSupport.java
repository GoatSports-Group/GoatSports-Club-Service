package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.response.TournamentRegistrationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentEligibilityRuleRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentPaymentRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.entity.TournamentPayment;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.EligibilityRuleType;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.service.EligibilityPolicy;
import iuh.fit.clubservice.domain.service.SportParticipationPolicy;
import iuh.fit.clubservice.domain.service.SportParticipationPolicy.PlayFormat;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Phần dùng chung giữa đăng ký/lệ phí ({@link TournamentRegistrationServiceImpl}) và lập đội
 * ({@link TournamentTeamServiceImpl}): tra cứu có khóa, các điều kiện chặn, chuyển trạng thái khi đủ điều kiện,
 * phát sự kiện và dựng response. Không mở transaction riêng — luôn chạy trong transaction của use case gọi nó.
 */
@Component
@RequiredArgsConstructor
class TournamentRegistrationSupport {
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentLineupRepository lineupRepository;
    private final TournamentPaymentRepository paymentRepository;
    private final TournamentEligibilityRuleRepository eligibilityRuleRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final PlayerProfileProvider playerProfileProvider;
    private final ClubConversationSync conversationSync;
    private final TournamentEventPublisher eventPublisher;

    // ---------------------------------------------------------------- tra cứu

    /**
     * Khóa hàng giải (SELECT ... FOR UPDATE) trước mọi thao tác đổi số suất hoặc đội hình.
     * Thiếu khóa này, hai request song song cùng thấy "còn 1 suất" và cùng lọt — lỗi đã từng xảy ra.
     */
    Tournament lockTournament(UUID tournamentId) throws ServiceException {
        return tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
    }

    Tournament requireTournament(UUID tournamentId) throws ServiceException {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
    }

    TournamentRegistration requireRegistration(Tournament tournament, UUID registrationId) throws ServiceException {
        return registrationRepository.findById(registrationId)
                .filter(item -> item.getTournamentId().equals(tournament.getTournamentId()))
                .orElseThrow(() -> new ServiceException("Không tìm thấy đăng ký giải đấu."));
    }

    List<TournamentRegistration> seatHolders(UUID tournamentId) {
        return registrationRepository.findByTournamentId(tournamentId).stream()
                .filter(item -> TournamentLifecyclePolicy.holdsSeat(item.getStatus())).toList();
    }

    PlayFormat formatOf(Tournament tournament) {
        return SportParticipationPolicy.resolve(tournament.getSportType(), tournament.getPlayFormat());
    }

    TournamentStatus statusOf(Tournament tournament) {
        return TournamentLifecyclePolicy.effectiveStatus(tournament, LocalDate.now());
    }

    long acceptedCount(List<TournamentLineup> lineup) {
        return lineup.stream().filter(row -> row.getMemberStatus() == LineupMemberStatus.ACCEPTED).count();
    }

    /** Thành viên đang hoạt động của CLB, luôn gồm cả chủ CLB (chủ không nằm trong bảng thành viên). */
    Set<UUID> activeMembers(Club club) {
        Set<UUID> members = new HashSet<>();
        clubMemberRepository.findByClubIdAndStatus(club.getClubId(), ClubMemberStatus.ACTIVE)
                .forEach(member -> members.add(member.getUserId()));
        members.add(club.getOwnerId());
        return members;
    }

    // ---------------------------------------------------------------- điều kiện chặn

    void assertRegistrationOpen(Tournament tournament, String message) throws ServiceException {
        if (statusOf(tournament) != TournamentStatus.REGISTRATION_OPEN) throw new ServiceException(message);
    }

    /** Một người chỉ thi đấu cho một đăng ký trong cùng giải: là người đăng ký, hoặc đã nhận lời vào đội. */
    void assertFreeToPlay(UUID userId, List<TournamentRegistration> active, UUID exceptRegistrationId,
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

    /**
     * Hồ sơ lấy từ auth-service, không lấy từ dữ liệu người dùng tự khai: tự khai sẽ cho phép lách điều kiện
     * tuổi/ELO. Số người trong đội do hình thức thi đấu quy định nên bỏ qua luật TEAM_SIZE cũ khi xét từng người.
     */
    void assertEligible(Tournament tournament, UUID userId, UUID clubId, String who) throws ServiceException {
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

    // ---------------------------------------------------------------- chuyển trạng thái

    /**
     * Đăng ký vừa đủ điều kiện (cá nhân hợp lệ, hoặc đội vừa đủ số người tối thiểu):
     * có lệ phí thì giữ suất {@link TournamentLifecyclePolicy#PAYMENT_WINDOW} chờ đóng tiền, miễn phí thì xác nhận ngay.
     */
    void advanceWhenReady(Tournament tournament, TournamentRegistration registration) {
        boolean paid = tournament.getEntryFee() != null && tournament.getEntryFee() > 0D;
        if (paid) {
            LocalDateTime deadline = LocalDateTime.now().plus(TournamentLifecyclePolicy.PAYMENT_WINDOW);
            registration.setStatus(RegistrationStatus.PENDING_PAYMENT);
            registration.setPaymentDeadline(deadline);
            registrationRepository.save(registration);
            if (paymentRepository.findByRegistrationId(registration.getRegistrationId()).isEmpty()) {
                paymentRepository.save(TournamentPayment.builder().registrationId(registration.getRegistrationId())
                        .amount(tournament.getEntryFee()).status(FeePaymentStatus.PENDING).build());
            }
            publish(TournamentEventPublisher.PAYMENT_DUE, tournament, registration, registration.getRegisteredBy(), null,
                    Map.of("amount", money(tournament.getEntryFee()), "deadline", deadline.format(DEADLINE)));
            return;
        }
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setConfirmedAt(LocalDateTime.now());
        registrationRepository.save(registration);
        syncConversation(tournament);
        publish(TournamentEventPublisher.CONFIRMED, tournament, registration, registration.getRegisteredBy(), null, Map.of());
    }

    /**
     * Gửi lại toàn bộ danh sách người tham gia (không thêm/bớt từng người): idempotent và tự sửa được
     * nếu lần đồng bộ trước bị lỗi giữa chừng.
     */
    void syncConversation(Tournament tournament) {
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

    /** Mọi sự kiện đăng ký đều mang registrationId + teamName để notification-service dựng nội dung. */
    void publish(String eventType, Tournament tournament, TournamentRegistration registration, UUID receiverId,
                 UUID actorId, Map<String, String> extra) {
        Map<String, String> payload = new HashMap<>(extra);
        payload.put("registrationId", registration.getRegistrationId().toString());
        payload.put("teamName", String.valueOf(registration.getTeamName()));
        eventPublisher.publish(eventType, tournament, receiverId, actorId, Map.copyOf(payload));
    }

    // ---------------------------------------------------------------- dựng response

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
                        // Dòng đội hình cũ (trước cơ chế mời) không có trạng thái: coi như đã tham gia.
                        .memberStatus(row.getMemberStatus() == null ? LineupMemberStatus.ACCEPTED : row.getMemberStatus())
                        .respondedAt(row.getRespondedAt()).build()).toList())
                .build();
    }

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String money(Double amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount == null ? 0 : amount) + " đ";
    }
}
