package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.persistence.ClubFriendlyMatchRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.service.ClubMatchmakingService;
import iuh.fit.clubservice.application.service.ClubMatchmakingService.FriendlyResult;
import iuh.fit.clubservice.application.service.ClubMatchmakingService.Side;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubFriendlyMatch;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.SportType;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubMatchmakingServiceImplTest {
    @Mock private ClubRepository clubRepository;
    @Mock private ClubMemberRepository memberRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private ClubConversationSync conversationSync;
    @Mock private ClubFriendlyMatchRepository friendlyMatchRepository;

    private ClubMatchmakingServiceImpl service;
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ClubSupport support = new ClubSupport(clubRepository, memberRepository, registrationRepository, conversationSync);
        service = new ClubMatchmakingServiceImpl(support, clubRepository, memberRepository, friendlyMatchRepository);
    }

    private static Club club(String name, int elo, int matches) {
        return Club.builder().clubId(UUID.randomUUID()).ownerId(UUID.randomUUID()).name(name)
                .sportType(SportType.FOOTBALL).active(true).eloRating(elo)
                .winCount(matches).lossCount(0).drawCount(0).matchCount(matches).winRate(matches == 0 ? 0D : 1D).build();
    }

    @Test
    @DisplayName("hồ sơ ghép trận: chủ CLB và quản lý đang hoạt động là người đại diện; CLB giải tán thì không có hồ sơ")
    void profileListsOwnerAndActiveAdmins() {
        Club club = club("FC Bến Nghé", 1260, 4);
        UUID admin = UUID.randomUUID();
        when(clubRepository.findById(club.getClubId())).thenReturn(Optional.of(club));
        when(memberRepository.findByClubIdAndStatus(club.getClubId(), ClubMemberStatus.ACTIVE)).thenReturn(List.of(
                member(club, club.getOwnerId(), ClubRole.OWNER), member(club, admin, ClubRole.ADMIN),
                member(club, UUID.randomUUID(), ClubRole.MEMBER)));

        ClubMatchmakingService.Profile profile = service.getProfile(club.getClubId()).orElseThrow();

        assertThat(profile.managerIds()).containsExactly(club.getOwnerId(), admin);
        assertThat(profile.activeMemberCount()).isEqualTo(3);
        assertThat(profile.eloRating()).isEqualTo(1260);

        club.setActive(false);
        assertThat(service.getProfile(club.getClubId())).isEmpty();
    }

    @Test
    @DisplayName("kết quả giao hữu cộng thắng/thua, đổi ELO cả hai CLB và ghi hai dòng lịch sử; gọi lại thì không tính lần hai")
    void friendlyResultRatesBothClubsOnce() throws ServiceException {
        Club home = club("FC Bến Nghé", 1200, 0);
        Club away = club("FC Thảo Điền", 1200, 12);
        when(friendlyMatchRepository.findBySessionId(sessionId)).thenReturn(List.of());
        when(clubRepository.findById(home.getClubId())).thenReturn(Optional.of(home));
        when(clubRepository.findById(away.getClubId())).thenReturn(Optional.of(away));

        Map<UUID, Integer> ratings = service.applyFriendlyResult(new FriendlyResult(sessionId, SportType.FOOTBALL,
                "FOOTBALL_7", new Side(home.getClubId(), 3), new Side(away.getClubId(), 1)));

        // CLB mới (K = 40) được cộng nhiều hơn CLB lâu năm (K = 24) bị trừ.
        assertThat(ratings).containsEntry(home.getClubId(), 1220).containsEntry(away.getClubId(), 1188);
        assertThat(home.getWinCount()).isEqualTo(1);
        assertThat(home.getMatchCount()).isEqualTo(1);
        assertThat(away.getLossCount()).isEqualTo(1);
        assertThat(away.getMatchCount()).isEqualTo(13);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClubFriendlyMatch>> saved = ArgumentCaptor.forClass(List.class);
        verify(friendlyMatchRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting("clubId", "opponentName", "clubScore", "opponentScore", "result", "eloBefore", "eloAfter")
                .containsExactly(
                        Tuple.tuple(home.getClubId(), "FC Thảo Điền", 3, 1, "WIN", 1200, 1220),
                        Tuple.tuple(away.getClubId(), "FC Bến Nghé", 1, 3, "LOSS", 1200, 1188));

        when(friendlyMatchRepository.findBySessionId(sessionId)).thenReturn(saved.getValue());
        assertThat(service.applyFriendlyResult(new FriendlyResult(sessionId, SportType.FOOTBALL, "FOOTBALL_7",
                new Side(home.getClubId(), 3), new Side(away.getClubId(), 1))))
                .containsEntry(home.getClubId(), 1220).containsEntry(away.getClubId(), 1188);
        verify(clubRepository, Mockito.times(2)).save(any());
    }

    @Test
    @DisplayName("hai bên phải là hai CLB khác nhau")
    void friendlyResultNeedsTwoClubs() {
        UUID clubId = UUID.randomUUID();
        when(friendlyMatchRepository.findBySessionId(sessionId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.applyFriendlyResult(new FriendlyResult(sessionId, SportType.FOOTBALL, null,
                new Side(clubId, 1), new Side(clubId, 0)))).isInstanceOf(ServiceException.class);
        verify(clubRepository, never()).save(any());
    }

    private static ClubMember member(Club club, UUID userId, ClubRole role) {
        return ClubMember.builder().membershipId(UUID.randomUUID()).clubId(club.getClubId()).userId(userId)
                .role(role).status(ClubMemberStatus.ACTIVE).joinedAt(LocalDateTime.now()).build();
    }
}
