package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.external.CommunityHighlightPublisher;
import iuh.fit.clubservice.application.port.persistence.ClubFriendlyMatchRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Khoe thanh tich giai len Cong dong: chi chu / quan tri CLB, gan the doi da ra san truoc roi den thanh vien. */
@ExtendWith(MockitoExtension.class)
class ClubTournamentShareTest {
    @Mock private ClubRepository clubRepository;
    @Mock private ClubMemberRepository memberRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentFixtureRepository fixtureRepository;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private ClubFriendlyMatchRepository friendlyMatchRepository;
    @Mock private TournamentLineupRepository lineupRepository;
    @Mock private ClubConversationSync conversationSync;
    @Mock private CommunityHighlightPublisher highlightPublisher;

    private final UUID clubId = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID tournamentId = UUID.randomUUID();
    private ClubMatchServiceImpl service;

    @BeforeEach
    void setUp() {
        ClubSupport support = new ClubSupport(clubRepository, memberRepository, registrationRepository, conversationSync);
        service = new ClubMatchServiceImpl(support, registrationRepository, fixtureRepository, tournamentRepository,
                friendlyMatchRepository, lineupRepository, highlightPublisher);
        Club club = Club.builder().clubId(clubId).ownerId(owner).name("Weekend Club").sportType(SportType.FOOTBALL)
                .active(true).build();
        lenient().when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
    }

    private void clubPlayedTheTournament() {
        LocalDate today = LocalDate.now();
        TournamentRegistration registration = TournamentRegistration.builder().registrationId(UUID.randomUUID())
                .tournamentId(tournamentId).clubId(clubId).teamName("Code FC").status(RegistrationStatus.CONFIRMED).build();
        when(registrationRepository.findByClubId(clubId)).thenReturn(List.of(registration));
        when(registrationRepository.findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(registration));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(Tournament.builder()
                .tournamentId(tournamentId).name("Giải Phong Trào Mùa Thu").sportType(SportType.FOOTBALL)
                .format(TournamentFormat.SINGLE_ELIMINATION).status(TournamentStatus.PUBLISHED)
                .registrationOpenDate(today.minusDays(10)).registrationCloseDate(today.minusDays(1))
                .startDate(today.plusDays(1)).endDate(today.plusDays(3)).build()));
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of());
        when(lineupRepository.findByRegistrationId(registration.getRegistrationId())).thenReturn(List.of(
                TournamentLineup.builder().playerId(player).memberStatus(LineupMemberStatus.ACCEPTED).build(),
                TournamentLineup.builder().playerId(UUID.randomUUID()).memberStatus(LineupMemberStatus.DECLINED).build()));
        when(memberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)).thenReturn(List.of(
                ClubMember.builder().userId(admin).build(), ClubMember.builder().userId(member).build(),
                ClubMember.builder().userId(player).build()));
    }

    @Test
    void anAdminSharesAndTagsTheSquadFirstThenTheOtherMembers() throws ServiceException {
        when(memberRepository.findByClubIdAndUserId(clubId, admin)).thenReturn(Optional.of(ClubMember.builder()
                .clubId(clubId).userId(admin).role(ClubRole.ADMIN).status(ClubMemberStatus.ACTIVE).build()));
        clubPlayedTheTournament();
        UUID postId = UUID.randomUUID();
        when(highlightPublisher.publishTournament(any())).thenReturn(postId);

        assertThat(service.shareTournament(clubId, tournamentId, admin, "  Lần đầu dự giải!  ", "Cố lên cả nhà"))
                .isEqualTo(postId);

        ArgumentCaptor<CommunityHighlightPublisher.TournamentHighlight> captor =
                ArgumentCaptor.forClass(CommunityHighlightPublisher.TournamentHighlight.class);
        verify(highlightPublisher).publishTournament(captor.capture());
        CommunityHighlightPublisher.TournamentHighlight highlight = captor.getValue();
        assertThat(highlight.title()).isEqualTo("Lần đầu dự giải!");
        assertThat(highlight.headline()).isEqualTo("Giải Phong Trào Mùa Thu");
        assertThat(highlight.subtitle()).startsWith("Code FC · ");
        assertThat(highlight.result()).isEqualTo("Đã ghi danh tham dự");
        assertThat(highlight.clubName()).isEqualTo("Weekend Club");
        // Nguoi da tu choi vao doi va chinh nguoi dang khong duoc gan the.
        assertThat(highlight.tagged()).containsExactly(player, member);
    }

    @Test
    void aPlainMemberCannotShowOffTheClubsTournament() throws ServiceException {
        when(memberRepository.findByClubIdAndUserId(clubId, member)).thenReturn(Optional.of(ClubMember.builder()
                .clubId(clubId).userId(member).role(ClubRole.MEMBER).status(ClubMemberStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.shareTournament(clubId, tournamentId, member, "Vô địch!", null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("Chỉ chủ CLB hoặc quản trị viên");
        verify(highlightPublisher, never()).publishTournament(any());
    }
}
