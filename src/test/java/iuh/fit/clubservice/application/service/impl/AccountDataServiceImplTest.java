package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubShortlistRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDataServiceImplTest {
    @Mock private ClubMemberRepository memberRepository;
    @Mock private ClubInvitationRepository invitationRepository;
    @Mock private ClubShortlistRepository shortlistRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentLineupRepository lineupRepository;
    @Mock private TournamentRepository tournamentRepository;
    @InjectMocks private AccountDataServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    private ClubMember member(ClubRole role, ClubMemberStatus status) {
        ClubMember member = new ClubMember();
        member.setUserId(userId);
        member.setClubId(UUID.randomUUID());
        member.setRole(role);
        member.setStatus(status);
        return member;
    }

    @Test
    void clubOwnersAndPlayersInARunningTournamentCannotDeleteYet() {
        UUID running = UUID.randomUUID();
        UUID finished = UUID.randomUUID();
        when(memberRepository.findByUserIdAndStatus(userId, ClubMemberStatus.ACTIVE))
                .thenReturn(List.of(member(ClubRole.OWNER, ClubMemberStatus.ACTIVE)));
        when(registrationRepository.findTournamentIdsByParticipant(eq(userId), any())).thenReturn(List.of(finished, running));
        Tournament done = new Tournament();
        done.setStatus(TournamentStatus.COMPLETED);
        Tournament live = new Tournament();
        live.setStatus(TournamentStatus.IN_PROGRESS);
        when(tournamentRepository.findById(finished)).thenReturn(Optional.of(done));
        when(tournamentRepository.findById(running)).thenReturn(Optional.of(live));

        assertThat(service.deletionBlockers(userId)).containsExactly("CLUB_OWNER", "TOURNAMENT_IN_PROGRESS");
    }

    @Test
    void anOrdinaryMemberWithOnlyFinishedTournamentsCanDelete() {
        when(memberRepository.findByUserIdAndStatus(userId, ClubMemberStatus.ACTIVE))
                .thenReturn(List.of(member(ClubRole.ADMIN, ClubMemberStatus.ACTIVE)));
        when(registrationRepository.findTournamentIdsByParticipant(eq(userId), any())).thenReturn(List.of());

        assertThat(service.deletionBlockers(userId)).isEmpty();
    }

    @Test
    void erasingLeavesEveryClubAndWithdrawsPendingInvitations() {
        ClubMember active = member(ClubRole.MEMBER, ClubMemberStatus.ACTIVE);
        ClubMember request = member(ClubRole.MEMBER, ClubMemberStatus.PENDING);
        ClubInvitation invitation = new ClubInvitation();
        invitation.setStatus(ClubInvitationStatus.PENDING);
        when(memberRepository.findByUserIdAndStatus(userId, ClubMemberStatus.ACTIVE)).thenReturn(List.of(active));
        when(memberRepository.findByUserIdAndStatus(userId, ClubMemberStatus.PENDING)).thenReturn(List.of(request));
        when(invitationRepository.findByInviteeIdAndStatus(userId, ClubInvitationStatus.PENDING)).thenReturn(List.of(invitation));
        when(lineupRepository.findByPlayerIdAndMemberStatus(userId, LineupMemberStatus.INVITED)).thenReturn(List.of());

        service.eraseUserData(userId);

        assertThat(active.getStatus()).isEqualTo(ClubMemberStatus.LEFT);
        assertThat(request.getStatus()).isEqualTo(ClubMemberStatus.LEFT);
        assertThat(invitation.getStatus()).isEqualTo(ClubInvitationStatus.CANCELLED);
        verify(memberRepository).save(active);
        verify(invitationRepository).save(invitation);
        verify(shortlistRepository).deleteByUserId(userId);
    }
}
