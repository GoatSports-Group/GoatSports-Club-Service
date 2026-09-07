package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.*;
import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.*;
import iuh.fit.clubservice.application.service.TournamentService;
import iuh.fit.clubservice.domain.entity.*;
import iuh.fit.clubservice.domain.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
        validateDates(command);
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
        return toResponse(tournament);
    }

    @Override
    @Transactional
    public TournamentRegistrationResponseCommand registerTeam(RegisterTournamentTeamCommand command) throws ServiceException {
        Tournament tournament = requireTournament(command.getTournamentId());
        if (effectiveStatus(tournament) != TournamentStatus.REGISTRATION_OPEN) {
            throw new ServiceException("Giải đấu chưa mở hoặc đã đóng đăng ký.");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(tournament.getRegistrationOpenDate()) || today.isAfter(tournament.getRegistrationCloseDate())) {
            throw new ServiceException("Hiện không nằm trong thời gian đăng ký giải đấu.");
        }
        boolean duplicate = registrationRepository.findByTournamentId(command.getTournamentId()).stream()
                .anyMatch(item -> item.getRegisteredBy().equals(command.getRegisteredBy())
                        && item.getStatus() != RegistrationStatus.CANCELLED
                        && item.getStatus() != RegistrationStatus.REJECTED);
        if (duplicate) throw new ServiceException("Bạn đã đăng ký giải đấu này.");

        long occupied = registrationRepository.findByTournamentId(command.getTournamentId()).stream()
                .filter(item -> item.getStatus() != RegistrationStatus.CANCELLED
                        && item.getStatus() != RegistrationStatus.REJECTED).count();
        if (occupied >= tournament.getMaxParticipants()) throw new ServiceException("Giải đấu đã đủ số lượng đăng ký.");

        RegistrationType type = command.getType() == null ? RegistrationType.INDIVIDUAL : command.getType();
        validateRegistration(type, command);
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

        List<TournamentFixture> fixtures = new ArrayList<>();
        int match = 1;
        if (tournament.getFormat() == TournamentFormat.ROUND_ROBIN) {
            for (int i = 0; i < teams.size(); i++) {
                for (int j = i + 1; j < teams.size(); j++) {
                    fixtures.add(fixture(tournamentId, "Vòng bảng", 1, match++, teams.get(i), teams.get(j)));
                }
            }
        } else {
            Collections.shuffle(teams);
            for (int i = 0; i < teams.size(); i += 2) {
                fixtures.add(fixture(tournamentId, "Vòng 1", 1, match++, teams.get(i),
                        i + 1 < teams.size() ? teams.get(i + 1) : null));
            }
        }
        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        tournamentRepository.save(tournament);
        return fixtureRepository.saveAll(fixtures).stream().map(this::toResponse).toList();
    }

    @Override
    public List<TournamentFixtureResponseCommand> getFixtures(UUID tournamentId) {
        return fixtureRepository.findByTournamentId(tournamentId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public TournamentFixtureResponseCommand updateFixtureResult(UpdateFixtureResultCommand command) throws ServiceException {
        TournamentFixture fixture = fixtureRepository.findById(command.getFixtureId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy trận đấu."));
        Tournament tournament = requireTournament(fixture.getTournamentId());
        assertOrganizer(tournament, command.getActorUserId());
        if (fixture.getRegistration2Id() == null) throw new ServiceException("Trận đấu được miễn thi đấu không cần cập nhật tỷ số.");
        if (command.getScore1() < 0 || command.getScore2() < 0) throw new ServiceException("Tỷ số không được âm.");
        fixture.setScore1(command.getScore1());
        fixture.setScore2(command.getScore2());
        fixture.setWinnerRegistrationId(command.getScore1().equals(command.getScore2()) ? null
                : command.getScore1() > command.getScore2() ? fixture.getRegistration1Id() : fixture.getRegistration2Id());
        fixture.setStatus(MatchStatus.COMPLETED);
        return toResponse(fixtureRepository.save(fixture));
    }

    @Override
    public List<TournamentStandingResponseCommand> getStandings(UUID tournamentId) {
        Map<UUID, int[]> table = new HashMap<>();
        registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED)
                .forEach(registration -> table.put(registration.getRegistrationId(), new int[7]));
        fixtureRepository.findByTournamentId(tournamentId).stream()
                .filter(f -> f.getStatus() == MatchStatus.COMPLETED && f.getRegistration2Id() != null)
                .forEach(f -> applyResult(table, f));

        List<Map.Entry<UUID, int[]>> sorted = table.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, int[]>>comparingInt(e -> e.getValue()[6]).reversed()
                        .thenComparingInt(e -> e.getValue()[4] - e.getValue()[5]).reversed()).toList();
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

    private void validateDates(CreateTournamentCommand c) throws ServiceException {
        if (c.getRegistrationOpenDate() == null || c.getRegistrationCloseDate() == null
                || c.getStartDate() == null || c.getEndDate() == null
                || c.getRegistrationCloseDate().isBefore(c.getRegistrationOpenDate())
                || c.getStartDate().isBefore(c.getRegistrationCloseDate())
                || c.getEndDate().isBefore(c.getStartDate())) {
            throw new ServiceException("Mốc thời gian giải đấu không hợp lệ.");
        }
        if (c.getMaxParticipants() != null && c.getMaxParticipants() < 2) {
            throw new ServiceException("Giải đấu cần ít nhất 2 người hoặc đội.");
        }
    }

    private void validateRegistration(RegistrationType type, RegisterTournamentTeamCommand c) throws ServiceException {
        if (type == RegistrationType.CLUB && c.getClubId() == null) throw new ServiceException("Đăng ký theo câu lạc bộ cần clubId.");
        if (type == RegistrationType.INDIVIDUAL && c.getPlayerId() != null && !c.getPlayerId().equals(c.getRegisteredBy())) {
            throw new ServiceException("Người chơi chỉ có thể tự đăng ký cá nhân.");
        }
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
