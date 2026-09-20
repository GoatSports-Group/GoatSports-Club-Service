package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.*;
import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.external.VenueSlotReservation;
import iuh.fit.clubservice.application.port.persistence.*;
import iuh.fit.clubservice.domain.service.EligibilityPolicy;
import iuh.fit.clubservice.application.service.TournamentService;
import iuh.fit.clubservice.domain.entity.*;
import iuh.fit.clubservice.domain.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentLineupRepository lineupRepository;
    private final TournamentFixtureRepository fixtureRepository;
    private final TournamentPaymentRepository paymentRepository;
    private final TournamentEligibilityRuleRepository eligibilityRuleRepository;
    private final PlayerProfileProvider playerProfileProvider;
    private final TournamentVenueReservationRepository reservationRepository;
    private final ClubRepository clubRepository;
    private final VenueSlotReservation venueSlotReservation;
    private final ClubConversationSync conversationSync;

    @Override
    public Page<TournamentResponseCommand> searchTournaments(SportType sportType, TournamentStatus status,
                                                              String keyword, Pageable pageable) {
        return tournamentRepository.searchTournaments(sportType, status, normalize(keyword), pageable)
                .map(this::toResponse);
    }

    @Override
    public TournamentResponseCommand getTournamentDetails(UUID tournamentId) throws ServiceException {
        return toResponse(requireTournament(tournamentId));
    }

    @Override
    @Transactional
    public TournamentResponseCommand createTournament(CreateTournamentCommand command) throws ServiceException {
        command.validate();
        LocalDate today = LocalDate.now();
        TournamentStatus status = !today.isBefore(command.getRegistrationOpenDate())
                && !today.isAfter(command.getRegistrationCloseDate())
                ? TournamentStatus.REGISTRATION_OPEN : TournamentStatus.PUBLISHED;
        Tournament tournament = tournamentRepository.save(Tournament.builder()
                .organizerId(command.getOrganizerId()).name(command.getName().trim())
                .description(command.getDescription()).sportType(command.getSportType())
                .format(command.getFormat() == null ? TournamentFormat.SINGLE_ELIMINATION : command.getFormat())
                .status(status).maxParticipants(command.getMaxParticipants() == null ? 16 : command.getMaxParticipants())
                .entryFee(command.getEntryFee() == null ? 0D : command.getEntryFee())
                .prizePool(command.getPrizePool() == null ? 0D : command.getPrizePool())
                .registrationOpenDate(command.getRegistrationOpenDate())
                .registrationCloseDate(command.getRegistrationCloseDate())
                .startDate(command.getStartDate()).endDate(command.getEndDate())
                .rules(command.getRules() == null ? List.of() : List.copyOf(command.getRules())).build());
        saveEligibilityRules(tournament.getTournamentId(), command.getEligibilityRules());
        return toResponse(tournament);
    }

    @Override
    @Transactional
    public TournamentRegistrationResponseCommand registerTeam(RegisterTournamentTeamCommand command) throws ServiceException {
        Tournament tournament = tournamentRepository.findByIdForUpdate(command.getTournamentId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
        if (effectiveStatus(tournament) != TournamentStatus.REGISTRATION_OPEN) {
            throw new ServiceException("Giải đấu chưa mở hoặc đã đóng đăng ký.");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(tournament.getRegistrationOpenDate()) || today.isAfter(tournament.getRegistrationCloseDate())) {
            throw new ServiceException("Hiện không nằm trong thời gian đăng ký giải đấu.");
        }
        List<TournamentRegistration> active = registrationRepository
                .findByTournamentId(command.getTournamentId()).stream()
                .filter(item -> item.getStatus() != RegistrationStatus.CANCELLED
                        && item.getStatus() != RegistrationStatus.REJECTED).toList();
        if (active.stream().anyMatch(item -> item.getRegisteredBy().equals(command.getRegisteredBy()))) {
            throw new ServiceException("Bạn đã đăng ký giải đấu này.");
        }
        if (active.size() >= tournament.getMaxParticipants()) {
            throw new ServiceException("Giải đấu đã đủ số lượng đăng ký.");
        }
        assertEligible(tournament, command);

        RegistrationType type = command.getType() == null ? RegistrationType.INDIVIDUAL : command.getType();
        command.validate(type);
        boolean paymentRequired = tournament.getEntryFee() != null && tournament.getEntryFee() > 0D;
        TournamentRegistration registration = registrationRepository.save(TournamentRegistration.builder()
                .tournamentId(command.getTournamentId()).type(type)
                .playerId(type == RegistrationType.INDIVIDUAL ? command.getRegisteredBy() : command.getPlayerId())
                .clubId(command.getClubId()).teamName(normalize(command.getTeamName()))
                .registeredBy(command.getRegisteredBy()).skillLevel(command.getSkillLevel())
                .status(paymentRequired ? RegistrationStatus.PENDING_PAYMENT : RegistrationStatus.CONFIRMED)
                .registeredAt(LocalDateTime.now())
                .confirmedAt(paymentRequired ? null : LocalDateTime.now()).build());

        if (paymentRequired) {
            paymentRepository.save(TournamentPayment.builder().registrationId(registration.getRegistrationId())
                    .amount(tournament.getEntryFee()).status(FeePaymentStatus.PENDING).build());
        }

        if (command.getLineups() != null && !command.getLineups().isEmpty()) {
            List<TournamentLineup> lineups = command.getLineups().stream()
                    .map(item -> mapLineup(registration.getRegistrationId(), item)).toList();
            registration.setLineups(lineupRepository.saveAll(lineups));
        }
        syncTournamentConversation(tournament);
        return toResponse(registration);
    }

    @Override
    @Transactional
    public void cancelRegistration(UUID tournamentId, UUID registrationId, UUID actorUserId) throws ServiceException {
        Tournament tournament = requireTournament(tournamentId);
        TournamentRegistration registration = registrationRepository.findById(registrationId)
                .filter(item -> item.getTournamentId().equals(tournamentId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy đăng ký giải đấu."));
        if (!registration.getRegisteredBy().equals(actorUserId) && !tournament.getOrganizerId().equals(actorUserId)) {
            throw new ServiceException("Bạn không có quyền hủy đăng ký này.");
        }
        if (registration.getStatus() == RegistrationStatus.CANCELLED) return;
        if (tournament.getStatus() == TournamentStatus.IN_PROGRESS || tournament.getStatus() == TournamentStatus.COMPLETED) {
            throw new ServiceException("Không thể hủy đăng ký khi giải đấu đã bắt đầu.");
        }
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setStatusReason("Người đăng ký hủy");
        registrationRepository.save(registration);
        syncTournamentConversation(tournament);
    }

    @Override
    public List<TournamentRegistrationResponseCommand> getTournamentTeams(UUID tournamentId) {
        return registrationRepository.findByTournamentId(tournamentId).stream().map(registration -> {
            registration.setLineups(lineupRepository.findByRegistrationId(registration.getRegistrationId()));
            return toResponse(registration);
        }).toList();
    }

    @Override
    @Transactional
    public List<TournamentFixtureResponseCommand> generateFixtures(UUID tournamentId, UUID actorUserId) throws ServiceException {
        Tournament tournament = requireTournament(tournamentId);
        assertOrganizer(tournament, actorUserId);
        List<TournamentRegistration> teams = registrationRepository
                .findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED);
        if (teams.size() < 2) throw new ServiceException("Cần ít nhất 2 đăng ký đã xác nhận để tạo lịch đấu.");
        fixtureRepository.deleteByTournamentId(tournamentId);

        // Seed tu tournamentId: chay lai cung mot giai luon sinh dung cung mot bracket.
        teams = new ArrayList<>(teams);
        teams.sort(Comparator.comparing(item -> item.getRegistrationId().toString()));

        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        tournamentRepository.save(tournament);

        if (tournament.getFormat() == TournamentFormat.ROUND_ROBIN) {
            List<TournamentFixture> fixtures = new ArrayList<>();
            int match = 1;
            for (int i = 0; i < teams.size(); i++) {
                for (int j = i + 1; j < teams.size(); j++) {
                    fixtures.add(fixture(tournamentId, "Vòng bảng", 1, match++, teams.get(i), teams.get(j)));
                }
            }
            return fixtureRepository.saveAll(fixtures).stream().map(this::toResponse).toList();
        }

        Collections.shuffle(teams, new Random(tournamentId.getMostSignificantBits()));
        return generateEliminationBracket(tournamentId, teams);
    }

    @Override
    public List<TournamentFixtureResponseCommand> getFixtures(UUID tournamentId) {
        return fixtureRepository.findByTournamentId(tournamentId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public TournamentFixtureResponseCommand updateFixtureResult(UpdateFixtureResultCommand command) throws ServiceException {
        command.validate();
        TournamentFixture fixture = fixtureRepository.findById(command.getFixtureId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy trận đấu."));
        Tournament tournament = requireTournament(fixture.getTournamentId());
        assertOrganizer(tournament, command.getActorUserId());
        if (fixture.getRegistration2Id() == null) throw new ServiceException("Trận đấu được miễn thi đấu không cần cập nhật tỷ số.");
        if (fixture.getRegistration1Id() == null) throw new ServiceException("Trận đấu chưa đủ hai đội.");

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
            // Sua ty so sau khi vong sau da da xong se lam sai ca nhanh con lai.
            if (next.getStatus() == MatchStatus.COMPLETED
                    && !Objects.equals(previousWinner, fixture.getWinnerRegistrationId())) {
                throw new ServiceException("Trận kế tiếp đã có kết quả, không thể đổi đội thắng trận này.");
            }
            if (fixture.getWinnerRegistrationId() == null) {
                throw new ServiceException("Vòng loại trực tiếp không chấp nhận tỷ số hòa.");
            }
            placeWinner(next, fixture.getMatchNumber(), fixture.getWinnerRegistrationId());
            fixtureRepository.save(next);
        }
        if (wasCompleted) applyClubStats(fixture, previousScore1, previousScore2, -1);
        applyClubStats(fixture, fixture.getScore1(), fixture.getScore2(), 1);
        return toResponse(fixtureRepository.save(fixture));
    }

    /**
     * Cong (sign=1) hoac tru (sign=-1) ket qua vao thanh tich CLB.
     * Sua ty so se tru ket qua cu truoc roi moi cong ket qua moi, neu khong thanh tich se cong don sai.
     */
    /**
     * Chat nhom giai dau: gui ca danh sach thay vi them/bot tung nguoi,
     * giong CLB - idempotent tu nhien va tu sua khi lo mot lan goi.
     */
    private void syncTournamentConversation(Tournament tournament) {
        List<UUID> participants = registrationRepository.findByTournamentId(tournament.getTournamentId()).stream()
                .filter(item -> item.getStatus() == RegistrationStatus.CONFIRMED)
                .map(TournamentRegistration::getRegisteredBy)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        conversationSync.syncTournament(tournament, participants);
    }

    private void applyClubStats(TournamentFixture fixture, Integer score1, Integer score2, int sign) {
        if (score1 == null || score2 == null) return;
        UUID firstClub = clubOf(fixture.getRegistration1Id());
        UUID secondClub = clubOf(fixture.getRegistration2Id());
        int comparison = Integer.compare(score1, score2);
        adjustClub(firstClub, comparison, sign);
        adjustClub(secondClub, -comparison, sign);
    }

    private UUID clubOf(UUID registrationId) {
        if (registrationId == null) return null;
        return registrationRepository.findById(registrationId)
                .map(TournamentRegistration::getClubId).orElse(null);
    }

    private void adjustClub(UUID clubId, int comparison, int sign) {
        if (clubId == null) return;
        clubRepository.findById(clubId).ifPresent(club -> {
            int wins = orZero(club.getWinCount()) + (comparison > 0 ? sign : 0);
            int losses = orZero(club.getLossCount()) + (comparison < 0 ? sign : 0);
            int draws = orZero(club.getDrawCount()) + (comparison == 0 ? sign : 0);
            club.setWinCount(Math.max(0, wins));
            club.setLossCount(Math.max(0, losses));
            club.setDrawCount(Math.max(0, draws));
            int matches = club.getWinCount() + club.getLossCount() + club.getDrawCount();
            club.setMatchCount(matches);
            club.setWinRate(matches == 0 ? 0D : (double) club.getWinCount() / matches);
            clubRepository.save(club);
        });
    }

    private int orZero(Integer value) { return value == null ? 0 : value; }

    @Override
    public List<TournamentStandingResponseCommand> getStandings(UUID tournamentId) {
        Map<UUID, int[]> table = new LinkedHashMap<>();
        registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED)
                .forEach(registration -> table.put(registration.getRegistrationId(), new int[7]));
        fixtureRepository.findByTournamentId(tournamentId).stream()
                .filter(f -> f.getStatus() == MatchStatus.COMPLETED && f.getRegistration2Id() != null)
                .forEach(f -> applyResult(table, f));

        // Truoc day chuoi comparator bi .reversed() lan hai tren toan bo chuoi,
        // lam bang xep hang dao nguoc (doi 0 diem dung hang 1).
        Comparator<Map.Entry<UUID, int[]>> ranking =
                Comparator.<Map.Entry<UUID, int[]>>comparingInt(e -> e.getValue()[6]).reversed()
                        .thenComparing(Comparator.<Map.Entry<UUID, int[]>>comparingInt(
                                e -> e.getValue()[4] - e.getValue()[5]).reversed())
                        .thenComparing(Comparator.<Map.Entry<UUID, int[]>>comparingInt(
                                e -> e.getValue()[4]).reversed())
                        .thenComparing(e -> e.getKey().toString());
        List<Map.Entry<UUID, int[]>> sorted = table.entrySet().stream().sorted(ranking).toList();
        List<TournamentStandingResponseCommand> result = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            int[] s = sorted.get(index).getValue();
            result.add(TournamentStandingResponseCommand.builder().tournamentId(tournamentId)
                    .registrationId(sorted.get(index).getKey()).played(s[0]).won(s[1]).drawn(s[2]).lost(s[3])
                    .scoreFor(s[4]).scoreAgainst(s[5]).points(s[6]).rank(index + 1).build());
        }
        return result;
    }

    private void applyResult(Map<UUID, int[]> table, TournamentFixture f) {
        int[] first = table.get(f.getRegistration1Id());
        int[] second = table.get(f.getRegistration2Id());
        if (first == null || second == null) return;
        first[0]++; second[0]++; first[4] += f.getScore1(); first[5] += f.getScore2();
        second[4] += f.getScore2(); second[5] += f.getScore1();
        if (f.getScore1().equals(f.getScore2())) { first[2]++; second[2]++; first[6]++; second[6]++; }
        else if (f.getScore1() > f.getScore2()) { first[1]++; second[3]++; first[6] += 3; }
        else { second[1]++; first[3]++; second[6] += 3; }
    }

    /** Sinh cay loai truc tiep day du: moi tran deu biet tran ke tiep, bye duoc day len ngay. */
    private List<TournamentFixtureResponseCommand> generateEliminationBracket(
            UUID tournamentId, List<TournamentRegistration> teams) {
        int bracketSize = 1;
        while (bracketSize < teams.size()) bracketSize *= 2;
        int totalRounds = Integer.numberOfTrailingZeros(bracketSize);

        // Sinh nguoc tu chung ket ve vong 1 vi vong truoc can id cua tran ke tiep.
        List<TournamentFixture> nextRound = null;
        List<List<TournamentFixture>> allRounds = new ArrayList<>();
        for (int round = totalRounds; round >= 1; round--) {
            int matches = bracketSize >> round;
            List<TournamentFixture> current = new ArrayList<>();
            for (int index = 0; index < matches; index++) {
                var builder = TournamentFixture.builder()
                        .tournamentId(tournamentId)
                        .roundName(roundName(round, totalRounds))
                        .roundNumber(round)
                        .matchNumber(index + 1)
                        .nextFixtureId(nextRound == null ? null : nextRound.get(index / 2).getFixtureId())
                        .status(MatchStatus.SCHEDULED);
                if (round == 1) {
                    TournamentRegistration first = seatAt(teams, index * 2);
                    TournamentRegistration second = seatAt(teams, index * 2 + 1);
                    builder.registration1Id(first == null ? null : first.getRegistrationId())
                            .registration2Id(second == null ? null : second.getRegistrationId());
                }
                current.add(builder.build());
            }
            current = fixtureRepository.saveAll(current);
            allRounds.add(0, current);
            nextRound = current;
        }

        List<TournamentFixture> firstRound = allRounds.get(0);
        if (totalRounds > 1) advanceByes(firstRound, allRounds.get(1));

        return allRounds.stream().flatMap(List::stream).map(this::toResponse).toList();
    }

    /** Tran chi co mot doi (bye) duoc xu ly ngay de vong sau khong ket vinh vien. */
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
            if (target != null) placeWinner(target, fixture.getMatchNumber(), fixture.getRegistration1Id());
        }
        if (touched.isEmpty()) return;
        fixtureRepository.saveAll(touched);
        fixtureRepository.saveAll(new ArrayList<>(next.values()));
    }

    /** Tran thu n cua vong truoc do vao nhanh tren hay duoi cua tran ke tiep theo tinh chan le. */
    private void placeWinner(TournamentFixture target, int sourceMatchNumber, UUID registrationId) {
        if ((sourceMatchNumber - 1) % 2 == 0) target.setRegistration1Id(registrationId);
        else target.setRegistration2Id(registrationId);
    }

    private TournamentRegistration seatAt(List<TournamentRegistration> teams, int index) {
        return index < teams.size() ? teams.get(index) : null;
    }

    private String roundName(int round, int totalRounds) {
        int fromFinal = totalRounds - round;
        if (fromFinal == 0) return "Chung kết";
        if (fromFinal == 1) return "Bán kết";
        if (fromFinal == 2) return "Tứ kết";
        return "Vòng " + round;
    }

    private TournamentFixture fixture(UUID tournamentId, String round, int roundNumber, int match,
                                      TournamentRegistration first, TournamentRegistration second) {
        return TournamentFixture.builder().tournamentId(tournamentId).roundName(round).roundNumber(roundNumber)
                .matchNumber(match).registration1Id(first.getRegistrationId())
                .registration2Id(second == null ? null : second.getRegistrationId())
                .winnerRegistrationId(second == null ? first.getRegistrationId() : null)
                .status(second == null ? MatchStatus.COMPLETED : MatchStatus.SCHEDULED).build();
    }

    private TournamentLineup mapLineup(UUID registrationId, RegisterTournamentTeamCommand.LineupItem item) {
        return TournamentLineup.builder().registrationId(registrationId).playerId(item.getPlayerId())
                .playerName(item.getPlayerName()).lineupRole(item.getLineupRole())
                .shirtNumber(item.getShirtNumber()).build();
    }

    /**
     * Chi cac chuyen trang thai thu cong. Mo/dong dang ky van do ngay quyet dinh
     * (xem effectiveStatus) nen khong dung hai nguon su that song song.
     */
    @Override
    @Transactional
    public TournamentResponseCommand changeStatus(UUID tournamentId, TournamentStatus target, UUID actorUserId)
            throws ServiceException {
        Tournament tournament = requireTournament(tournamentId);
        assertOrganizer(tournament, actorUserId);
        TournamentStatus current = tournament.getStatus();
        if (current == target) return toResponse(tournament);
        if (current == TournamentStatus.COMPLETED || current == TournamentStatus.CANCELLED) {
            throw new ServiceException("Giải đấu đã kết thúc hoặc đã hủy, không thể đổi trạng thái.");
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
        tournament.setStatus(target);
        return toResponse(tournamentRepository.save(tournament));
    }

    @Override
    @Transactional
    public TournamentVenueReservationResponseCommand reserveVenue(ReserveTournamentVenueCommand command)
            throws ServiceException {
        command.validate();
        Tournament tournament = requireTournament(command.getTournamentId());
        assertOrganizer(tournament, command.getActorUserId());

        // Khoa cac dat cho cung san cung ngay truoc khi kiem tra, tranh hai giai cung chiem mot khung gio.
        List<TournamentVenueReservation> overlaps = reservationRepository.findActiveOverlaps(
                command.getCourtId(), command.getPlayDate(), command.getStartTime(), command.getEndTime());
        if (!overlaps.isEmpty()) {
            throw new ServiceException("Khung giờ này đã được giữ cho một trận khác.");
        }

        TournamentFixture fixture = null;
        if (command.getFixtureId() != null) {
            fixture = fixtureRepository.findById(command.getFixtureId())
                    .filter(item -> item.getTournamentId().equals(command.getTournamentId()))
                    .orElseThrow(() -> new ServiceException("Không tìm thấy trận đấu của giải này."));
        }

        // Venue moi biet nguoi choi thuong da dat gio nao; guard overlap o tren chi chan giai-vs-giai.
        venueSlotReservation.hold(command.getCourtId(), command.getPlayDate(),
                command.getStartTime(), command.getEndTime());

        TournamentVenueReservation reservation = reservationRepository.save(TournamentVenueReservation.builder()
                .tournamentId(command.getTournamentId())
                .venueId(command.getVenueId())
                .courtId(command.getCourtId())
                .playDate(command.getPlayDate())
                .startTime(command.getStartTime())
                .endTime(command.getEndTime())
                .status(ReservationStatus.PENDING)
                .build());

        if (fixture != null) {
            fixture.setReservationId(reservation.getReservationId());
            fixtureRepository.save(fixture);
        }
        return toResponse(reservation);
    }

    @Override
    public List<TournamentVenueReservationResponseCommand> getReservations(UUID tournamentId) {
        return reservationRepository.findByTournamentId(tournamentId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void releaseReservation(UUID tournamentId, UUID reservationId, UUID actorUserId) throws ServiceException {
        Tournament tournament = requireTournament(tournamentId);
        assertOrganizer(tournament, actorUserId);
        TournamentVenueReservation reservation = reservationRepository.findById(reservationId)
                .filter(item -> item.getTournamentId().equals(tournamentId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy đặt sân của giải này."));
        if (reservation.getStatus() == ReservationStatus.RELEASED
                || reservation.getStatus() == ReservationStatus.CANCELLED) return;
        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.save(reservation);
        venueSlotReservation.release(reservation.getCourtId(), reservation.getPlayDate(),
                reservation.getStartTime(), reservation.getEndTime());

        fixtureRepository.findByTournamentId(tournamentId).stream()
                .filter(item -> reservationId.equals(item.getReservationId()))
                .forEach(item -> {
                    item.setReservationId(null);
                    fixtureRepository.save(item);
                });
    }

    private TournamentVenueReservationResponseCommand toResponse(TournamentVenueReservation reservation) {
        return TournamentVenueReservationResponseCommand.builder()
                .reservationId(reservation.getReservationId())
                .tournamentId(reservation.getTournamentId())
                .venueId(reservation.getVenueId())
                .courtId(reservation.getCourtId())
                .bookingId(reservation.getBookingId())
                .playDate(reservation.getPlayDate())
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .status(reservation.getStatus())
                .build();
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

    /**
     * Ho so lay tu auth-service, khong lay tu du lieu nguoi dang ky tu khai:
     * tu khai se cho phep lach dieu kien tuoi/ELO.
     */
    private void assertEligible(Tournament tournament, RegisterTournamentTeamCommand command)
            throws ServiceException {
        List<TournamentEligibilityRule> rules =
                eligibilityRuleRepository.findByTournamentId(tournament.getTournamentId());
        if (rules.isEmpty()) return;

        UUID subject = command.getPlayerId() != null ? command.getPlayerId() : command.getRegisteredBy();
        boolean needsProfile = rules.stream().anyMatch(rule ->
                rule.getRuleType() == EligibilityRuleType.AGE
                        || rule.getRuleType() == EligibilityRuleType.GENDER
                        || rule.getRuleType() == EligibilityRuleType.SKILL_LEVEL
                        || rule.getRuleType() == EligibilityRuleType.ELO_RATING);

        PlayerProfileProvider.PlayerProfileView profile = needsProfile
                ? playerProfileProvider.findProfile(subject, tournament.getSportType()).orElse(null)
                : null;

        Integer age = profile == null || profile.dateOfBirth() == null ? null
                : Period.between(profile.dateOfBirth(), LocalDate.now()).getYears();
        int teamSize = command.getLineups() == null ? 0 : command.getLineups().size();

        EligibilityPolicy.Candidate candidate = new EligibilityPolicy.Candidate(
                age,
                profile == null ? null : profile.gender(),
                profile == null ? null : profile.skillLevel(),
                profile == null ? null : profile.eloRating(),
                command.getClubId(),
                teamSize);

        Optional<String> violation = EligibilityPolicy.firstViolation(rules, candidate);
        if (violation.isPresent()) throw new ServiceException(violation.get());
    }

    private Tournament requireTournament(UUID id) throws ServiceException {
        return tournamentRepository.findById(id).orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
    }
    private void assertOrganizer(Tournament t, UUID userId) throws ServiceException {
        if (!t.getOrganizerId().equals(userId)) throw new ServiceException("Chỉ người tổ chức mới có quyền thực hiện thao tác này.");
    }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private TournamentResponseCommand toResponse(Tournament t) {
        long count = registrationRepository.findByTournamentId(t.getTournamentId()).stream()
                .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED && r.getStatus() != RegistrationStatus.REJECTED).count();
        return TournamentResponseCommand.builder().tournamentId(t.getTournamentId()).organizerId(t.getOrganizerId())
                .name(t.getName()).description(t.getDescription()).sportType(t.getSportType()).format(t.getFormat())
                .status(effectiveStatus(t)).maxParticipants(t.getMaxParticipants()).currentParticipants(count)
                .entryFee(t.getEntryFee()).prizePool(t.getPrizePool()).registrationOpenDate(t.getRegistrationOpenDate())
                .registrationCloseDate(t.getRegistrationCloseDate()).startDate(t.getStartDate()).endDate(t.getEndDate())
                .rules(t.getRules()).createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt()).build();
    }

    private TournamentRegistrationResponseCommand toResponse(TournamentRegistration r) {
        List<TournamentRegistrationResponseCommand.LineupResponseItem> lineups = r.getLineups() == null ? List.of()
                : r.getLineups().stream().map(l -> TournamentRegistrationResponseCommand.LineupResponseItem.builder()
                .lineupId(l.getLineupId()).playerId(l.getPlayerId()).playerName(l.getPlayerName())
                .lineupRole(l.getLineupRole()).shirtNumber(l.getShirtNumber()).build()).toList();
        return TournamentRegistrationResponseCommand.builder().registrationId(r.getRegistrationId())
                .tournamentId(r.getTournamentId()).type(r.getType()).playerId(r.getPlayerId()).clubId(r.getClubId())
                .teamName(r.getTeamName()).registeredBy(r.getRegisteredBy()).skillLevel(r.getSkillLevel())
                .status(r.getStatus()).statusReason(r.getStatusReason()).registeredAt(r.getRegisteredAt())
                .confirmedAt(r.getConfirmedAt()).lineups(lineups).build();
    }

    private TournamentStatus effectiveStatus(Tournament tournament) {
        TournamentStatus stored = tournament.getStatus();
        if (stored == TournamentStatus.DRAFT || stored == TournamentStatus.CANCELLED
                || stored == TournamentStatus.IN_PROGRESS || stored == TournamentStatus.COMPLETED) return stored;
        LocalDate today = LocalDate.now();
        if (today.isBefore(tournament.getRegistrationOpenDate())) return TournamentStatus.PUBLISHED;
        if (!today.isAfter(tournament.getRegistrationCloseDate())) return TournamentStatus.REGISTRATION_OPEN;
        return TournamentStatus.REGISTRATION_CLOSED;
    }

    private TournamentFixtureResponseCommand toResponse(TournamentFixture f) {
        return TournamentFixtureResponseCommand.builder().fixtureId(f.getFixtureId()).tournamentId(f.getTournamentId())
                .roundName(f.getRoundName()).roundNumber(f.getRoundNumber()).matchNumber(f.getMatchNumber())
                .registration1Id(f.getRegistration1Id()).registration2Id(f.getRegistration2Id())
                .score1(f.getScore1()).score2(f.getScore2()).winnerRegistrationId(f.getWinnerRegistrationId())
                .nextFixtureId(f.getNextFixtureId()).reservationId(f.getReservationId()).status(f.getStatus()).build();
    }
}
