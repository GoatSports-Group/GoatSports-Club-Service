package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.CreateClubCommand;
import iuh.fit.clubservice.application.dto.request.JoinClubCommand;
import iuh.fit.clubservice.application.dto.request.RespondClubMemberCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubActivityCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.dto.request.CreateClubActivityCommand;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.external.ClubEventPublisher;
import iuh.fit.clubservice.application.port.external.ClubMediaStorage;
import iuh.fit.clubservice.application.port.persistence.ClubActivityRepository;
import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubActivity;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.MatchStatus;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClubServiceImplTest {

    @Mock private ClubRepository clubRepository;
    @Mock private ClubMemberRepository memberRepository;
    @Mock private ClubActivityRepository activityRepository;
    @Mock private ClubInvitationRepository invitationRepository;
    @Mock private TournamentRegistrationRepository tournamentRegistrationRepository;
    @Mock private TournamentFixtureRepository tournamentFixtureRepository;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private ClubConversationSync conversationSync;
    @Mock private ClubEventPublisher eventPublisher;
    @Mock private ClubMediaStorage mediaStorage;

    private ClubServiceImpl service;

    private final UUID clubId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();
    private final UUID memberUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws ServiceException {
        service = new ClubServiceImpl(
                clubRepository, memberRepository, activityRepository,
                invitationRepository, tournamentRegistrationRepository, tournamentFixtureRepository,
                tournamentRepository, conversationSync, eventPublisher, mediaStorage
        );

        when(clubRepository.save(any())).thenAnswer(call -> {
            Club club = call.getArgument(0);
            if (club.getClubId() == null) club.setClubId(clubId);
            return club;
        });
        when(memberRepository.save(any())).thenAnswer(call -> {
            ClubMember member = call.getArgument(0);
            if (member.getMembershipId() == null) member.setMembershipId(UUID.randomUUID());
            return member;
        });
        when(memberRepository.findByClubIdAndStatus(any(), eq(ClubMemberStatus.ACTIVE))).thenReturn(List.of());
        when(conversationSync.sync(any(), any())).thenReturn(Optional.of(conversationId));
        when(mediaStorage.finalizeUpload(any(), any())).thenAnswer(call -> call.getArgument(0));
    }

    private Club club(ClubApprovalMode mode) {
        return Club.builder().clubId(clubId).ownerId(ownerId).name("CLB test")
                .sportType(SportType.FOOTBALL).approvalMode(mode).active(true)
                .winCount(0).lossCount(0).drawCount(0).matchCount(0).winRate(0D).build();
    }

    @Test
    @DisplayName("tạo câu lạc bộ thì đồng bộ chat nhóm và lưu lại mã cuộc trò chuyện")
    void createClubSyncsConversation() {
        service.createClub(CreateClubCommand.builder()
                .ownerId(ownerId).name("  CLB Sài Gòn  ").sportType(SportType.FOOTBALL)
                .tags(List.of(" Giao lưu ", "Thi đấu", "giao LƯU")).build());

        ArgumentCaptor<Club> captor = ArgumentCaptor.forClass(Club.class);
        verify(conversationSync).sync(any(), any());
        verify(clubRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(saved ->
                assertThat(saved.getConversationId()).isEqualTo(conversationId));
        assertThat(captor.getAllValues().getFirst().getTags()).containsExactly("Giao lưu", "Thi đấu");
    }

    @Test
    @DisplayName("social-service lỗi không được làm hỏng việc tạo câu lạc bộ")
    void clubIsStillCreatedWhenSocialFails() {
        when(conversationSync.sync(any(), any())).thenReturn(Optional.empty());

        var response = service.createClub(CreateClubCommand.builder()
                .ownerId(ownerId).name("CLB không chat").sportType(SportType.FOOTBALL).build());

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("CLB không chat");
    }

    @Test
    @DisplayName("tham gia câu lạc bộ tự duyệt thì vào chat nhóm ngay")
    void autoApprovedJoinSyncsConversation() throws ServiceException {
        UUID newcomer = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findByClubIdAndUserId(clubId, newcomer)).thenReturn(Optional.empty());

        var response = service.joinClub(JoinClubCommand.builder().clubId(clubId).userId(newcomer).build());

        assertThat(response.getStatus()).isEqualTo(ClubMemberStatus.ACTIVE);
        verify(conversationSync).sync(any(), any());
    }

    @Test
    @DisplayName("câu lạc bộ cần duyệt thì chưa vào chat khi còn chờ")
    void pendingJoinDoesNotSyncConversation() throws ServiceException {
        UUID newcomer = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(memberRepository.findByClubIdAndUserId(clubId, newcomer)).thenReturn(Optional.empty());

        var response = service.joinClub(JoinClubCommand.builder().clubId(clubId).userId(newcomer)
                .introMessage("Tôi muốn tham gia lịch tập cuối tuần.").build());

        assertThat(response.getStatus()).isEqualTo(ClubMemberStatus.PENDING);
        assertThat(response.getIntroMessage()).isEqualTo("Tôi muốn tham gia lịch tập cuối tuần.");
        assertThat(response.getRequestedAt()).isNotNull();
        verify(conversationSync, never()).sync(any(), any());
    }

    @Test
    @DisplayName("duyệt thành viên thì đồng bộ lại chat nhóm")
    void acceptingMemberSyncsConversation() throws ServiceException {
        UUID membershipId = UUID.randomUUID();
        ClubMember pending = ClubMember.builder().membershipId(membershipId).clubId(clubId)
                .userId(UUID.randomUUID()).role(ClubRole.MEMBER).status(ClubMemberStatus.PENDING).build();
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(pending));
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));

        service.respondMembership(RespondClubMemberCommand.builder()
                .membershipId(membershipId).actorUserId(ownerId).accepted(true).build());

        assertThat(pending.getStatus()).isEqualTo(ClubMemberStatus.ACTIVE);
        verify(conversationSync).sync(any(), any());
    }

    @Test
    @DisplayName("từ chối thành viên thì không đụng tới chat nhóm")
    void rejectingMemberDoesNotSyncConversation() throws ServiceException {
        UUID membershipId = UUID.randomUUID();
        ClubMember pending = ClubMember.builder().membershipId(membershipId).clubId(clubId)
                .userId(UUID.randomUUID()).role(ClubRole.MEMBER).status(ClubMemberStatus.PENDING).build();
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(pending));
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));

        service.respondMembership(RespondClubMemberCommand.builder()
                .membershipId(membershipId).actorUserId(ownerId).accepted(false).build());

        assertThat(pending.getStatus()).isEqualTo(ClubMemberStatus.REJECTED);
        verify(conversationSync, never()).sync(any(), any());
    }

    @Test
    @DisplayName("rời câu lạc bộ thì bị gỡ khỏi chat nhóm")
    void leavingClubSyncsConversation() throws ServiceException {
        UUID member = UUID.randomUUID();
        ClubMember active = ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId)
                .userId(member).role(ClubRole.MEMBER).status(ClubMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now()).build();
        when(memberRepository.findByClubIdAndUserId(clubId, member)).thenReturn(Optional.of(active));
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));

        service.leaveClub(clubId, member);

        assertThat(active.getStatus()).isEqualTo(ClubMemberStatus.LEFT);
        verify(conversationSync).sync(any(), any());
    }

    @Test
    @DisplayName("thành viên bị cấm không tham gia lại được")
    void bannedMemberCannotRejoin() {
        UUID banned = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findByClubIdAndUserId(clubId, banned)).thenReturn(Optional.of(
                ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId).userId(banned)
                        .role(ClubRole.MEMBER).status(ClubMemberStatus.BANNED).build()));

        assertThatThrownBy(() -> service.joinClub(
                JoinClubCommand.builder().clubId(clubId).userId(banned).build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("bị cấm");

        verify(conversationSync, never()).sync(any(), any());
    }

    @Test
    @DisplayName("chủ câu lạc bộ không rời khi chưa chuyển quyền")
    void ownerCannotLeave() {
        when(memberRepository.findByClubIdAndUserId(clubId, ownerId)).thenReturn(Optional.of(
                ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId).userId(ownerId)
                        .role(ClubRole.OWNER).status(ClubMemberStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.leaveClub(clubId, ownerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("chuyển quyền sở hữu");
    }

    private ClubMember memberWithRole(UUID membership, UUID userId, ClubRole role) {
        return ClubMember.builder().membershipId(membership).clubId(clubId).userId(userId)
                .role(role).status(ClubMemberStatus.ACTIVE).joinedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("danh sách thành viên hoạt động được phân trang")
    void activeMembersArePaged() throws ServiceException {
        PageRequest pageable = PageRequest.of(0, 7);
        ClubMember member = memberWithRole(membershipId, memberUserId, ClubRole.MEMBER);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(member), pageable, 8));

        var page = service.getClubMembersPage(clubId, null, ClubMemberStatus.ACTIVE, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(8);
        assertThat(page.getContent().getFirst().getMembershipId()).isEqualTo(membershipId);
    }

    @Test
    @DisplayName("người ngoài không xem được trang yêu cầu đang chờ")
    void pendingMemberPageRequiresManager() {
        UUID outsider = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));

        assertThatThrownBy(() -> service.getClubMembersPage(clubId, outsider,
                ClubMemberStatus.PENDING, PageRequest.of(0, 7)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ban quản trị");
    }

    // ---------- CLUB-01: quan ly thanh vien ----------

    @Test
    @DisplayName("chu cau lac bo phong duoc quan tri vien")
    void ownerPromotesAdmin() throws ServiceException {
        ClubMember member = memberWithRole(membershipId, memberUserId, ClubRole.MEMBER);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(member));

        service.changeMemberRole(clubId, membershipId, ClubRole.ADMIN, ownerId);

        assertThat(member.getRole()).isEqualTo(ClubRole.ADMIN);
    }

    @Test
    @DisplayName("quan tri vien khong tu phong quan tri vien khac")
    void adminCannotPromoteAnotherAdmin() {
        UUID adminUser = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findByClubIdAndUserId(clubId, adminUser)).thenReturn(Optional.of(
                memberWithRole(UUID.randomUUID(), adminUser, ClubRole.ADMIN)));

        assertThatThrownBy(() -> service.changeMemberRole(clubId, membershipId, ClubRole.ADMIN, adminUser))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("khong doi vai tro thanh chu so huu bang duong nay")
    void roleChangeCannotGrantOwnership() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));

        assertThatThrownBy(() -> service.changeMemberRole(clubId, membershipId, ClubRole.OWNER, ownerId))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("cam thanh vien dat trang thai BANNED va dong bo lai chat nhom")
    void banningMemberMarksBannedAndSyncs() throws ServiceException {
        ClubMember member = memberWithRole(membershipId, memberUserId, ClubRole.MEMBER);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(member));

        service.removeMember(clubId, membershipId, true, ownerId);

        assertThat(member.getStatus()).isEqualTo(ClubMemberStatus.BANNED);
        assertThat(member.getRespondedBy()).isEqualTo(ownerId);
        verify(conversationSync).sync(any(), any());
    }

    @Test
    @DisplayName("xoa thanh vien khong cam thi trang thai la REMOVED")
    void removingWithoutBanMarksRemoved() throws ServiceException {
        ClubMember member = memberWithRole(membershipId, memberUserId, ClubRole.MEMBER);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(member));

        service.removeMember(clubId, membershipId, false, ownerId);

        assertThat(member.getStatus()).isEqualTo(ClubMemberStatus.REMOVED);
    }

    @Test
    @DisplayName("khong xoa duoc chu cau lac bo")
    void ownerCannotBeRemoved() {
        UUID ownerMembership = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findById(ownerMembership)).thenReturn(Optional.of(
                memberWithRole(ownerMembership, ownerId, ClubRole.OWNER)));

        assertThatThrownBy(() -> service.removeMember(clubId, ownerMembership, false, ownerId))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("quan ly khong tu xoa minh, phai dung chuc nang roi")
    void managerCannotRemoveSelf() {
        UUID adminUser = UUID.randomUUID();
        UUID adminMembership = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findByClubIdAndUserId(clubId, adminUser)).thenReturn(Optional.of(
                memberWithRole(adminMembership, adminUser, ClubRole.ADMIN)));
        when(memberRepository.findById(adminMembership)).thenReturn(Optional.of(
                memberWithRole(adminMembership, adminUser, ClubRole.ADMIN)));

        assertThatThrownBy(() -> service.removeMember(clubId, adminMembership, false, adminUser))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("cam lai nguoi da bi cam khong lam gi them")
    void banningTwiceIsIdempotent() throws ServiceException {
        ClubMember banned = ClubMember.builder().membershipId(membershipId).clubId(clubId)
                .userId(memberUserId).role(ClubRole.MEMBER).status(ClubMemberStatus.BANNED).build();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(banned));

        service.removeMember(clubId, membershipId, true, ownerId);

        verify(memberRepository, never()).save(any());
        verify(conversationSync, never()).sync(any(), any());
    }

    // ---------- CLUB-01/02: cap nhat CLB va hoat dong ----------

    @Test
    @DisplayName("doi ten cau lac bo thi dong bo lai ten chat nhom")
    void renamingClubSyncsConversation() throws ServiceException {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));

        var response = service.updateClub(UpdateClubCommand.builder()
                .clubId(clubId).actorUserId(ownerId).name("  CLB doi ten  ").build());

        assertThat(response.getName()).isEqualTo("CLB doi ten");
        verify(conversationSync).sync(any(), any());
    }

    @Test
    @DisplayName("cap nhat logo va banner thi xac nhan tep tam truoc khi luu")
    void updatingClubMediaFinalizesTemporaryUploads() throws ServiceException {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(mediaStorage.finalizeUpload("temp/clubs/logos/logo.webp", "club-logo:" + clubId))
                .thenReturn("https://cdn.goatsports.test/clubs/logos/logo.webp");
        when(mediaStorage.finalizeUpload("temp/clubs/banners/banner.webp", "club-banner:" + clubId))
                .thenReturn("https://cdn.goatsports.test/clubs/banners/banner.webp");

        var response = service.updateClub(UpdateClubCommand.builder()
                .clubId(clubId).actorUserId(ownerId)
                .logoUrl("temp/clubs/logos/logo.webp")
                .bannerUrl("temp/clubs/banners/banner.webp")
                .build());

        assertThat(response.getLogoUrl()).isEqualTo("https://cdn.goatsports.test/clubs/logos/logo.webp");
        assertThat(response.getBannerUrl()).isEqualTo("https://cdn.goatsports.test/clubs/banners/banner.webp");
        verify(mediaStorage).finalizeUpload("temp/clubs/logos/logo.webp", "club-logo:" + clubId);
        verify(mediaStorage).finalizeUpload("temp/clubs/banners/banner.webp", "club-banner:" + clubId);
    }

    @Test
    @DisplayName("quản lý câu lạc bộ cập nhật được tỉnh thành và địa chỉ sinh hoạt")
    void clubManagerCanUpdateCityAndLocation() throws ServiceException {
        UUID adminUserId = UUID.randomUUID();
        Club currentClub = club(ClubApprovalMode.AUTO);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(currentClub));
        when(memberRepository.findByClubIdAndUserId(clubId, adminUserId)).thenReturn(Optional.of(
                memberWithRole(UUID.randomUUID(), adminUserId, ClubRole.ADMIN)));

        var response = service.updateClub(UpdateClubCommand.builder()
                .clubId(clubId)
                .actorUserId(adminUserId)
                .city("  ho-chi-minh  ")
                .location("  123 Nguyễn Văn Linh, Quận 7  ")
                .build());

        assertThat(response.getCity()).isEqualTo("ho-chi-minh");
        assertThat(response.getLocation()).isEqualTo("123 Nguyễn Văn Linh, Quận 7");
        assertThat(currentClub.getCity()).isEqualTo("ho-chi-minh");
        assertThat(currentClub.getLocation()).isEqualTo("123 Nguyễn Văn Linh, Quận 7");
    }

    @Test
    @DisplayName("thanh vien thuong khong sua duoc cau lac bo")
    void plainMemberCannotUpdateClub() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId)).thenReturn(Optional.of(
                memberWithRole(membershipId, memberUserId, ClubRole.MEMBER)));

        assertThatThrownBy(() -> service.updateClub(UpdateClubCommand.builder()
                .clubId(clubId).actorUserId(memberUserId).name("Ten trai phep").build()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("sua hoat dong chan moc ket thuc truoc moc bat dau")
    void activityUpdateRejectsInvertedTime() {
        assertThatThrownBy(() -> service.updateClubActivity(UpdateClubActivityCommand.builder()
                .clubId(clubId).activityId(UUID.randomUUID()).actorUserId(ownerId)
                .title("Tap luyen")
                .startAt(LocalDateTime.now().plusDays(2))
                .endAt(LocalDateTime.now().plusDays(1))
                .build()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("khong sua duoc hoat dong cua cau lac bo khac")
    void cannotUpdateActivityOfAnotherClub() {
        UUID activityId = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(ClubActivity.builder()
                .activityId(activityId).clubId(UUID.randomUUID()).title("Hoat dong CLB khac")
                .startAt(LocalDateTime.now()).endAt(LocalDateTime.now().plusHours(2)).build()));

        assertThatThrownBy(() -> service.updateClubActivity(UpdateClubActivityCommand.builder()
                .clubId(clubId).activityId(activityId).actorUserId(ownerId).title("Sua")
                .startAt(LocalDateTime.now().plusDays(1)).endAt(LocalDateTime.now().plusDays(1).plusHours(2))
                .build()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("xoa hoat dong goi dung repository")
    void deletingActivityRemovesIt() throws ServiceException {
        UUID activityId = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(activityRepository.findById(activityId)).thenReturn(Optional.of(ClubActivity.builder()
                .activityId(activityId).clubId(clubId).title("Tap luyen")
                .startAt(LocalDateTime.now()).endAt(LocalDateTime.now().plusHours(2)).build()));

        service.deleteClubActivity(clubId, activityId, ownerId);

        verify(activityRepository).deleteById(activityId);
    }

    // ---------- CLUB-02: thong bao hoat dong ----------

    @Test
    @DisplayName("tao hoat dong thi bao cho thanh vien khac, khong bao cho chinh nguoi tao")
    void creatingActivityNotifiesOtherMembersOnly() throws ServiceException {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(activityRepository.save(any())).thenAnswer(call -> {
            ClubActivity activity = call.getArgument(0);
            if (activity.getActivityId() == null) activity.setActivityId(UUID.randomUUID());
            return activity;
        });
        when(memberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)).thenReturn(List.of(
                memberWithRole(UUID.randomUUID(), ownerId, ClubRole.OWNER),
                memberWithRole(membershipId, memberUserId, ClubRole.MEMBER)));

        service.createClubActivity(CreateClubActivityCommand.builder()
                .clubId(clubId).creatorId(ownerId).title("Tap luyen thu 7")
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusHours(2))
                .build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).activityCreated(any(), any(), captor.capture());
        assertThat(captor.getValue()).containsExactly(memberUserId);
    }

    @Test
    @DisplayName("club khong co thanh vien nao khac thi khong gui thong bao nao")
    void soloClubNotifiesNobody() throws ServiceException {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(activityRepository.save(any())).thenAnswer(call -> {
            ClubActivity activity = call.getArgument(0);
            if (activity.getActivityId() == null) activity.setActivityId(UUID.randomUUID());
            return activity;
        });
        when(memberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)).thenReturn(List.of(
                memberWithRole(UUID.randomUUID(), ownerId, ClubRole.OWNER)));

        service.createClubActivity(CreateClubActivityCommand.builder()
                .clubId(clubId).creatorId(ownerId).title("Tap rieng")
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusHours(2))
                .build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).activityCreated(any(), any(), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    // ---------- Thong bao realtime khi xin vao CLB ----------

    @Test
    @DisplayName("xin vào CLB duyệt tay thì báo cho chủ và quản trị viên, không báo cho chính mình")
    void joinRequestNotifiesManagersOnly() throws ServiceException {
        UUID adminUser = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId)).thenReturn(Optional.empty());
        when(memberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)).thenReturn(List.of(
                memberWithRole(UUID.randomUUID(), ownerId, ClubRole.OWNER),
                memberWithRole(UUID.randomUUID(), adminUser, ClubRole.ADMIN),
                memberWithRole(UUID.randomUUID(), UUID.randomUUID(), ClubRole.MEMBER)));

        service.joinClub(JoinClubCommand.builder().clubId(clubId).userId(memberUserId).build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).membershipRequested(any(), any(), any(), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(ownerId, adminUser);
    }

    @Test
    @DisplayName("CLB tự động duyệt thì vào thẳng, không sinh yêu cầu chờ duyệt")
    void autoApprovalSkipsRequestNotification() throws ServiceException {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId)).thenReturn(Optional.empty());
        when(memberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)).thenReturn(List.of());

        service.joinClub(JoinClubCommand.builder().clubId(clubId).userId(memberUserId).build());

        verify(eventPublisher, never()).membershipRequested(any(), any(), any(), any());
    }

    @Test
    @DisplayName("duyệt yêu cầu thì báo lại cho người xin vào")
    void approvingNotifiesRequester() throws ServiceException {
        ClubMember pending = memberWithRole(membershipId, memberUserId, ClubRole.MEMBER);
        pending.setStatus(ClubMemberStatus.PENDING);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(pending));

        service.respondMembership(RespondClubMemberCommand.builder()
                .membershipId(membershipId).actorUserId(ownerId).accepted(true).build());

        verify(eventPublisher).membershipResponded(any(), any(), eq(true));
    }

    @Test
    @DisplayName("từ chối yêu cầu cũng báo lại, không im lặng")
    void rejectingNotifiesRequester() throws ServiceException {
        ClubMember pending = memberWithRole(membershipId, memberUserId, ClubRole.MEMBER);
        pending.setStatus(ClubMemberStatus.PENDING);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(pending));

        service.respondMembership(RespondClubMemberCommand.builder()
                .membershipId(membershipId).actorUserId(ownerId).accepted(false).build());

        verify(eventPublisher).membershipResponded(any(), any(), eq(false));
    }

    // ---------- Loi moi vao CLB ----------

    private ClubInvitation pendingInvitation(UUID invitationId, UUID invitee, UUID invitedBy) {
        return ClubInvitation.builder()
                .invitationId(invitationId).clubId(clubId).inviteeId(invitee).invitedBy(invitedBy)
                .status(ClubInvitationStatus.PENDING).build();
    }

    @Test
    @DisplayName("chủ CLB mời được người ngoài và người đó nhận thông báo")
    void ownerCanInviteOutsider() throws ServiceException {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId)).thenReturn(Optional.empty());
        when(invitationRepository.findByClubIdAndInviteeIdAndStatus(
                clubId, memberUserId, ClubInvitationStatus.PENDING)).thenReturn(Optional.empty());
        when(invitationRepository.save(any())).thenAnswer(call -> {
            ClubInvitation saved = call.getArgument(0);
            if (saved.getInvitationId() == null) saved.setInvitationId(UUID.randomUUID());
            return saved;
        });

        var response = service.inviteMember(clubId, memberUserId, "Vào đá với anh em nhé", ownerId);

        assertThat(response.getStatus()).isEqualTo(ClubInvitationStatus.PENDING);
        verify(eventPublisher).invitationSent(any(), any());
    }

    @Test
    @DisplayName("thành viên thường không mời được ai")
    void plainMemberCannotInvite() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId)).thenReturn(Optional.of(
                memberWithRole(membershipId, memberUserId, ClubRole.MEMBER)));

        assertThatThrownBy(() -> service.inviteMember(clubId, UUID.randomUUID(), null, memberUserId))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("không mời người đã là thành viên")
    void cannotInviteExistingMember() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId)).thenReturn(Optional.of(
                memberWithRole(membershipId, memberUserId, ClubRole.MEMBER)));

        assertThatThrownBy(() -> service.inviteMember(clubId, memberUserId, null, ownerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đã là thành viên");
    }

    @Test
    @DisplayName("không gửi hai lời mời cùng lúc cho một người")
    void cannotSendTwoPendingInvitations() {
        UUID invitationId = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId)).thenReturn(Optional.empty());
        when(invitationRepository.findByClubIdAndInviteeIdAndStatus(
                clubId, memberUserId, ClubInvitationStatus.PENDING))
                .thenReturn(Optional.of(pendingInvitation(invitationId, memberUserId, ownerId)));

        assertThatThrownBy(() -> service.inviteMember(clubId, memberUserId, null, ownerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đang chờ");
    }

    @Test
    @DisplayName("nhận lời mời thì vào thẳng CLB, không phải chờ duyệt lần nữa")
    void acceptingInvitationJoinsWithoutApproval() throws ServiceException {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId))
                .thenReturn(Optional.of(pendingInvitation(invitationId, memberUserId, ownerId)));
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId)).thenReturn(Optional.empty());
        when(invitationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var response = service.acceptInvitation(invitationId, memberUserId);

        assertThat(response.getStatus()).isEqualTo(ClubMemberStatus.ACTIVE);
        verify(conversationSync).sync(any(), any());
        verify(eventPublisher).invitationAnswered(any(), any(), eq(true));
    }

    @Test
    @DisplayName("từ chối lời mời thì không tạo thành viên nào")
    void decliningInvitationCreatesNoMember() throws ServiceException {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId))
                .thenReturn(Optional.of(pendingInvitation(invitationId, memberUserId, ownerId)));
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(invitationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.declineInvitation(invitationId, memberUserId);

        verify(memberRepository, never()).save(any());
        verify(eventPublisher).invitationAnswered(any(), any(), eq(false));
    }

    @Test
    @DisplayName("không trả lời hộ lời mời của người khác")
    void cannotAnswerSomeoneElsesInvitation() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId))
                .thenReturn(Optional.of(pendingInvitation(invitationId, memberUserId, ownerId)));

        assertThatThrownBy(() -> service.acceptInvitation(invitationId, UUID.randomUUID()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("không dành cho bạn");
    }

    @Test
    @DisplayName("hoạt động sắp tới của CLB được trả theo từng trang")
    void upcomingActivitiesArePaged() {
        PageRequest pageable = PageRequest.of(0, 3);
        ClubActivity activity = ClubActivity.builder().activityId(UUID.randomUUID()).clubId(clubId)
                .title("Buổi tập chiến thuật").startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusHours(2)).build();
        when(activityRepository.findUpcomingByClubId(eq(clubId), any(LocalDateTime.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(activity), pageable, 1));

        var page = service.getClubActivitiesPage(clubId, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting("title").containsExactly("Buổi tập chiến thuật");
    }

    @Test
    @DisplayName("ba trận gần nhất được quy đổi điểm số theo phía CLB")
    void recentMatchesUseClubPerspective() throws ServiceException {
        UUID tournamentId = UUID.randomUUID();
        UUID clubRegistrationId = UUID.randomUUID();
        UUID opponentRegistrationId = UUID.randomUUID();
        LocalDateTime playedAt = LocalDateTime.now().minusDays(2);
        TournamentRegistration clubRegistration = TournamentRegistration.builder()
                .registrationId(clubRegistrationId).tournamentId(tournamentId).clubId(clubId)
                .teamName("GOAT FC").build();
        TournamentRegistration opponent = TournamentRegistration.builder()
                .registrationId(opponentRegistrationId).tournamentId(tournamentId)
                .teamName("Weekend FC").build();
        TournamentFixture fixture = TournamentFixture.builder().fixtureId(UUID.randomUUID())
                .tournamentId(tournamentId).registration1Id(opponentRegistrationId)
                .registration2Id(clubRegistrationId).score1(1).score2(2)
                .createdAt(playedAt).status(MatchStatus.COMPLETED).build();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.MANUAL)));
        when(tournamentRegistrationRepository.findByClubId(clubId)).thenReturn(List.of(clubRegistration));
        when(tournamentFixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(fixture));
        when(tournamentRegistrationRepository.findById(opponentRegistrationId)).thenReturn(Optional.of(opponent));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(
                Tournament.builder().tournamentId(tournamentId).name("GOAT Community Cup").build()));

        var matches = service.getClubRecentMatches(clubId, 3);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getOpponentName()).isEqualTo("Weekend FC");
        assertThat(matches.get(0).getClubScore()).isEqualTo(2);
        assertThat(matches.get(0).getOpponentScore()).isEqualTo(1);
        assertThat(matches.get(0).getResult()).isEqualTo("WIN");
        assertThat(matches.get(0).getPlayedAt()).isEqualTo(playedAt);

        var page = service.getClubMatchesPage(clubId, PageRequest.of(0, 3));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting("opponentName").containsExactly("Weekend FC");
    }

    private Club disbandedClub() {
        Club club = club(ClubApprovalMode.AUTO);
        club.setActive(false);
        club.setDisbandedAt(LocalDateTime.now());
        club.setDisbandedBy(ownerId);
        return club;
    }

    @Test
    @DisplayName("CLB da giai tan khong con xuat hien trong danh sach CLB cua nguoi dung")
    void disbandedClubIsExcludedFromMyMemberships() {
        ClubMember membership = ClubMember.builder()
                .membershipId(membershipId)
                .clubId(clubId)
                .userId(memberUserId)
                .role(ClubRole.MEMBER)
                .status(ClubMemberStatus.ACTIVE)
                .build();
        when(memberRepository.findByUserIdAndStatus(memberUserId, ClubMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(disbandedClub()));

        var result = service.getMyMemberships(memberUserId, ClubMemberStatus.ACTIVE);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Chu CLB giai tan: doi trang thai, huy loi moi treo va bao cho thanh vien")
    void ownerDisbandsClubAndCancelsPendingInvitations() throws Exception {
        Club club = club(ClubApprovalMode.AUTO);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(tournamentRegistrationRepository.findByClubId(clubId)).thenReturn(List.of());
        when(memberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)).thenReturn(List.of(
                ClubMember.builder().membershipId(membershipId).clubId(clubId).userId(memberUserId)
                        .role(ClubRole.MEMBER).status(ClubMemberStatus.ACTIVE).build()));
        ClubInvitation pending = ClubInvitation.builder().invitationId(UUID.randomUUID()).clubId(clubId)
                .inviteeId(UUID.randomUUID()).invitedBy(ownerId).status(ClubInvitationStatus.PENDING).build();
        when(invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING))
                .thenReturn(List.of(pending));

        service.disbandClub(clubId, ownerId);

        assertThat(club.getActive()).isFalse();
        assertThat(club.getDisbandedAt()).isNotNull();
        assertThat(club.getDisbandedBy()).isEqualTo(ownerId);
        assertThat(pending.getStatus()).isEqualTo(ClubInvitationStatus.CANCELLED);
        verify(eventPublisher).clubDisbanded(any(), eq(ownerId), eq(List.of(memberUserId)));
    }

    @Test
    @DisplayName("Chi chu CLB moi duoc giai tan, quan tri vien thi khong")
    void adminCannotDisbandClub() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));

        assertThatThrownBy(() -> service.disbandClub(clubId, memberUserId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Chỉ chủ câu lạc bộ");
        verify(eventPublisher, never()).clubDisbanded(any(), any(), any());
    }

    @Test
    @DisplayName("Dang co nghia vu o giai chua ket thuc thi khong duoc giai tan")
    void cannotDisbandWhileCommittedToLiveTournament() {
        UUID tournamentId = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(tournamentRegistrationRepository.findByClubId(clubId)).thenReturn(List.of(
                TournamentRegistration.builder().registrationId(UUID.randomUUID()).tournamentId(tournamentId)
                        .clubId(clubId).status(RegistrationStatus.CONFIRMED).build()));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(
                Tournament.builder().tournamentId(tournamentId).name("Giải mùa thu")
                        .status(TournamentStatus.IN_PROGRESS).build()));

        assertThatThrownBy(() -> service.disbandClub(clubId, ownerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Giải mùa thu");
        verify(clubRepository, never()).save(any());
    }

    @Test
    @DisplayName("Giai tan roi thi giai da ket thuc khong con can tro nua")
    void canDisbandWhenOnlyFinishedTournamentsRemain() throws Exception {
        UUID tournamentId = UUID.randomUUID();
        Club club = club(ClubApprovalMode.AUTO);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(tournamentRegistrationRepository.findByClubId(clubId)).thenReturn(List.of(
                TournamentRegistration.builder().registrationId(UUID.randomUUID()).tournamentId(tournamentId)
                        .clubId(clubId).status(RegistrationStatus.CONFIRMED).build()));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(
                Tournament.builder().tournamentId(tournamentId).name("Giải mùa xuân")
                        .status(TournamentStatus.COMPLETED).build()));
        when(invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING))
                .thenReturn(List.of());

        service.disbandClub(clubId, ownerId);

        assertThat(club.getActive()).isFalse();
    }

    @Test
    @DisplayName("CLB da giai tan thi moi thao tac ghi deu bi tu choi")
    void disbandedClubRejectsEveryWrite() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(disbandedClub()));

        assertThatThrownBy(() -> service.joinClub(JoinClubCommand.builder().clubId(clubId)
                .userId(memberUserId).build()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã giải tán");
        assertThatThrownBy(() -> service.updateClub(UpdateClubCommand.builder().clubId(clubId)
                .actorUserId(ownerId).name("Tên mới").build()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã giải tán");
        assertThatThrownBy(() -> service.inviteMember(clubId, memberUserId, null, ownerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã giải tán");
        assertThatThrownBy(() -> service.removeMember(clubId, membershipId, false, ownerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã giải tán");
        assertThatThrownBy(() -> service.disbandClub(clubId, ownerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã giải tán");
    }

    @Test
    @DisplayName("CLB da giai tan van xem duoc chi tiet de hien trang thai")
    void disbandedClubIsStillReadable() throws Exception {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(disbandedClub()));

        var response = service.getClubDetails(clubId);

        assertThat(response.getActive()).isFalse();
        assertThat(response.getDisbandedAt()).isNotNull();
    }

    @Test
    @DisplayName("Chuyen quyen so huu: chu moi len OWNER, chu cu xuong ADMIN, chat doi chu theo")
    void ownerTransfersOwnershipAndPreviousOwnerBecomesAdmin() throws Exception {
        Club club = club(ClubApprovalMode.AUTO);
        ClubMember successor = ClubMember.builder().membershipId(membershipId).clubId(clubId)
                .userId(memberUserId).role(ClubRole.MEMBER).status(ClubMemberStatus.ACTIVE).build();
        ClubMember previousOwner = ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId)
                .userId(ownerId).role(ClubRole.OWNER).status(ClubMemberStatus.ACTIVE).build();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(successor));
        when(memberRepository.findByClubIdAndUserId(clubId, ownerId)).thenReturn(Optional.of(previousOwner));

        var response = service.transferOwnership(clubId, membershipId, ownerId);

        assertThat(response.getRole()).isEqualTo(ClubRole.OWNER);
        assertThat(previousOwner.getRole()).isEqualTo(ClubRole.ADMIN);
        assertThat(club.getOwnerId()).isEqualTo(memberUserId);
        verify(conversationSync).sync(any(), any());
        verify(eventPublisher).ownershipTransferred(any(), eq(ownerId), eq(memberUserId));
    }

    @Test
    @DisplayName("Khong chuyen quyen so huu cho thanh vien khong con hoat dong")
    void cannotTransferOwnershipToInactiveMember() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(
                ClubMember.builder().membershipId(membershipId).clubId(clubId).userId(memberUserId)
                        .role(ClubRole.MEMBER).status(ClubMemberStatus.LEFT).build()));

        assertThatThrownBy(() -> service.transferOwnership(clubId, membershipId, ownerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đang hoạt động");
        verify(eventPublisher, never()).ownershipTransferred(any(), any(), any());
    }

    @Test
    @DisplayName("Chi chu CLB moi duoc chuyen quyen so huu")
    void nonOwnerCannotTransferOwnership() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));

        assertThatThrownBy(() -> service.transferOwnership(clubId, membershipId, memberUserId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Chỉ chủ câu lạc bộ");
    }

    @Test
    @DisplayName("CLB da giai tan thi khong chuyen quyen so huu duoc nua")
    void disbandedClubRejectsOwnershipTransfer() {
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(disbandedClub()));

        assertThatThrownBy(() -> service.transferOwnership(clubId, membershipId, ownerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("đã giải tán");
    }

    @Test
    @DisplayName("Quan ly CLB roi CLB binh thuong nhu thanh vien")
    void activeManagerCanLeaveClub() throws Exception {
        UUID managerId = UUID.randomUUID();
        ClubMember manager = ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId)
                .userId(managerId).role(ClubRole.ADMIN).status(ClubMemberStatus.ACTIVE).build();
        when(memberRepository.findByClubIdAndUserId(clubId, managerId)).thenReturn(Optional.of(manager));
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));

        service.leaveClub(clubId, managerId);

        assertThat(manager.getStatus()).isEqualTo(ClubMemberStatus.LEFT);
    }

    @Test
    @DisplayName("Chu CLB trao quyen xong thi roi CLB duoc")
    void ownerCanLeaveAfterTransferringOwnership() throws Exception {
        Club club = club(ClubApprovalMode.AUTO);
        ClubMember successor = ClubMember.builder().membershipId(membershipId).clubId(clubId)
                .userId(memberUserId).role(ClubRole.MEMBER).status(ClubMemberStatus.ACTIVE).build();
        ClubMember owner = ClubMember.builder().membershipId(UUID.randomUUID()).clubId(clubId)
                .userId(ownerId).role(ClubRole.OWNER).status(ClubMemberStatus.ACTIVE).build();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club));
        when(memberRepository.findById(membershipId)).thenReturn(Optional.of(successor));
        when(memberRepository.findByClubIdAndUserId(clubId, ownerId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.leaveClub(clubId, ownerId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("chuyển quyền sở hữu");

        service.transferOwnership(clubId, membershipId, ownerId);
        service.leaveClub(clubId, ownerId);

        assertThat(owner.getRole()).isEqualTo(ClubRole.ADMIN);
        assertThat(owner.getStatus()).isEqualTo(ClubMemberStatus.LEFT);
        assertThat(club.getOwnerId()).isEqualTo(memberUserId);
    }

    @Test
    @DisplayName("quản lý thu hồi lời mời đang chờ thì lời mời chuyển sang CANCELLED")
    void managerCancelsPendingInvitation() throws ServiceException {
        UUID invitationId = UUID.randomUUID();
        ClubInvitation invitation = pendingInvitation(invitationId, memberUserId, ownerId);
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

        service.cancelInvitation(clubId, invitationId, ownerId);

        assertThat(invitation.getStatus()).isEqualTo(ClubInvitationStatus.CANCELLED);
        assertThat(invitation.getRespondedAt()).isNotNull();
        verify(invitationRepository).save(invitation);
    }

    @Test
    @DisplayName("không thu hồi được lời mời người chơi đã trả lời hoặc của CLB khác")
    void cannotCancelAnsweredOrForeignInvitation() {
        UUID answeredId = UUID.randomUUID();
        ClubInvitation answered = pendingInvitation(answeredId, memberUserId, ownerId);
        answered.setStatus(ClubInvitationStatus.ACCEPTED);
        UUID foreignId = UUID.randomUUID();
        ClubInvitation foreign = pendingInvitation(foreignId, memberUserId, ownerId);
        foreign.setClubId(UUID.randomUUID());
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(invitationRepository.findById(answeredId)).thenReturn(Optional.of(answered));
        when(invitationRepository.findById(foreignId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.cancelInvitation(clubId, answeredId, ownerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã trả lời");
        assertThatThrownBy(() -> service.cancelInvitation(clubId, foreignId, ownerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("Không tìm thấy");
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("thành viên thường không thu hồi được lời mời")
    void plainMemberCannotCancelInvitation() {
        UUID invitationId = UUID.randomUUID();
        when(clubRepository.findById(clubId)).thenReturn(Optional.of(club(ClubApprovalMode.AUTO)));
        when(memberRepository.findByClubIdAndUserId(clubId, memberUserId)).thenReturn(Optional.of(
                ClubMember.builder().membershipId(membershipId).clubId(clubId).userId(memberUserId)
                        .role(ClubRole.MEMBER).status(ClubMemberStatus.ACTIVE).build()));
        when(invitationRepository.findById(invitationId))
                .thenReturn(Optional.of(pendingInvitation(invitationId, UUID.randomUUID(), ownerId)));

        assertThatThrownBy(() -> service.cancelInvitation(clubId, invitationId, memberUserId))
                .isInstanceOf(ServiceException.class);
        verify(invitationRepository, never()).save(any());
    }
}
