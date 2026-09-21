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
import iuh.fit.clubservice.application.port.persistence.ClubActivityRepository;
import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubActivity;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
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
    @Mock private ClubConversationSync conversationSync;
    @Mock private ClubEventPublisher eventPublisher;

    private ClubServiceImpl service;

    private final UUID clubId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();
    private final UUID memberUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClubServiceImpl(
                clubRepository, memberRepository, activityRepository,
                invitationRepository, conversationSync, eventPublisher
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
                .ownerId(ownerId).name("  CLB Sài Gòn  ").sportType(SportType.FOOTBALL).build());

        ArgumentCaptor<Club> captor = ArgumentCaptor.forClass(Club.class);
        verify(conversationSync).sync(any(), any());
        verify(clubRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(saved ->
                assertThat(saved.getConversationId()).isEqualTo(conversationId));
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
}
