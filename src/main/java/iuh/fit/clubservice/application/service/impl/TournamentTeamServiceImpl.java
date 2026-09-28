package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand.LineupItem;
import iuh.fit.clubservice.application.dto.response.TournamentRegistrationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.TournamentTeamService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.service.SportParticipationPolicy.PlayFormat;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TournamentTeamServiceImpl implements TournamentTeamService {
    private final TournamentRegistrationSupport support;
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentLineupRepository lineupRepository;
    private final ClubRepository clubRepository;

    @Override
    public List<TeamInvitation> getMyInvitations(UUID userId) {
        List<TeamInvitation> result = new ArrayList<>();
        for (TournamentLineup invite : lineupRepository.findByPlayerIdAndMemberStatus(userId, LineupMemberStatus.INVITED)) {
            Optional<TournamentRegistration> registration = registrationRepository.findById(invite.getRegistrationId())
                    .filter(item -> TournamentLifecyclePolicy.holdsSeat(item.getStatus()));
            if (registration.isEmpty()) continue;
            Optional<Tournament> tournament = tournamentRepository.findById(registration.get().getTournamentId());
            // Lời mời chỉ còn ý nghĩa khi giải còn mở đăng ký.
            if (tournament.isEmpty() || support.statusOf(tournament.get()) != TournamentStatus.REGISTRATION_OPEN) continue;
            PlayFormat format = support.formatOf(tournament.get());
            int accepted = (int) support.acceptedCount(lineupRepository.findByRegistrationId(invite.getRegistrationId()));
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
        Tournament tournament = support.lockTournament(tournamentId);
        TournamentRegistration registration = support.requireRegistration(tournament, registrationId);
        List<TournamentLineup> lineup = lineupRepository.findByRegistrationId(registrationId);
        TournamentLineup mine = lineup.stream()
                .filter(row -> userId.equals(row.getPlayerId()) && row.getMemberStatus() == LineupMemberStatus.INVITED)
                .findFirst().orElseThrow(() -> new ServiceException("Bạn không có lời mời đang chờ trong đội này."));
        if (!TournamentLifecyclePolicy.holdsSeat(registration.getStatus())) {
            throw new ServiceException("Đội này không còn tham gia giải.");
        }

        if (!accept) {
            answer(mine, LineupMemberStatus.DECLINED);
            support.publish(TournamentEventPublisher.TEAM_ANSWERED, tournament, registration,
                    registration.getRegisteredBy(), userId, Map.of("accepted", "false"));
            return support.toResponse(registration);
        }

        support.assertRegistrationOpen(tournament, "Giải đã đóng đăng ký nên không nhận thêm thành viên.");
        PlayFormat format = support.formatOf(tournament);
        long accepted = support.acceptedCount(lineup);
        if (accepted >= format.rosterMax()) {
            throw new ServiceException("Đội đã đủ tối đa " + format.rosterMax() + " người.");
        }
        // Người nhận lời phải qua đủ các điều kiện như tự đăng ký: chưa ở đội khác, còn trong CLB, đạt điều kiện giải.
        support.assertFreeToPlay(userId, support.seatHolders(tournamentId), registrationId, "Bạn");
        if (registration.getClubId() != null) {
            Club club = clubRepository.findById(registration.getClubId()).orElse(null);
            if (club == null || !support.activeMembers(club).contains(userId)) {
                throw new ServiceException("Bạn không còn là thành viên của câu lạc bộ đại diện đội này.");
            }
        }
        support.ensureProfile(tournament, userId);
        support.assertEligible(tournament, userId, registration.getClubId(), "");

        answer(mine, LineupMemberStatus.ACCEPTED);
        declineOtherInvitations(tournament, userId, registrationId);
        support.publish(TournamentEventPublisher.TEAM_ANSWERED, tournament, registration,
                registration.getRegisteredBy(), userId, Map.of("accepted", "true"));

        // Người này làm đội vừa đủ số tối thiểu: đội chuyển sang đóng lệ phí (hoặc xác nhận nếu miễn phí).
        if (registration.getStatus() == RegistrationStatus.PENDING_MEMBERS && accepted + 1 >= format.rosterMin()) {
            support.advanceWhenReady(tournament, registration);
        } else if (registration.getStatus() == RegistrationStatus.CONFIRMED) {
            support.syncConversation(tournament);
        }
        return support.toResponse(registration);
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentRegistrationResponseCommand inviteMembers(UUID tournamentId, UUID registrationId, UUID captainId,
                                                               List<LineupItem> invitees) throws ServiceException {
        Tournament tournament = support.lockTournament(tournamentId);
        TournamentRegistration registration = support.requireRegistration(tournament, registrationId);
        assertCaptainManagesTeam(registration, captainId);
        support.assertRegistrationOpen(tournament, "Giải đã đóng đăng ký nên không mời thêm được.");
        if (invitees == null || invitees.isEmpty()) throw new ServiceException("Chọn ít nhất một người để mời.");

        PlayFormat format = support.formatOf(tournament);
        List<TournamentLineup> lineup = lineupRepository.findByRegistrationId(registrationId);
        Map<UUID, TournamentLineup> byPlayer = new HashMap<>();
        lineup.forEach(row -> byPlayer.put(row.getPlayerId(), row));
        long taken = lineup.stream().filter(row -> row.getMemberStatus() != LineupMemberStatus.DECLINED).count();
        Set<Integer> usedShirts = new HashSet<>();
        lineup.stream().filter(row -> row.getMemberStatus() != LineupMemberStatus.DECLINED && row.getShirtNumber() != null)
                .forEach(row -> usedShirts.add(row.getShirtNumber()));
        TeamInvites.assertValid(invitees, captainId, null, usedShirts, (int) (format.rosterMax() - taken));

        List<TournamentRegistration> active = support.seatHolders(tournamentId);
        Set<UUID> clubMembers = registration.getClubId() == null ? null
                : clubRepository.findById(registration.getClubId()).map(support::activeMembers).orElse(Set.of());
        List<TournamentLineup> changed = new ArrayList<>();
        for (LineupItem invitee : invitees) {
            TournamentLineup existing = byPlayer.get(invitee.getPlayerId());
            if (existing != null && existing.getMemberStatus() != LineupMemberStatus.DECLINED) {
                throw new ServiceException(TeamInvites.label(invitee) + " đã có trong đội hoặc đang được mời.");
            }
            if (clubMembers != null && !clubMembers.contains(invitee.getPlayerId())) {
                throw new ServiceException(TeamInvites.label(invitee) + " không phải thành viên đang hoạt động của câu lạc bộ.");
            }
            support.assertFreeToPlay(invitee.getPlayerId(), active, null, TeamInvites.label(invitee));
            if (existing != null) {
                // Mời lại người đã từ chối: dùng lại dòng cũ vì (registration, player) là unique.
                existing.setMemberStatus(LineupMemberStatus.INVITED);
                existing.setRespondedAt(null);
                existing.setLineupRole(TeamInvites.role(invitee));
                existing.setShirtNumber(invitee.getShirtNumber());
                existing.setPlayerName(invitee.getPlayerName());
                changed.add(existing);
            } else {
                changed.add(TeamInvites.newRow(registrationId, invitee));
            }
        }
        lineupRepository.saveAll(changed);
        invitees.forEach(invitee -> support.publish(TournamentEventPublisher.TEAM_INVITED, tournament, registration,
                invitee.getPlayerId(), captainId, Map.of()));
        return support.toResponse(registration);
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentRegistrationResponseCommand removeMember(UUID tournamentId, UUID registrationId, UUID actorId,
                                                              UUID playerId) throws ServiceException {
        Tournament tournament = support.lockTournament(tournamentId);
        TournamentRegistration registration = support.requireRegistration(tournament, registrationId);
        boolean captain = registration.getRegisteredBy().equals(actorId);
        boolean leaving = actorId.equals(playerId);
        if (!captain && !leaving) throw new ServiceException("Chỉ đội trưởng mới bỏ được thành viên khỏi đội.");
        if (captain && leaving) throw new ServiceException("Đội trưởng không rời đội được; hãy rút đăng ký của đội.");
        if (!TournamentLifecyclePolicy.holdsSeat(registration.getStatus())) {
            throw new ServiceException("Đội này không còn tham gia giải.");
        }
        if (TournamentLifecyclePolicy.isStarted(tournament)) {
            throw new ServiceException("Giải đã bắt đầu nên không đổi đội hình được.");
        }

        List<TournamentLineup> lineup = lineupRepository.findByRegistrationId(registrationId);
        TournamentLineup target = lineup.stream()
                .filter(row -> playerId.equals(row.getPlayerId()) && row.getMemberStatus() != LineupMemberStatus.DECLINED)
                .findFirst().orElseThrow(() -> new ServiceException("Người này không có trong đội."));
        // Đội đã giữ suất chính thức (chờ phí / đã xác nhận) không được tụt dưới số người tối thiểu.
        if (target.getMemberStatus() == LineupMemberStatus.ACCEPTED
                && (registration.getStatus() == RegistrationStatus.PENDING_PAYMENT
                || registration.getStatus() == RegistrationStatus.CONFIRMED)) {
            PlayFormat format = support.formatOf(tournament);
            long remaining = support.acceptedCount(lineup) - 1;
            if (remaining < format.rosterMin()) {
                throw new ServiceException("Đội sẽ chỉ còn " + remaining + "/" + format.rosterMin()
                        + " người tối thiểu. Mời người thay thế trước khi bỏ bớt.");
            }
        }
        lineupRepository.deleteById(target.getLineupId());
        if (registration.getStatus() == RegistrationStatus.CONFIRMED) support.syncConversation(tournament);
        return support.toResponse(registration);
    }

    private void answer(TournamentLineup row, LineupMemberStatus status) {
        row.setMemberStatus(status);
        row.setRespondedAt(LocalDateTime.now());
        lineupRepository.save(row);
    }

    /** Đã vào một đội thì các lời mời khác trong cùng giải không còn ý nghĩa. */
    private void declineOtherInvitations(Tournament tournament, UUID userId, UUID keepRegistrationId) {
        for (TournamentLineup other : lineupRepository.findByPlayerIdAndMemberStatus(userId, LineupMemberStatus.INVITED)) {
            if (other.getRegistrationId().equals(keepRegistrationId)) continue;
            boolean sameTournament = registrationRepository.findById(other.getRegistrationId())
                    .map(item -> item.getTournamentId().equals(tournament.getTournamentId())).orElse(false);
            if (sameTournament) answer(other, LineupMemberStatus.DECLINED);
        }
    }

    private void assertCaptainManagesTeam(TournamentRegistration registration, UUID captainId) throws ServiceException {
        if (registration.getType() == RegistrationType.INDIVIDUAL) throw new ServiceException("Đăng ký cá nhân không có đội.");
        if (!registration.getRegisteredBy().equals(captainId)) {
            throw new ServiceException("Chỉ đội trưởng mới mời được thành viên.");
        }
        if (!TournamentLifecyclePolicy.holdsSeat(registration.getStatus())) {
            throw new ServiceException("Đội này không còn tham gia giải.");
        }
    }
}
