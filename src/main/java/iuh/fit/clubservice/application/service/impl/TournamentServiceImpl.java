package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.*;
import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.VenueCourtHold;
import iuh.fit.clubservice.application.port.persistence.*;
import iuh.fit.clubservice.application.service.TournamentRegistrationService;
import iuh.fit.clubservice.application.service.TournamentService;
import iuh.fit.clubservice.domain.entity.*;
import iuh.fit.clubservice.domain.enums.*;
import iuh.fit.clubservice.domain.service.SportParticipationPolicy;
import iuh.fit.clubservice.domain.service.TournamentCapacityPolicy;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import iuh.fit.clubservice.domain.service.ChampionPolicy;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.domain.enums.SportType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentFixtureRepository fixtureRepository;
    private final TournamentEligibilityRuleRepository eligibilityRuleRepository;
    private final TournamentVenueReservationRepository reservationRepository;
    private final VenueCourtHold venueCourtHold;
    private final TournamentRegistrationService registrationService;
    private final TournamentLineupRepository lineupRepository;
    private final PlayerProfileProvider playerProfileProvider;

    @Override
    public Page<TournamentResponseCommand> searchTournaments(SportType sportType, TournamentStatus status,
                                                              String keyword, Pageable pageable) {
        return tournamentRepository.searchTournaments(sportType, status, normalize(keyword), LocalDate.now(), pageable)
                .map(this::toResponse);
    }

    @Override
    public Page<TournamentResponseCommand> getMyTournaments(UUID userId, boolean organizing, Pageable pageable) {
        if (organizing) return tournamentRepository.findByOrganizerId(userId, pageable).map(this::toResponse);
        // Đăng ký bị từ chối / đã hủy vẫn hiện để người chơi thấy lý do và tình trạng hoàn phí.
        List<UUID> ids = registrationRepository.findTournamentIdsByParticipant(userId, List.of(
                RegistrationStatus.PENDING_MEMBERS, RegistrationStatus.PENDING_ELIGIBILITY,
                RegistrationStatus.PENDING_PAYMENT, RegistrationStatus.CONFIRMED, RegistrationStatus.REJECTED,
                RegistrationStatus.CANCELLED));
        return tournamentRepository.findByIds(ids, pageable).map(this::toResponse);
    }

    @Override
    public Page<TournamentResponseCommand> getOrganizedTournaments(UUID userId, TournamentStatus status, SportType sportType,
                                                                   UUID venueId, String keyword, Pageable pageable) {
        return tournamentRepository.searchOrganized(userId, status, sportType, venueId, normalize(keyword),
                LocalDate.now(), pageable).map(this::toResponse);
    }

    @Override
    public OrganizerSummary getOrganizerSummary(UUID userId) {
        Map<TournamentStatus, Long> byStatus = new EnumMap<>(TournamentStatus.class);
        for (TournamentStatus status : TournamentStatus.values()) byStatus.put(status, 0L);
        long seats = 0;
        List<Tournament> tournaments = tournamentRepository.findByOrganizerId(userId, Pageable.unpaged()).getContent();
        for (Tournament tournament : tournaments) {
            TournamentStatus status = effectiveStatus(tournament);
            byStatus.merge(status, 1L, Long::sum);
            // Suất giữ chỉ tính cho giải còn nhận/giữ người (chưa kết thúc, chưa hủy).
            if (status != TournamentStatus.COMPLETED && status != TournamentStatus.CANCELLED) {
                seats += registrationRepository.findByTournamentId(tournament.getTournamentId()).stream()
                        .filter(item -> TournamentLifecyclePolicy.holdsSeat(item.getStatus())).count();
            }
        }
        return new OrganizerSummary(byStatus, tournaments.size(), seats);
    }

    @Override
    public TournamentResponseCommand getTournamentDetails(UUID tournamentId, UUID viewerId) throws ServiceException {
        Tournament tournament = requireTournament(tournamentId);
        // Bản nháp chỉ ban tổ chức thấy; người khác nhận cùng lỗi như giải không tồn tại (không lộ sự tồn tại).
        if (tournament.getStatus() == TournamentStatus.DRAFT && !tournament.getOrganizerId().equals(viewerId)) {
            throw new ServiceException("Không tìm thấy giải đấu.");
        }
        return toResponse(tournament);
    }

    /**
     * Chỉ chủ sân tạo giải, tại cơ sở của chính họ: venue-service từ chối nếu cơ sở không thuộc người tạo.
     * Công bố ngay thì giữ sân luôn; lưu nháp chỉ kiểm tra (dry run), chưa khóa sân của người chơi.
     */
    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentResponseCommand createTournament(CreateTournamentCommand command) throws ServiceException {
        command.validate();
        LocalDate today = LocalDate.now();
        if (command.getRegistrationCloseDate().isBefore(today)) {
            throw new ServiceException("Hạn đóng đăng ký đã qua, hãy chọn ngày từ hôm nay trở đi.");
        }
        int maxParticipants = command.getMaxParticipants() == null ? 16 : command.getMaxParticipants();
        TournamentFormat format = command.getFormat() == null ? TournamentFormat.SINGLE_ELIMINATION : command.getFormat();
        assertEnoughCourts(format, maxParticipants, command.getCourtIds().size(), command.getStartDate(),
                command.getEndDate(), command.getDailyStartTime(), command.getDailyEndTime(), command.getMatchDurationMinutes());

        TournamentStatus status = Boolean.FALSE.equals(command.getPublish()) ? TournamentStatus.DRAFT
                : !today.isBefore(command.getRegistrationOpenDate())
                && !today.isAfter(command.getRegistrationCloseDate())
                ? TournamentStatus.REGISTRATION_OPEN : TournamentStatus.PUBLISHED;
        Tournament tournament = tournamentRepository.save(Tournament.builder()
                .organizerId(command.getOrganizerId()).name(command.getName().trim())
                .description(command.getDescription()).sportType(command.getSportType())
                .format(format).status(status).maxParticipants(maxParticipants)
                .entryFee(command.getEntryFee() == null ? 0D : command.getEntryFee())
                .prizePool(command.getPrizePool() == null ? 0D : command.getPrizePool())
                .registrationOpenDate(command.getRegistrationOpenDate())
                .registrationCloseDate(command.getRegistrationCloseDate())
                .startDate(command.getStartDate()).endDate(command.getEndDate())
                .rules(command.getRules() == null ? List.of() : List.copyOf(command.getRules()))
                .venueId(command.getVenueId()).courtIds(List.copyOf(command.getCourtIds()))
                .dailyStartTime(command.getDailyStartTime()).dailyEndTime(command.getDailyEndTime())
                .matchDurationMinutes(command.getMatchDurationMinutes()).playFormat(command.getPlayFormat())
                .build());
        saveEligibilityRules(tournament.getTournamentId(), command.getEligibilityRules());
        venueCourtHold.hold(tournament, status == TournamentStatus.DRAFT);
        return toResponse(tournament);
    }

    /**
     * Chỉ sửa được bản nháp. Đã công bố thì người chơi dựa vào nội dung giải để đăng ký/đóng phí,
     * nên đổi gì cũng là đổi luật giữa chừng; muốn đổi thì hủy giải (tự hoàn phí) và tạo giải mới.
     */
    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentResponseCommand updateTournament(UUID tournamentId, CreateTournamentCommand command)
            throws ServiceException {
        command.validate();
        Tournament tournament = tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
        TournamentGuards.assertOrganizer(tournament, command.getOrganizerId());
        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new ServiceException("Giải đã công bố nên không sửa được nội dung. Nếu cần thay đổi, hãy hủy giải và tạo giải mới.");
        }
        int maxParticipants = command.getMaxParticipants() == null ? tournament.getMaxParticipants()
                : command.getMaxParticipants();
        double entryFee = command.getEntryFee() == null ? 0D : command.getEntryFee();
        TournamentFormat format = command.getFormat() == null ? tournament.getFormat() : command.getFormat();
        assertEnoughCourts(format, maxParticipants, command.getCourtIds().size(), command.getStartDate(),
                command.getEndDate(), command.getDailyStartTime(), command.getDailyEndTime(), command.getMatchDurationMinutes());

        tournament.setName(command.getName().trim());
        tournament.setDescription(command.getDescription());
        tournament.setSportType(command.getSportType());
        tournament.setFormat(format);
        tournament.setMaxParticipants(maxParticipants);
        tournament.setEntryFee(entryFee);
        tournament.setPrizePool(command.getPrizePool() == null ? 0D : command.getPrizePool());
        tournament.setRegistrationOpenDate(command.getRegistrationOpenDate());
        tournament.setRegistrationCloseDate(command.getRegistrationCloseDate());
        tournament.setStartDate(command.getStartDate());
        tournament.setEndDate(command.getEndDate());
        tournament.setRules(command.getRules() == null ? List.of() : List.copyOf(command.getRules()));
        tournament.setVenueId(command.getVenueId());
        tournament.setCourtIds(List.copyOf(command.getCourtIds()));
        tournament.setDailyStartTime(command.getDailyStartTime());
        tournament.setDailyEndTime(command.getDailyEndTime());
        tournament.setMatchDurationMinutes(command.getMatchDurationMinutes());
        tournament.setPlayFormat(command.getPlayFormat());
        Tournament saved = tournamentRepository.save(tournament);
        eligibilityRuleRepository.deleteByTournamentId(tournamentId);
        saveEligibilityRules(tournamentId, command.getEligibilityRules());
        venueCourtHold.hold(saved, true);
        return toResponse(saved);
    }

    private void assertEnoughCourts(TournamentFormat format, int maxParticipants, int courts, LocalDate start,
                                    LocalDate end, java.time.LocalTime dailyStart, java.time.LocalTime dailyEnd,
                                    int matchMinutes) throws ServiceException {
        if (start.isBefore(LocalDate.now())) throw new ServiceException("Ngày khai mạc đã qua.");
        var capacity = TournamentCapacityPolicy.capacity(format, maxParticipants, courts, start, end,
                dailyStart, dailyEnd, matchMinutes);
        Optional<String> shortage = TournamentCapacityPolicy.shortage(capacity, courts, matchMinutes);
        if (shortage.isPresent()) throw new ServiceException(shortage.get());
    }

    /**
     * Chỉ các chuyển trạng thái thủ công của ban tổ chức: công bố, hủy, kết thúc. Mở/đóng đăng ký vẫn do ngày
     * quyết định (xem {@link TournamentLifecyclePolicy#effectiveStatus}) để không có hai nguồn sự thật song song.
     */
    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentResponseCommand changeStatus(UUID tournamentId, TournamentStatus target, UUID actorUserId)
            throws ServiceException {
        Tournament tournament = tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
        TournamentGuards.assertOrganizer(tournament, actorUserId);
        TournamentStatus current = tournament.getStatus();
        if (current == target) return toResponse(tournament);
        if (current == TournamentStatus.COMPLETED || current == TournamentStatus.CANCELLED) {
            throw new ServiceException("Giải đấu đã kết thúc hoặc đã hủy, không thể đổi trạng thái.");
        }
        if (target == TournamentStatus.CANCELLED && current == TournamentStatus.IN_PROGRESS) {
            throw new ServiceException("Giải đang diễn ra, không thể hủy giữa chừng. Hãy hoàn tất các trận rồi kết thúc giải.");
        }
        boolean allowed = switch (target) {
            case PUBLISHED -> current == TournamentStatus.DRAFT;
            case CANCELLED -> true;
            case COMPLETED -> current == TournamentStatus.IN_PROGRESS;
            default -> false;
        };
        if (!allowed) {
            throw new ServiceException("Không thể chuyển giải đấu từ " + current + " sang " + target + ".");
        }
        if (target == TournamentStatus.COMPLETED && fixtureRepository.findByTournamentId(tournamentId).stream()
                .anyMatch(item -> item.getStatus() != MatchStatus.COMPLETED)) {
            throw new ServiceException("Vẫn còn trận đấu chưa có kết quả.");
        }
        if (target == TournamentStatus.PUBLISHED) {
            if (tournament.getRegistrationCloseDate().isBefore(LocalDate.now())) {
                throw new ServiceException("Hạn đăng ký đã qua, hãy sửa lịch giải trước khi công bố.");
            }
            if (tournament.getVenueId() == null || tournament.getCourtIds() == null || tournament.getCourtIds().isEmpty()) {
                throw new ServiceException("Hãy chọn cơ sở và sân cho giải trước khi công bố.");
            }
            // Công bố mới thực sự khóa sân với người chơi.
            venueCourtHold.hold(tournament, false);
        }
        tournament.setStatus(target);
        if (target == TournamentStatus.COMPLETED) tournament.setCompletedAt(LocalDateTime.now());
        Tournament saved = tournamentRepository.save(tournament);
        if (target == TournamentStatus.CANCELLED) {
            closeOutReservations(tournamentId);
            registrationService.closeAllForCancelledTournament(saved, actorUserId);
        }
        // Hủy hoặc kết thúc sớm: trả các ngày/khung giờ còn lại cho người chơi đặt.
        if (target == TournamentStatus.CANCELLED || target == TournamentStatus.COMPLETED) {
            venueCourtHold.release(tournamentId);
        }
        return toResponse(saved);
    }

    /** Lịch xếp trận chỉ lưu ở club-service (sân đã giữ nguyên khối bên venue), hủy giải thì trả hết. */
    private void closeOutReservations(UUID tournamentId) {
        for (TournamentVenueReservation reservation : reservationRepository.findByTournamentId(tournamentId)) {
            if (reservation.getStatus() != ReservationStatus.PENDING
                    && reservation.getStatus() != ReservationStatus.CONFIRMED) continue;
            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);
        }
    }

    private void saveEligibilityRules(UUID tournamentId,
                                      List<CreateTournamentCommand.EligibilityRuleItem> items) {
        if (items == null || items.isEmpty()) return;
        List<TournamentEligibilityRule> rules = new ArrayList<>();
        for (CreateTournamentCommand.EligibilityRuleItem item : items) {
            rules.add(TournamentEligibilityRule.builder()
                    .tournamentId(tournamentId)
                    .ruleType(item.getRuleType())
                    .operator(item.getOperator())
                    .expectedValue(item.getExpectedValue())
                    .build());
        }
        eligibilityRuleRepository.saveAll(rules);
    }

    @Override
    public List<TournamentEligibilityRule> getEligibilityRules(UUID tournamentId) {
        return eligibilityRuleRepository.findByTournamentId(tournamentId);
    }

    private Tournament requireTournament(UUID id) throws ServiceException {
        return tournamentRepository.findById(id).orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private TournamentResponseCommand toResponse(Tournament t) {
        List<TournamentRegistration> registrations = registrationRepository.findByTournamentId(t.getTournamentId());
        long count = registrations.stream().filter(item -> TournamentLifecyclePolicy.holdsSeat(item.getStatus())).count();
        Optional<TournamentRegistration> champion = champion(t, registrations);
        SportParticipationPolicy.PlayFormat format = SportParticipationPolicy.resolve(t.getSportType(), t.getPlayFormat());
        return TournamentResponseCommand.builder().tournamentId(t.getTournamentId()).organizerId(t.getOrganizerId())
                .name(t.getName()).description(t.getDescription()).sportType(t.getSportType()).format(t.getFormat())
                .status(effectiveStatus(t)).maxParticipants(t.getMaxParticipants()).currentParticipants(count)
                .entryFee(t.getEntryFee()).prizePool(t.getPrizePool()).registrationOpenDate(t.getRegistrationOpenDate())
                .registrationCloseDate(t.getRegistrationCloseDate()).startDate(t.getStartDate()).endDate(t.getEndDate())
                .rules(t.getRules() == null ? List.of() : t.getRules()).venueId(t.getVenueId()).courtIds(t.getCourtIds())
                .dailyStartTime(t.getDailyStartTime()).dailyEndTime(t.getDailyEndTime())
                .matchDurationMinutes(t.getMatchDurationMinutes())
                .playFormat(format.code()).playFormatLabel(format.label()).participantType(format.participantType())
                .rosterMin(format.rosterMin()).rosterMax(format.rosterMax())
                .createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt()).completedAt(t.getCompletedAt())
                .championRegistrationId(champion.map(TournamentRegistration::getRegistrationId).orElse(null))
                .championName(champion.map(this::displayName).orElse(null))
                .build();
    }

    /** Nhà vô địch chỉ tính cho giải đã kết thúc, từ kết quả các trận đã lưu. */
    private Optional<TournamentRegistration> champion(Tournament t, List<TournamentRegistration> registrations) {
        if (t.getStatus() != TournamentStatus.COMPLETED) return Optional.empty();
        List<UUID> confirmed = registrations.stream()
                .filter(item -> item.getStatus() == RegistrationStatus.CONFIRMED)
                .map(TournamentRegistration::getRegistrationId).toList();
        return ChampionPolicy.champion(t.getFormat(), confirmed, fixtureRepository.findByTournamentId(t.getTournamentId()))
                .flatMap(id -> registrations.stream().filter(item -> id.equals(item.getRegistrationId())).findFirst());
    }

    /** Tên đội; giải cá nhân không có tên đội thì lấy tên trong đội hình hoặc họ tên tài khoản. */
    private String displayName(TournamentRegistration registration) {
        if (registration.getTeamName() != null && !registration.getTeamName().isBlank()) return registration.getTeamName();
        return lineupRepository.findByRegistrationId(registration.getRegistrationId()).stream()
                .filter(row -> registration.getRegisteredBy().equals(row.getPlayerId()) && row.getPlayerName() != null)
                .map(TournamentLineup::getPlayerName).findFirst()
                .or(() -> playerProfileProvider.findFullName(registration.getRegisteredBy()))
                .orElse("Người chơi");
    }

    private TournamentStatus effectiveStatus(Tournament tournament) {
        return TournamentLifecyclePolicy.effectiveStatus(tournament, LocalDate.now());
    }
}
