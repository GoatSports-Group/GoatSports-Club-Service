package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubShortlistRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.AccountDataService;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDataServiceImpl implements AccountDataService {
    private static final List<RegistrationStatus> HOLDING_A_SPOT = List.of(
            RegistrationStatus.PENDING_MEMBERS, RegistrationStatus.PENDING_ELIGIBILITY,
            RegistrationStatus.PENDING_PAYMENT, RegistrationStatus.CONFIRMED);

    private final ClubMemberRepository memberRepository;
    private final ClubInvitationRepository invitationRepository;
    private final ClubShortlistRepository shortlistRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentLineupRepository lineupRepository;
    private final TournamentRepository tournamentRepository;

    @Override
    public List<String> deletionBlockers(UUID userId) {
        List<String> blockers = new ArrayList<>();
        // Chủ CLB phải chuyển quyền chủ trước, nếu không CLB mất người quản lý.
        if (memberRepository.findByUserIdAndStatus(userId, ClubMemberStatus.ACTIVE).stream()
                .anyMatch(member -> member.getRole() == ClubRole.OWNER)) {
            blockers.add("CLUB_OWNER");
        }
        boolean inRunningTournament = registrationRepository.findTournamentIdsByParticipant(userId, HOLDING_A_SPOT)
                .stream()
                .map(tournamentRepository::findById)
                .flatMap(java.util.Optional::stream)
                .anyMatch(tournament -> tournament.getStatus() != TournamentStatus.COMPLETED
                        && tournament.getStatus() != TournamentStatus.CANCELLED);
        if (inRunningTournament) blockers.add("TOURNAMENT_IN_PROGRESS");
        return blockers;
    }

    @Override
    @Transactional
    public void eraseUserData(UUID userId) {
        // Kết quả trận, đăng ký giải đã xong giữ lại (chỉ còn userId); nhóm chat CLB do social-service tự gỡ.
        for (ClubMemberStatus status : List.of(ClubMemberStatus.ACTIVE, ClubMemberStatus.PENDING)) {
            for (ClubMember member : memberRepository.findByUserIdAndStatus(userId, status)) {
                member.setStatus(ClubMemberStatus.LEFT);
                memberRepository.save(member);
            }
        }
        for (ClubInvitation invitation : invitationRepository.findByInviteeIdAndStatus(userId, ClubInvitationStatus.PENDING)) {
            invitation.setStatus(ClubInvitationStatus.CANCELLED);
            invitationRepository.save(invitation);
        }
        for (TournamentLineup lineup : lineupRepository.findByPlayerIdAndMemberStatus(userId, LineupMemberStatus.INVITED)) {
            lineup.setMemberStatus(LineupMemberStatus.DECLINED);
            lineupRepository.save(lineup);
        }
        shortlistRepository.deleteByUserId(userId);
    }
}
