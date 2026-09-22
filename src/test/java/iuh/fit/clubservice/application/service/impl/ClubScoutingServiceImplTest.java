package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import iuh.fit.clubservice.domain.enums.SportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClubScoutingServiceImplTest {
    @Mock private ClubRepository clubRepository;
    @Mock private ClubMemberRepository memberRepository;
    @Mock private ClubInvitationRepository invitationRepository;
    @Mock private PlayerProfileProvider playerProfileProvider;

    private ClubScoutingServiceImpl service;
    private final UUID clubId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClubScoutingServiceImpl(
                clubRepository, memberRepository, invitationRepository, playerProfileProvider);
        when(memberRepository.findByClubId(clubId)).thenReturn(List.of());
        when(invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING)).thenReturn(List.of());
        when(playerProfileProvider.findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of());
    }

    private Club club(String city) {
        return Club.builder().clubId(clubId).ownerId(ownerId).name("CLB test")
                .sportType(SportType.BADMINTON).city(city).active(true).build();
    }

    private PlayerProfileProvider.ScoutingCandidateView candidate(
            UUID userId, double distanceKm, int matchCount, Double playRadiusKm) {
        return new PlayerProfileProvider.ScoutingCandidateView(
                userId, "player", "Nguoi choi", null, SkillLevel.INTERMEDIATE, 1200,
                matchCount, 0.5, playRadiusKm, distanceKm);
    }

    private ClubMember member(UUID userId, ClubMemberStatus status) {
        return ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId).userId(userId)
                .role(ClubRole.MEMBER).status(status).build();
    }

    @Test
    @DisplayName("CLB chua dat toa do thi lay tam tinh/thanh tu city")
    void fallsBackToProvinceCentreWhenClubHasNoCoordinates() throws Exception {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("khanh-hoa")));

        service.findCandidates(clubId, ownerId, null, null);

        ArgumentCaptor<Double> latitude = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> longitude = ArgumentCaptor.forClass(Double.class);
        verify(playerProfileProvider).findScoutingCandidates(
                any(), latitude.capture(), longitude.capture(), anyDouble(), anyInt());
        assertThat(latitude.getValue()).isCloseTo(12.239, within(0.01));
        assertThat(longitude.getValue()).isCloseTo(109.196, within(0.01));
    }

    @Test
    @DisplayName("CLB co toa do rieng thi uu tien toa do do")
    void clubCoordinatesOverrideTheProvinceCentre() throws Exception {
        Club club = club("khanh-hoa");
        club.setLatitude(10.776);
        club.setLongitude(106.701);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));

        service.findCandidates(clubId, ownerId, null, null);

        ArgumentCaptor<Double> latitude = ArgumentCaptor.forClass(Double.class);
        verify(playerProfileProvider).findScoutingCandidates(
                any(), latitude.capture(), anyDouble(), anyDouble(), anyInt());
        assertThat(latitude.getValue()).isCloseTo(10.776, within(0.01));
    }

    @Test
    @DisplayName("Loai nguoi da o trong CLB, dang cho duyet, bi cam va da co loi moi treo")
    void alreadyRelatedPlayersAreExcluded() throws Exception {
        UUID activeMember = UUID.randomUUID();
        UUID pendingMember = UUID.randomUUID();
        UUID bannedMember = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        UUID leftMember = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(memberRepository.findByClubId(clubId)).thenReturn(List.of(
                member(activeMember, ClubMemberStatus.ACTIVE),
                member(pendingMember, ClubMemberStatus.PENDING),
                member(bannedMember, ClubMemberStatus.BANNED),
                member(leftMember, ClubMemberStatus.LEFT)));
        when(invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING)).thenReturn(List.of(
                ClubInvitation.builder().invitationId(UUID.randomUUID()).clubId(clubId).inviteeId(invited)
                        .status(ClubInvitationStatus.PENDING).build()));
        when(playerProfileProvider.findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(
                        candidate(activeMember, 1, 5, null),
                        candidate(pendingMember, 1, 5, null),
                        candidate(bannedMember, 1, 5, null),
                        candidate(invited, 1, 5, null),
                        candidate(ownerId, 1, 5, null),
                        candidate(leftMember, 2, 5, null),
                        candidate(stranger, 3, 5, null)));

        var result = service.findCandidates(clubId, ownerId, null, null);

        // Nguoi da roi CLB van moi lai duoc, nen con dung hai nguoi.
        assertThat(result).extracting("userId").containsExactlyInAnyOrder(leftMember, stranger);
    }

    @Test
    @DisplayName("Gan hon va nam trong ban kinh nguoi choi thi diem phu hop cao hon")
    void closerAndReachablePlayersRankHigher() throws Exception {
        UUID near = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(playerProfileProvider.findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(
                        candidate(far, 24, 0, 1.0),
                        candidate(near, 2, 20, 30.0)));

        var result = service.findCandidates(clubId, ownerId, 25D, null);

        assertThat(result).extracting("userId").containsExactly(near, far);
        assertThat(result.getFirst().getFitScore()).isGreaterThan(result.getLast().getFitScore());
        assertThat(result.getFirst().isWithinPlayerRadius()).isTrue();
        assertThat(result.getLast().isWithinPlayerRadius()).isFalse();
    }

    @Test
    @DisplayName("Thanh vien thuong khong duoc dung scouting")
    void plainMemberCannotScout() {
        UUID memberId = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(memberRepository.findByClubIdAndUserId(clubId, memberId)).thenReturn(Optional.of(
                member(memberId, ClubMemberStatus.ACTIVE)));

        assertThatThrownBy(() -> service.findCandidates(clubId, memberId, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("quản lý");
        verify(playerProfileProvider, never())
                .findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("Quan ly CLB dung duoc scouting")
    void activeManagerCanScout() throws Exception {
        UUID managerId = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(memberRepository.findByClubIdAndUserId(clubId, managerId)).thenReturn(Optional.of(
                ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId).userId(managerId)
                        .role(ClubRole.ADMIN).status(ClubMemberStatus.ACTIVE).build()));

        service.findCandidates(clubId, managerId, null, null);

        verify(playerProfileProvider)
                .findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("CLB khong ro tinh/thanh thi bao loi thay vi tra danh sach rong kho hieu")
    void clubWithoutAnyLocationReportsAClearError() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(null)));

        assertThatThrownBy(() -> service.findCandidates(clubId, ownerId, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("vị trí");
    }

    @Test
    @DisplayName("CLB da giai tan thi khong scouting duoc")
    void disbandedClubCannotScout() {
        Club club = club("ho-chi-minh");
        club.setDisbandedAt(LocalDateTime.now());
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));

        assertThatThrownBy(() -> service.findCandidates(clubId, ownerId, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("giải tán");
    }
}
