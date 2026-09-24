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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentFixtureRepository fixtureRepository;
    private final TournamentEligibilityRuleRepository eligibilityRuleRepository;
    private final TournamentVenueReservationRepository reservationRepository;
    private final ClubRepository clubRepository;
    private final VenueCourtHold venueCourtHold;
    private final TournamentRegistrationService registrationService;

    @Override
    public Page<TournamentResponseCommand> searchTournaments(SportType sportType, TournamentStatus status,
                                                              String keyword, Pageable pageable) {
        return tournamentRepository.searchTournaments(sportType, status, normalize(keyword), LocalDate.now(), pageable)
                .map(this::toResponse);
    }

    @Override
    public Page<TournamentResponseCommand> getMyTournaments(UUID userId, boolean organizing, Pageable pageable) {
        if (organizing) return tournamentRepository.findByOrganizerId(userId, pageable).map(this::toResponse);
        // Dang ky bi tu choi / da huy van hien de nguoi choi thay ly do va tinh trang hoan phi.
        List<UUID> ids = registrationRepository.findTournamentIdsByParticipant(userId, List.of(
                RegistrationStatus.PENDING_MEMBERS, RegistrationStatus.PENDING_ELIGIBILITY,
                RegistrationStatus.PENDING_PAYMENT, RegistrationStatus.CONFIRMED, RegistrationStatus.REJECTED,
                RegistrationStatus.CANCELLED));
        return tournamentRepository.findByIds(ids, pageable).map(this::toResponse);
    }

    @Override
    public TournamentResponseCommand getTournamentDetails(UUID tournamentId, UUID viewerId) throws ServiceException {
        Tournament tournament = requireTournament(tournamentId);
        // Ban nhap chi ban to chuc thay; nguoi khac nhan cung loi nhu giai khong ton tai.
        if (tournament.getStatus() == TournamentStatus.DRAFT && !tournament.getOrganizerId().equals(viewerId)) {
            throw new ServiceException("Không tìm thấy giải đấu.");
        }
        return toResponse(tournament);
    }

    /**
     * Chi chu san tao giai, tai co so cua chinh ho: venue-service tu choi neu co so khong thuoc nguoi tao.
     * Cong bo ngay thi giu san luon; luu nhap chi kiem tra (chua khoa san cua nguoi choi).
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
     * Sua cau hinh truoc khi giai bat dau. Khi da co nguoi dang ky thi khoa nhung gi ho da dong y:
     * le phi (khoan cho thanh toan dang mang so tien cu), mon, hinh thuc, co so va dieu kien tham du.
     * San/ngay/khung gio doi duoc: venue-service giu lai toan bo va tu choi neu trung luot dat hien co.
     */
    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentResponseCommand updateTournament(UUID tournamentId, CreateTournamentCommand command)
            throws ServiceException {
        command.validate();
        Tournament tournament = tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
        assertOrganizer(tournament, command.getOrganizerId());
        TournamentStatus current = tournament.getStatus();
        if (current == TournamentStatus.IN_PROGRESS || current == TournamentStatus.COMPLETED
                || current == TournamentStatus.CANCELLED) {
            throw new ServiceException("Chỉ sửa được giải đấu chưa bắt đầu.");
        }
        int holding = (int) registrationRepository.findByTournamentId(tournamentId).stream()
                .filter(item -> TournamentRegistrationServiceImpl.HOLDING_SEAT.contains(item.getStatus())).count();
        int maxParticipants = command.getMaxParticipants() == null ? tournament.getMaxParticipants()
                : command.getMaxParticipants();
        if (maxParticipants < holding) {
            throw new ServiceException("Số suất không được nhỏ hơn số đăng ký hiện có (" + holding + ").");
        }
        double entryFee = command.getEntryFee() == null ? 0D : command.getEntryFee();
        boolean rulesChanged = !ruleKeys(command.getEligibilityRules())
                .equals(ruleKeysOf(eligibilityRuleRepository.findByTournamentId(tournamentId)));
        if (holding > 0) {
            if (Math.abs(entryFee - orZero(tournament.getEntryFee())) > 0.001D) {
                throw new ServiceException("Đã có người đăng ký nên không thể đổi lệ phí.");
            }
            if (command.getSportType() != tournament.getSportType()
                    || !Objects.equals(command.getPlayFormat(), tournament.getPlayFormat())) {
                throw new ServiceException("Đã có người đăng ký nên không thể đổi môn hoặc hình thức thi đấu.");
            }
            if (!Objects.equals(command.getVenueId(), tournament.getVenueId())) {
                throw new ServiceException("Đã có người đăng ký nên không thể đổi cơ sở tổ chức.");
            }
            if (rulesChanged) {
                throw new ServiceException("Đã có người đăng ký nên không thể đổi điều kiện tham dự.");
            }
        }
        TournamentFormat format = command.getFormat() == null ? tournament.getFormat() : command.getFormat();
        assertEnoughCourts(format, maxParticipants, command.getCourtIds().size(), command.getStartDate(),
                command.getEndDate(), command.getDailyStartTime(), command.getDailyEndTime(), command.getMatchDurationMinutes());

        boolean renamed = !tournament.getName().equals(command.getName().trim());
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
        if (rulesChanged) {
            eligibilityRuleRepository.deleteByTournamentId(tournamentId);
            saveEligibilityRules(tournamentId, command.getEligibilityRules());
        }
        venueCourtHold.hold(saved, saved.getStatus() == TournamentStatus.DRAFT);
        if (renamed) registrationService.syncConversation(saved);
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

    private Set<String> ruleKeys(List<CreateTournamentCommand.EligibilityRuleItem> items) {
        Set<String> keys = new HashSet<>();
        if (items != null) items.forEach(item -> keys.add(item.getRuleType() + "|" + item.getOperator() + "|"
                + (item.getExpectedValue() == null ? "" : item.getExpectedValue().trim())));
        return keys;
    }

    private Set<String> ruleKeysOf(List<TournamentEligibilityRule> rules) {
        Set<String> keys = new HashSet<>();
        rules.forEach(rule -> keys.add(rule.getRuleType() + "|" + rule.getOperator() + "|"
                + (rule.getExpectedValue() == null ? "" : rule.getExpectedValue().trim())));
        return keys;
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public List<TournamentFixtureResponseCommand> generateFixtures(UUID tournamentId, UUID actorUserId) throws ServiceException {
        // Khoa hang giai de dang ky dang chay song song khong lot vao sau khi danh sach da chot.
        Tournament tournament = tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
        assertOrganizer(tournament, actorUserId);
        TournamentStatus status = effectiveStatus(tournament);
        if (status == TournamentStatus.DRAFT) throw new ServiceException("Hãy công bố giải trước khi xếp lịch.");
        if (status == TournamentStatus.COMPLETED || status == TournamentStatus.CANCELLED) {
            throw new ServiceException("Giải đấu đã kết thúc hoặc đã hủy.");
        }
        // Xep lai se xoa ket qua da nhap va lam lech thanh tich CLB da cong.
        if (fixtureRepository.findByTournamentId(tournamentId).stream().anyMatch(this::hasPlayedResult)) {
            throw new ServiceException("Đã có trận có kết quả, không thể xếp lại lịch thi đấu.");
        }
        List<TournamentRegistration> teams = registrationRepository
                .findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED);
        if (teams.size() < 2) throw new ServiceException("Cần ít nhất 2 đăng ký đã xác nhận để tạo lịch đấu.");
        // Bracket chot tu danh sach CONFIRMED luc nay; doi chua du nguoi / chua dong phi khong con cho nua.
        registrationService.closeUnfinishedBeforeFixtures(tournament, actorUserId);
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
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentFixtureResponseCommand updateFixtureResult(UpdateFixtureResultCommand command) throws ServiceException {
        command.validate();
        TournamentFixture fixture = fixtureRepository.findById(command.getFixtureId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy trận đấu."));
        Tournament tournament = requireTournament(fixture.getTournamentId());
        assertOrganizer(tournament, command.getActorUserId());
        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            throw new ServiceException("Chỉ cập nhật tỷ số khi giải đấu đang diễn ra.");
        }
        if (fixture.getRegistration2Id() == null) throw new ServiceException("Trận đấu được miễn thi đấu không cần cập nhật tỷ số.");
        if (fixture.getRegistration1Id() == null) throw new ServiceException("Trận đấu chưa đủ hai đội.");
        // Truoc day chi chan hoa khi co tran ke tiep, nen chung ket hoa lot qua va giai khong co nha vo dich.
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
            // Sua ty so sau khi vong sau da da xong se lam sai ca nhanh con lai.
            if (next.getStatus() == MatchStatus.COMPLETED
                    && !Objects.equals(previousWinner, fixture.getWinnerRegistrationId())) {
                throw new ServiceException("Trận kế tiếp đã có kết quả, không thể đổi đội thắng trận này.");
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

    private double orZero(Double value) { return value == null ? 0D : value; }

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

    private boolean hasPlayedResult(TournamentFixture fixture) {
        return fixture.getStatus() == MatchStatus.COMPLETED
                && fixture.getRegistration1Id() != null && fixture.getRegistration2Id() != null;
    }

    /**
     * Chi cac chuyen trang thai thu cong. Mo/dong dang ky van do ngay quyet dinh
     * (xem effectiveStatus) nen khong dung hai nguon su that song song.
     */
    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentResponseCommand changeStatus(UUID tournamentId, TournamentStatus target, UUID actorUserId)
            throws ServiceException {
        Tournament tournament = tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
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
        if (target == TournamentStatus.PUBLISHED) {
            if (tournament.getRegistrationCloseDate().isBefore(LocalDate.now())) {
                throw new ServiceException("Hạn đăng ký đã qua, hãy sửa lịch giải trước khi công bố.");
            }
            if (tournament.getVenueId() == null || tournament.getCourtIds() == null || tournament.getCourtIds().isEmpty()) {
                throw new ServiceException("Hãy chọn cơ sở và sân cho giải trước khi công bố.");
            }
            // Cong bo moi thuc su khoa san cua nguoi choi.
            venueCourtHold.hold(tournament, false);
        }
        tournament.setStatus(target);
        Tournament saved = tournamentRepository.save(tournament);
        if (target == TournamentStatus.CANCELLED) {
            closeOutReservations(tournamentId);
            registrationService.closeAllForCancelledTournament(saved, actorUserId);
        }
        // Huy hoac ket thuc som: tra cac ngay/khung gio con lai cho nguoi choi dat.
        if (target == TournamentStatus.CANCELLED || target == TournamentStatus.COMPLETED) {
            venueCourtHold.release(tournamentId);
        }
        return toResponse(saved);
    }

    /** Lich xep tran tren san cua giai; chi luu o club-service vi san da duoc giu nguyen khoi ben venue. */
    private void closeOutReservations(UUID tournamentId) {
        for (TournamentVenueReservation reservation : reservationRepository.findByTournamentId(tournamentId)) {
            if (reservation.getStatus() != ReservationStatus.PENDING
                    && reservation.getStatus() != ReservationStatus.CONFIRMED) continue;
            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);
        }
    }

    /**
     * Xep mot tran vao mot san + khung gio cua giai. San nam trong danh sach san giai da giu ben venue,
     * nen o day chi can chong trung giua cac tran cua chinh giai.
     */
    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentVenueReservationResponseCommand reserveVenue(ReserveTournamentVenueCommand command)
            throws ServiceException {
        command.validate();
        Tournament tournament = requireTournament(command.getTournamentId());
        assertOrganizer(tournament, command.getActorUserId());
        if (tournament.getStatus() == TournamentStatus.COMPLETED || tournament.getStatus() == TournamentStatus.CANCELLED) {
            throw new ServiceException("Giải đấu đã kết thúc hoặc đã hủy.");
        }
        if (tournament.getVenueId() == null || tournament.getCourtIds() == null) {
            throw new ServiceException("Giải chưa gắn cơ sở và sân thi đấu.");
        }
        if (!tournament.getCourtIds().contains(command.getCourtId())) {
            throw new ServiceException("Sân này không nằm trong danh sách sân của giải.");
        }
        if (command.getPlayDate().isBefore(tournament.getStartDate()) || command.getPlayDate().isAfter(tournament.getEndDate())) {
            throw new ServiceException("Ngày thi đấu phải nằm trong thời gian diễn ra giải.");
        }
        if (command.getStartTime().isBefore(tournament.getDailyStartTime())
                || command.getEndTime().isAfter(tournament.getDailyEndTime())) {
            throw new ServiceException("Giờ thi đấu phải nằm trong khung " + tournament.getDailyStartTime()
                    + "–" + tournament.getDailyEndTime() + " mà giải đã giữ sân.");
        }
        if (Duration.between(command.getStartTime(), command.getEndTime()).toMinutes()
                < (tournament.getMatchDurationMinutes() == null ? 0 : tournament.getMatchDurationMinutes())) {
            throw new ServiceException("Khung giờ ngắn hơn thời lượng một trận (" + tournament.getMatchDurationMinutes() + " phút).");
        }

        // Khoa cac lich cung san cung ngay truoc khi kiem tra, tranh hai tran cung chiem mot khung gio.
        List<TournamentVenueReservation> overlaps = reservationRepository.findActiveOverlaps(
                command.getCourtId(), command.getPlayDate(), command.getStartTime(), command.getEndTime());
        if (!overlaps.isEmpty()) {
            throw new ServiceException("Khung giờ này đã được xếp cho một trận khác.");
        }

        TournamentFixture fixture = null;
        if (command.getFixtureId() != null) {
            fixture = fixtureRepository.findById(command.getFixtureId())
                    .filter(item -> item.getTournamentId().equals(command.getTournamentId()))
                    .orElseThrow(() -> new ServiceException("Không tìm thấy trận đấu của giải này."));
            if (fixture.getStatus() == MatchStatus.COMPLETED) throw new ServiceException("Trận này đã có kết quả.");
        }

        TournamentVenueReservation reservation = reservationRepository.save(TournamentVenueReservation.builder()
                .tournamentId(command.getTournamentId())
                .venueId(tournament.getVenueId())
                .courtId(command.getCourtId())
                .playDate(command.getPlayDate())
                .startTime(command.getStartTime())
                .endTime(command.getEndTime())
                .status(ReservationStatus.CONFIRMED)
                .build());

        if (fixture != null) {
            if (fixture.getReservationId() != null) releaseLocal(fixture.getReservationId());
            fixture.setReservationId(reservation.getReservationId());
            fixtureRepository.save(fixture);
        }
        return toResponse(reservation);
    }

    private void releaseLocal(UUID reservationId) {
        reservationRepository.findById(reservationId).ifPresent(old -> {
            old.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(old);
        });
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
                .orElseThrow(() -> new ServiceException("Không tìm thấy lịch sân của giải này."));
        if (reservation.getStatus() == ReservationStatus.RELEASED
                || reservation.getStatus() == ReservationStatus.CANCELLED) return;
        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.save(reservation);

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

    private Tournament requireTournament(UUID id) throws ServiceException {
        return tournamentRepository.findById(id).orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
    }

    private void assertOrganizer(Tournament t, UUID userId) throws ServiceException {
        if (!t.getOrganizerId().equals(userId)) throw new ServiceException("Chỉ người tổ chức mới có quyền thực hiện thao tác này.");
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private TournamentResponseCommand toResponse(Tournament t) {
        long count = registrationRepository.findByTournamentId(t.getTournamentId()).stream()
                .filter(item -> TournamentRegistrationServiceImpl.HOLDING_SEAT.contains(item.getStatus())).count();
        SportParticipationPolicy.PlayFormat format = SportParticipationPolicy.resolve(t.getSportType(), t.getPlayFormat());
        return TournamentResponseCommand.builder().tournamentId(t.getTournamentId()).organizerId(t.getOrganizerId())
                .name(t.getName()).description(t.getDescription()).sportType(t.getSportType()).format(t.getFormat())
                .status(effectiveStatus(t)).maxParticipants(t.getMaxParticipants()).currentParticipants(count)
                .entryFee(t.getEntryFee()).prizePool(t.getPrizePool()).registrationOpenDate(t.getRegistrationOpenDate())
                .registrationCloseDate(t.getRegistrationCloseDate()).startDate(t.getStartDate()).endDate(t.getEndDate())
                .rules(t.getRules()).venueId(t.getVenueId()).courtIds(t.getCourtIds())
                .dailyStartTime(t.getDailyStartTime()).dailyEndTime(t.getDailyEndTime())
                .matchDurationMinutes(t.getMatchDurationMinutes())
                .playFormat(format.code()).playFormatLabel(format.label()).participantType(format.participantType())
                .rosterMin(format.rosterMin()).rosterMax(format.rosterMax())
                .createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt()).build();
    }

    private TournamentStatus effectiveStatus(Tournament tournament) {
        return TournamentRegistrationServiceImpl.effectiveStatus(tournament);
    }

    private TournamentFixtureResponseCommand toResponse(TournamentFixture f) {
        return TournamentFixtureResponseCommand.builder().fixtureId(f.getFixtureId()).tournamentId(f.getTournamentId())
                .roundName(f.getRoundName()).roundNumber(f.getRoundNumber()).matchNumber(f.getMatchNumber())
                .registration1Id(f.getRegistration1Id()).registration2Id(f.getRegistration2Id())
                .score1(f.getScore1()).score2(f.getScore2()).winnerRegistrationId(f.getWinnerRegistrationId())
                .nextFixtureId(f.getNextFixtureId()).reservationId(f.getReservationId()).status(f.getStatus()).build();
    }
}
