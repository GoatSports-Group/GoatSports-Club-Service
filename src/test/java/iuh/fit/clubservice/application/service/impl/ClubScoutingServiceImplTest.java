package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.ScoutingQueryCommand;
import iuh.fit.clubservice.application.dto.response.PlayerClubRelation;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider.ScoutingFilter;
import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.ClubShortlistRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.ClubShortlistEntry;
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

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClubScoutingServiceImplTest {
    @Mock private ClubRepository clubRepository;
    @Mock private ClubMemberRepository memberRepository;
    @Mock private ClubInvitationRepository invitationRepository;
    @Mock private ClubShortlistRepository shortlistRepository;
    @Mock private PlayerProfileProvider playerProfileProvider;

    private ClubScoutingServiceImpl service;
    private final UUID clubId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws ServiceException {
        service = new ClubScoutingServiceImpl(
                clubRepository, memberRepository, invitationRepository, shortlistRepository, playerProfileProvider);
        when(memberRepository.findByClubId(clubId)).thenReturn(List.of());
        when(invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING)).thenReturn(List.of());
        when(shortlistRepository.findByClubId(clubId)).thenReturn(List.of());
        when(shortlistRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(playerProfileProvider.findScoutingCandidates(any())).thenReturn(List.of());
        when(playerProfileProvider.lookupScoutingProfiles(any(), anyDouble(), anyDouble(), any())).thenReturn(List.of());
    }

    private Club club(String city) {
        return Club.builder().clubId(clubId).ownerId(ownerId).name("CLB test")
                .sportType(SportType.BADMINTON).city(city).active(true).build();
    }

    private PlayerProfileProvider.ScoutingCandidateView candidate(
            UUID userId, double distanceKm, int matchCount, Double playRadiusKm) {
        return new PlayerProfileProvider.ScoutingCandidateView(
                userId, "player", "Nguoi choi", null, true, SkillLevel.INTERMEDIATE, 1200, Set.of("Đánh đôi"),
                null, "Hồ Chí Minh", playRadiusKm, 3, 2, 0, matchCount, 0.5, List.of(), distanceKm);
    }

    private PlayerProfileProvider.ScoutingCandidateView hidden(UUID userId) {
        return new PlayerProfileProvider.ScoutingCandidateView(
                userId, "hidden", "An danh", null, false, null, null, Set.of(),
                null, null, null, null, null, null, null, null, List.of(), null);
    }

    private ClubMember member(UUID userId, ClubMemberStatus status) {
        return ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId).userId(userId)
                .role(ClubRole.MEMBER).status(status).build();
    }

    private ScoutingFilter capturedFilter() throws ServiceException {
        ArgumentCaptor<ScoutingFilter> filter = ArgumentCaptor.forClass(ScoutingFilter.class);
        verify(playerProfileProvider).findScoutingCandidates(filter.capture());
        return filter.getValue();
    }

    @Test
    @DisplayName("CLB chua dat toa do thi lay tam tinh/thanh tu city")
    void fallsBackToProvinceCentreWhenClubHasNoCoordinates() throws Exception {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("khanh-hoa")));

        service.findCandidates(clubId, ownerId, null);

        ScoutingFilter filter = capturedFilter();
        assertThat(filter.latitude()).isCloseTo(12.239, within(0.01));
        assertThat(filter.longitude()).isCloseTo(109.196, within(0.01));
    }

    @Test
    @DisplayName("CLB co toa do rieng thi uu tien toa do do")
    void clubCoordinatesOverrideTheProvinceCentre() throws Exception {
        Club club = club("khanh-hoa");
        club.setLatitude(10.776);
        club.setLongitude(106.701);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));

        service.findCandidates(clubId, ownerId, null);

        assertThat(capturedFilter().latitude()).isCloseTo(10.776, within(0.01));
    }

    @Test
    @DisplayName("Bo loc trinh do, vi tri, ngay ranh duoc chuyen nguyen sang auth-service; ban kinh bi chan tran")
    void filtersArePassedThroughAndRadiusIsClamped() throws Exception {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));

        service.findCandidates(clubId, ownerId, new ScoutingQueryCommand(
                999D, 10, Set.of(SkillLevel.ADVANCED), "Tiền đạo", DayOfWeek.SATURDAY));

        ScoutingFilter filter = capturedFilter();
        assertThat(filter.radiusKm()).isEqualTo(200);
        assertThat(filter.skillLevels()).containsExactly(SkillLevel.ADVANCED);
        assertThat(filter.position()).isEqualTo("Tiền đạo");
        assertThat(filter.availableOn()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(filter.sportType()).isEqualTo(SportType.BADMINTON);
    }

    @Test
    @DisplayName("Loai nguoi da o trong CLB, dang cho duyet, bi cam, da co loi moi treo va ho so bi an")
    void alreadyRelatedAndHiddenPlayersAreExcluded() throws Exception {
        UUID activeMember = UUID.randomUUID();
        UUID pendingMember = UUID.randomUUID();
        UUID bannedMember = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        UUID leftMember = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID hiddenPlayer = UUID.randomUUID();

        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(memberRepository.findByClubId(clubId)).thenReturn(List.of(
                member(activeMember, ClubMemberStatus.ACTIVE),
                member(pendingMember, ClubMemberStatus.PENDING),
                member(bannedMember, ClubMemberStatus.BANNED),
                member(leftMember, ClubMemberStatus.LEFT)));
        when(invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING)).thenReturn(List.of(
                ClubInvitation.builder().invitationId(UUID.randomUUID()).clubId(clubId).inviteeId(invited)
                        .status(ClubInvitationStatus.PENDING).build()));
        when(playerProfileProvider.findScoutingCandidates(any())).thenReturn(List.of(
                candidate(activeMember, 1, 5, null),
                candidate(pendingMember, 1, 5, null),
                candidate(bannedMember, 1, 5, null),
                candidate(invited, 1, 5, null),
                candidate(ownerId, 1, 5, null),
                candidate(leftMember, 2, 5, null),
                candidate(stranger, 3, 5, null),
                hidden(hiddenPlayer)));

        var result = service.findCandidates(clubId, ownerId, null);

        // Nguoi da roi CLB van moi lai duoc, nen con dung hai nguoi.
        assertThat(result).extracting("userId").containsExactlyInAnyOrder(leftMember, stranger);
    }

    @Test
    @DisplayName("Gan hon va nam trong ban kinh nguoi choi thi diem phu hop cao hon; danh dau nguoi dang theo doi")
    void closerAndReachablePlayersRankHigher() throws Exception {
        UUID near = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(shortlistRepository.findByClubId(clubId)).thenReturn(List.of(
                ClubShortlistEntry.builder().clubId(clubId).userId(far).build()));
        when(playerProfileProvider.findScoutingCandidates(any())).thenReturn(List.of(
                candidate(far, 24, 0, 1.0),
                candidate(near, 2, 20, 30.0)));

        var result = service.findCandidates(clubId, ownerId, new ScoutingQueryCommand(25D, null, null, null, null));

        assertThat(result).extracting("userId").containsExactly(near, far);
        assertThat(result.getFirst().getFitScore()).isGreaterThan(result.getLast().getFitScore());
        assertThat(result.getFirst().isWithinPlayerRadius()).isTrue();
        assertThat(result.getLast().isWithinPlayerRadius()).isFalse();
        assertThat(result.getFirst().isShortlisted()).isFalse();
        assertThat(result.getLast().isShortlisted()).isTrue();
        assertThat(result.getFirst().getPreferredPositions()).containsExactly("Đánh đôi");
    }

    @Test
    @DisplayName("Diem phu hop khong doi theo ban kinh tim kiem, va khop giua tab kham pha va theo doi")
    void fitScoreDoesNotDependOnTheSearchRadius() throws Exception {
        UUID player = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(playerProfileProvider.findScoutingCandidates(any())).thenReturn(List.of(candidate(player, 3.3, 36, 10.0)));
        when(shortlistRepository.findByClubId(clubId)).thenReturn(List.of(
                ClubShortlistEntry.builder().clubId(clubId).userId(player).build()));
        when(playerProfileProvider.lookupScoutingProfiles(any(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(candidate(player, 3.3, 36, 10.0)));

        int within10 = service.findCandidates(clubId, ownerId, new ScoutingQueryCommand(10D, null, null, null, null))
                .getFirst().getFitScore();
        int within100 = service.findCandidates(clubId, ownerId, new ScoutingQueryCommand(100D, null, null, null, null))
                .getFirst().getFitScore();
        int onShortlist = service.getShortlist(clubId, ownerId).getFirst().getPlayer().getFitScore();

        assertThat(within10).isEqualTo(within100).isEqualTo(onShortlist).isEqualTo(93);
    }

    @Test
    @DisplayName("Thanh vien thuong khong duoc dung scouting")
    void plainMemberCannotScout() throws ServiceException {
        UUID memberId = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(memberRepository.findByClubIdAndUserId(clubId, memberId)).thenReturn(Optional.of(
                member(memberId, ClubMemberStatus.ACTIVE)));

        assertThatThrownBy(() -> service.findCandidates(clubId, memberId, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("quản lý");
        assertThatThrownBy(() -> service.getShortlist(clubId, memberId)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.getSentInvitations(clubId, memberId)).isInstanceOf(ServiceException.class);
        verify(playerProfileProvider, never()).findScoutingCandidates(any());
    }

    @Test
    @DisplayName("Quan ly CLB dung duoc scouting")
    void activeManagerCanScout() throws Exception {
        UUID managerId = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(memberRepository.findByClubIdAndUserId(clubId, managerId)).thenReturn(Optional.of(
                ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId).userId(managerId)
                        .role(ClubRole.ADMIN).status(ClubMemberStatus.ACTIVE).build()));

        service.findCandidates(clubId, managerId, null);

        verify(playerProfileProvider).findScoutingCandidates(any());
    }

    @Test
    @DisplayName("CLB khong ro tinh/thanh thi bao loi thay vi tra danh sach rong kho hieu")
    void clubWithoutAnyLocationReportsAClearError() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(null)));

        assertThatThrownBy(() -> service.findCandidates(clubId, ownerId, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("vị trí");
    }

    @Test
    @DisplayName("CLB da giai tan thi khong scouting duoc")
    void disbandedClubCannotScout() {
        Club club = club("ho-chi-minh");
        club.setDisbandedAt(LocalDateTime.now());
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));

        assertThatThrownBy(() -> service.findCandidates(clubId, ownerId, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("giải tán");
    }

    @Test
    @DisplayName("Danh sach theo doi ghep ho so moi nhat va quan he cua nguoi choi voi CLB")
    void shortlistMergesProfilesAndRelations() throws Exception {
        UUID invited = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID hiddenPlayer = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(shortlistRepository.findByClubId(clubId)).thenReturn(List.of(
                ClubShortlistEntry.builder().clubId(clubId).userId(invited).note("Chạy cánh tốt").build(),
                ClubShortlistEntry.builder().clubId(clubId).userId(member).build(),
                ClubShortlistEntry.builder().clubId(clubId).userId(hiddenPlayer).build()));
        when(memberRepository.findByClubId(clubId)).thenReturn(List.of(member(member, ClubMemberStatus.ACTIVE)));
        when(invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING)).thenReturn(List.of(
                ClubInvitation.builder().clubId(clubId).inviteeId(invited).status(ClubInvitationStatus.PENDING).build()));
        when(playerProfileProvider.lookupScoutingProfiles(eq(SportType.BADMINTON), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(candidate(invited, 4, 10, 10.0), candidate(member, 2, 3, 5.0), hidden(hiddenPlayer)));

        var result = service.getShortlist(clubId, ownerId);

        assertThat(result).extracting("relation").containsExactly(
                PlayerClubRelation.INVITED, PlayerClubRelation.MEMBER, PlayerClubRelation.NONE);
        assertThat(result.getFirst().getNote()).isEqualTo("Chạy cánh tốt");
        assertThat(result.getFirst().getPlayer().getFitScore()).isNotNull();
        assertThat(result.getLast().getPlayer().isProfileVisible()).isFalse();
        assertThat(result.getLast().getPlayer().getFitScore()).isNull();
    }

    @Test
    @DisplayName("Khong them duoc nguoi da an ho so, nhung van sua ghi chu nguoi da co trong danh sach")
    void hiddenPlayersCannotBeAddedButExistingEntriesKeepTheirNote() throws Exception {
        UUID hiddenPlayer = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(playerProfileProvider.lookupScoutingProfiles(any(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(hidden(hiddenPlayer)));

        assertThatThrownBy(() -> service.saveShortlistEntry(clubId, ownerId, hiddenPlayer, null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("không cho phép");

        when(shortlistRepository.findByClubIdAndUserId(clubId, hiddenPlayer)).thenReturn(Optional.of(
                ClubShortlistEntry.builder().clubId(clubId).userId(hiddenPlayer).addedBy(ownerId).build()));
        var saved = service.saveShortlistEntry(clubId, ownerId, hiddenPlayer, "  Hẹn gặp lại mùa sau ");

        assertThat(saved.getNote()).isEqualTo("Hẹn gặp lại mùa sau");
    }

    @Test
    @DisplayName("Them nguoi moi vao danh sach theo doi va kiem tra do dai ghi chu")
    void addsVisiblePlayerAndValidatesNote() throws Exception {
        UUID player = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(playerProfileProvider.lookupScoutingProfiles(any(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(candidate(player, 3, 8, 10.0)));

        var saved = service.saveShortlistEntry(clubId, ownerId, player, " ");

        assertThat(saved.getNote()).isNull();
        assertThat(saved.getRelation()).isEqualTo(PlayerClubRelation.NONE);
        ArgumentCaptor<ClubShortlistEntry> entry = ArgumentCaptor.forClass(ClubShortlistEntry.class);
        verify(shortlistRepository).save(entry.capture());
        assertThat(entry.getValue().getAddedBy()).isEqualTo(ownerId);

        assertThatThrownBy(() -> service.saveShortlistEntry(clubId, ownerId, player, "x".repeat(281)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("280");
    }

    @Test
    @DisplayName("Moi ban be: tra quan he cua tung nguoi, khong loc khoang cach hay trinh do")
    void relationsCoverEveryRequestedFriend() throws Exception {
        UUID member = UUID.randomUUID(), requested = UUID.randomUUID(), invited = UUID.randomUUID(),
                banned = UUID.randomUUID(), stranger = UUID.randomUUID();
        // CLB chua co vi tri van tra duoc: tab nay khong can do khoang cach.
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(null)));
        when(memberRepository.findByClubId(clubId)).thenReturn(List.of(
                member(member, ClubMemberStatus.ACTIVE), member(requested, ClubMemberStatus.PENDING),
                member(banned, ClubMemberStatus.BANNED)));
        when(invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING)).thenReturn(List.of(
                ClubInvitation.builder().clubId(clubId).inviteeId(invited).status(ClubInvitationStatus.PENDING).build()));

        var relations = service.getRelations(clubId, ownerId, List.of(member, requested, invited, banned, stranger, ownerId));

        assertThat(relations).containsEntry(member, PlayerClubRelation.MEMBER)
                .containsEntry(requested, PlayerClubRelation.REQUESTED)
                .containsEntry(invited, PlayerClubRelation.INVITED)
                .containsEntry(banned, PlayerClubRelation.BANNED)
                .containsEntry(stranger, PlayerClubRelation.NONE)
                .containsEntry(ownerId, PlayerClubRelation.MEMBER);
        verify(playerProfileProvider, org.mockito.Mockito.never()).findScoutingCandidates(any());
    }

    @Test
    @DisplayName("Chi chu hoac quan ly CLB moi tra duoc quan he")
    void relationsAreForManagersOnly() {
        UUID outsider = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(memberRepository.findByClubIdAndUserId(clubId, outsider)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRelations(clubId, outsider, List.of(UUID.randomUUID())))
                .isInstanceOf(ServiceException.class).hasMessageContaining("quản lý");
    }

    @Test
    @DisplayName("Loi moi da nhan tra kem quan he hien tai: nguoi choi da la thanh vien")
    void sentInvitationsCarryTheCurrentRelation() throws Exception {
        UUID invitee = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(memberRepository.findByClubId(clubId)).thenReturn(List.of(member(invitee, ClubMemberStatus.ACTIVE)));
        when(invitationRepository.findRecentByClubId(eq(clubId), any(Integer.class))).thenReturn(List.of(
                ClubInvitation.builder().invitationId(UUID.randomUUID()).clubId(clubId).inviteeId(invitee)
                        .status(ClubInvitationStatus.ACCEPTED).build()));
        when(playerProfileProvider.lookupScoutingProfiles(any(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(candidate(invitee, 3, 10, 10.0)));

        assertThat(service.getSentInvitations(clubId, ownerId))
                .singleElement().extracting("relation").isEqualTo(PlayerClubRelation.MEMBER);
    }

    @Test
    @DisplayName("Loi moi da gui co ten nguoi choi, ke ca nguoi da an ho so")
    void sentInvitationsCarryPlayerIdentity() throws Exception {
        UUID invitee = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club("ho-chi-minh")));
        when(invitationRepository.findRecentByClubId(eq(clubId), any(Integer.class))).thenReturn(List.of(
                ClubInvitation.builder().invitationId(UUID.randomUUID()).clubId(clubId).inviteeId(invitee)
                        .status(ClubInvitationStatus.DECLINED).message("Vào đội nhé").build()));
        when(playerProfileProvider.lookupScoutingProfiles(any(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(hidden(invitee)));

        var result = service.getSentInvitations(clubId, ownerId);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getStatus()).isEqualTo(ClubInvitationStatus.DECLINED);
            assertThat(item.getPlayer().getFullName()).isEqualTo("An danh");
            assertThat(item.getMessage()).isEqualTo("Vào đội nhé");
            assertThat(item.getRelation()).isEqualTo(PlayerClubRelation.NONE);
        });
    }
}
