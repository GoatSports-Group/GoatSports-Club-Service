package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.CreateClubCommand;
import iuh.fit.clubservice.application.dto.request.JoinClubCommand;
import iuh.fit.clubservice.application.dto.request.RespondClubMemberCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubCommand;
import iuh.fit.clubservice.application.dto.response.ClubMemberResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubResponseCommand;
import iuh.fit.clubservice.application.dto.response.MyClubMembershipResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubEventPublisher;
import iuh.fit.clubservice.application.port.external.ClubMediaStorage;
import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.ClubService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static iuh.fit.clubservice.application.service.impl.ClubSupport.normalize;

@Service
@RequiredArgsConstructor
public class ClubServiceImpl implements ClubService {
    /** Giải chưa kết thúc: CLB còn đội trong những giải này thì không được giải tán. */
    private static final Set<TournamentStatus> LIVE_TOURNAMENT_STATUSES = EnumSet.of(
            TournamentStatus.PUBLISHED, TournamentStatus.REGISTRATION_OPEN,
            TournamentStatus.REGISTRATION_CLOSED, TournamentStatus.IN_PROGRESS);

    private final ClubSupport support;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubInvitationRepository invitationRepository;
    private final TournamentRegistrationRepository tournamentRegistrationRepository;
    private final TournamentRepository tournamentRepository;
    private final ClubEventPublisher eventPublisher;
    private final ClubMediaStorage mediaStorage;

    // ---------------------------------------------------------------- câu lạc bộ

    @Override
    public Page<ClubResponseCommand> searchClubs(SportType sportType, String city, String keyword, Pageable pageable) {
        return clubRepository.searchClubs(sportType, normalize(city), normalize(keyword), pageable)
                .map(support::toResponse);
    }

    @Override
    public ClubResponseCommand getClubDetails(UUID clubId) throws ServiceException {
        return support.toResponse(support.requireClub(clubId));
    }

    @Override
    @Transactional
    public ClubResponseCommand createClub(CreateClubCommand command) {
        Club club = clubRepository.save(Club.builder()
                .ownerId(command.getOwnerId())
                .name(command.getName().trim())
                .description(command.getDescription())
                .logoUrl(command.getLogoUrl())
                .city(normalize(command.getCity()))
                .location(normalize(command.getLocation()))
                .tags(normalizeTags(command.getTags()))
                .sportType(command.getSportType())
                .privacy(command.getPrivacy() == null ? ClubPrivacy.PUBLIC : command.getPrivacy())
                .approvalMode(command.getApprovalMode() == null ? ClubApprovalMode.AUTO : command.getApprovalMode())
                .active(true)
                .winCount(0).lossCount(0).drawCount(0).matchCount(0).winRate(0D)
                .build());

        memberRepository.save(ClubMember.builder()
                .clubId(club.getClubId())
                .userId(command.getOwnerId())
                .role(ClubRole.OWNER)
                .status(ClubMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build());
        return support.toResponse(support.syncConversation(club));
    }

    @Override
    @Transactional
    public ClubResponseCommand updateClub(UpdateClubCommand command) throws ServiceException {
        command.validate();
        Club club = support.requireActiveClub(command.getClubId());
        support.assertManager(club, command.getActorUserId());
        if (command.getName() != null) club.setName(command.getName().trim());
        if (command.getDescription() != null) club.setDescription(command.getDescription());
        // Ảnh upload lên vùng tạm; chỉ khi lưu CLB mới chốt (finalize) để ảnh bỏ dở không nằm lại vĩnh viễn.
        if (command.getLogoUrl() != null) {
            club.setLogoUrl(mediaStorage.finalizeUpload(command.getLogoUrl(), "club-logo:" + club.getClubId()));
        }
        if (command.getBannerUrl() != null) {
            club.setBannerUrl(mediaStorage.finalizeUpload(command.getBannerUrl(), "club-banner:" + club.getClubId()));
        }
        if (command.getCity() != null) club.setCity(normalize(command.getCity()));
        if (command.getLocation() != null) club.setLocation(normalize(command.getLocation()));
        if (command.getTags() != null) club.setTags(normalizeTags(command.getTags()));
        if (command.getPrivacy() != null) club.setPrivacy(command.getPrivacy());
        if (command.getApprovalMode() != null) club.setApprovalMode(command.getApprovalMode());
        Club saved = clubRepository.save(club);
        // Tên CLB đổi thì tên chat nhóm phải đổi theo.
        return support.toResponse(support.syncConversation(saved));
    }

    // ---------------------------------------------------------------- thành viên

    @Override
    @Transactional
    public ClubMemberResponseCommand joinClub(JoinClubCommand command) throws ServiceException {
        Club club = support.requireActiveClub(command.getClubId());
        if (!Boolean.TRUE.equals(club.getActive())) {
            throw new ServiceException("Câu lạc bộ hiện không hoạt động.");
        }
        ClubMember member = memberRepository.findByClubIdAndUserId(command.getClubId(), command.getUserId())
                .orElseGet(ClubMember::new);
        if (member.getStatus() == ClubMemberStatus.ACTIVE || member.getStatus() == ClubMemberStatus.PENDING) {
            throw new ServiceException(member.getStatus() == ClubMemberStatus.ACTIVE
                    ? "Bạn đã là thành viên của câu lạc bộ." : "Yêu cầu tham gia đang chờ duyệt.");
        }
        if (member.getStatus() == ClubMemberStatus.BANNED) {
            throw new ServiceException("Bạn đã bị cấm tham gia câu lạc bộ này.");
        }

        // CLB duyệt tự động thì vào ngay; duyệt tay thì chờ ban quản trị.
        ClubMemberStatus status = club.getApprovalMode() == ClubApprovalMode.AUTO
                ? ClubMemberStatus.ACTIVE : ClubMemberStatus.PENDING;
        member.setClubId(command.getClubId());
        member.setUserId(command.getUserId());
        member.setRole(ClubRole.MEMBER);
        member.setStatus(status);
        member.setIntroMessage(status == ClubMemberStatus.PENDING ? normalize(command.getIntroMessage()) : null);
        member.setRequestedAt(status == ClubMemberStatus.PENDING ? LocalDateTime.now() : null);
        member.setJoinedAt(status == ClubMemberStatus.ACTIVE ? LocalDateTime.now() : null);
        member.setRespondedAt(null);
        member.setRespondedBy(null);
        ClubMember saved = memberRepository.save(member);
        if (saved.getStatus() == ClubMemberStatus.ACTIVE) {
            support.syncConversation(club);
        } else {
            eventPublisher.membershipRequested(club, saved, null, managerIds(club));
        }
        return ClubSupport.toResponse(saved);
    }

    @Override
    @Transactional
    public ClubMemberResponseCommand respondMembership(RespondClubMemberCommand command) throws ServiceException {
        ClubMember member = memberRepository.findById(command.getMembershipId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy yêu cầu tham gia."));
        Club club = support.requireActiveClub(member.getClubId());
        support.assertManager(club, command.getActorUserId());
        if (member.getStatus() != ClubMemberStatus.PENDING) {
            throw new ServiceException("Yêu cầu tham gia đã được xử lý.");
        }
        member.setStatus(command.isAccepted() ? ClubMemberStatus.ACTIVE : ClubMemberStatus.REJECTED);
        member.setJoinedAt(command.isAccepted() ? LocalDateTime.now() : null);
        member.setRespondedBy(command.getActorUserId());
        member.setRespondedAt(LocalDateTime.now());
        ClubMember saved = memberRepository.save(member);
        if (command.isAccepted()) support.syncConversation(club);
        // Bị từ chối cũng phải được báo, không để người ta chờ mãi.
        eventPublisher.membershipResponded(club, saved, command.isAccepted());
        return ClubSupport.toResponse(saved);
    }

    @Override
    @Transactional
    public void leaveClub(UUID clubId, UUID userId) throws ServiceException {
        ClubMember member = memberRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new ServiceException("Bạn không phải thành viên của câu lạc bộ."));
        if (member.getRole() == ClubRole.OWNER) {
            throw new ServiceException("Chủ câu lạc bộ không thể rời khi chưa chuyển quyền sở hữu.");
        }
        if (member.getStatus() != ClubMemberStatus.ACTIVE && member.getStatus() != ClubMemberStatus.PENDING) {
            throw new ServiceException("Tư cách thành viên hiện tại không thể rời câu lạc bộ.");
        }
        member.setStatus(ClubMemberStatus.LEFT);
        member.setRespondedAt(LocalDateTime.now());
        memberRepository.save(member);
        clubRepository.findById(clubId).ifPresent(support::syncConversation);
    }

    @Override
    public List<ClubMemberResponseCommand> getClubMembers(UUID clubId, UUID requesterId) throws ServiceException {
        Club club = support.requireClub(clubId);
        boolean manager = requesterId != null && support.isManager(club, requesterId);
        // Yêu cầu chờ duyệt chỉ hiện với ban quản trị.
        return memberRepository.findByClubId(clubId).stream()
                .filter(member -> member.getStatus() == ClubMemberStatus.ACTIVE
                        || (manager && member.getStatus() == ClubMemberStatus.PENDING))
                .map(ClubSupport::toResponse).toList();
    }

    @Override
    public Page<ClubMemberResponseCommand> getClubMembersPage(UUID clubId, UUID requesterId,
            ClubMemberStatus status, Pageable pageable) throws ServiceException {
        Club club = support.requireClub(clubId);
        ClubMemberStatus requestedStatus = status == null ? ClubMemberStatus.ACTIVE : status;
        if (requestedStatus != ClubMemberStatus.ACTIVE
                && (requesterId == null || !support.isManager(club, requesterId))) {
            throw new ServiceException("Chỉ ban quản trị mới có thể xem các yêu cầu tham gia.");
        }
        return memberRepository.findByClubIdAndStatus(clubId, requestedStatus, pageable).map(ClubSupport::toResponse);
    }

    @Override
    public Optional<ClubMemberResponseCommand> getMembership(UUID clubId, UUID userId) {
        return memberRepository.findByClubIdAndUserId(clubId, userId).map(ClubSupport::toResponse);
    }

    @Override
    public List<MyClubMembershipResponseCommand> getMyMemberships(UUID userId, ClubMemberStatus status) {
        return memberRepository.findByUserIdAndStatus(userId, status).stream()
                .map(member -> clubRepository.findById(member.getClubId())
                        .filter(club -> !support.isDisbanded(club))
                        .map(club -> MyClubMembershipResponseCommand.builder()
                                .membershipId(member.getMembershipId())
                                .role(member.getRole())
                                .status(member.getStatus())
                                .joinedAt(member.getJoinedAt())
                                .requestedAt(member.getRequestedAt() == null ? member.getCreatedAt() : member.getRequestedAt())
                                .club(support.toResponse(club))
                                .build())
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    /** Chỉ chủ CLB được phong hoặc hạ quyền; ADMIN tự phong ADMIN khác sẽ làm quyền lan ra ngoài kiểm soát. */
    @Override
    @Transactional
    public ClubMemberResponseCommand changeMemberRole(UUID clubId, UUID membershipId, ClubRole role,
                                                      UUID actorUserId) throws ServiceException {
        Club club = support.requireActiveClub(clubId);
        if (!club.getOwnerId().equals(actorUserId)) {
            throw new ServiceException("Chỉ chủ câu lạc bộ mới đổi được vai trò thành viên.");
        }
        if (role == ClubRole.OWNER) {
            throw new ServiceException("Chuyển quyền sở hữu cần một luồng riêng, không đổi trực tiếp vai trò.");
        }
        ClubMember member = requireMember(clubId, membershipId);
        if (member.getRole() == ClubRole.OWNER) {
            throw new ServiceException("Không thể đổi vai trò của chủ câu lạc bộ.");
        }
        if (member.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new ServiceException("Chỉ đổi được vai trò của thành viên đang hoạt động.");
        }
        member.setRole(role);
        return ClubSupport.toResponse(memberRepository.save(member));
    }

    @Override
    @Transactional
    public void removeMember(UUID clubId, UUID membershipId, boolean ban, UUID actorUserId) throws ServiceException {
        Club club = support.requireActiveClub(clubId);
        support.assertManager(club, actorUserId);
        ClubMember member = requireMember(clubId, membershipId);
        if (member.getRole() == ClubRole.OWNER) {
            throw new ServiceException("Không thể xóa chủ câu lạc bộ khỏi câu lạc bộ.");
        }
        if (member.getUserId().equals(actorUserId)) {
            throw new ServiceException("Bạn hãy dùng chức năng rời câu lạc bộ cho chính mình.");
        }
        ClubMemberStatus target = ban ? ClubMemberStatus.BANNED : ClubMemberStatus.REMOVED;
        if (member.getStatus() == target) return;
        member.setStatus(target);
        member.setRespondedBy(actorUserId);
        member.setRespondedAt(LocalDateTime.now());
        memberRepository.save(member);
        support.syncConversation(club);
    }

    // ---------------------------------------------------------------- sở hữu / giải tán

    /**
     * Ba thay đổi phải nằm trong cùng một transaction, nếu không CLB sẽ có hai OWNER:
     * chủ cũ xuống ADMIN, người kế nhiệm lên OWNER, cập nhật clubs.owner_id. Sau đó đồng bộ chat để chủ cũ
     * không còn giữ quyền quản trị nhóm chat bên social-service.
     */
    @Override
    @Transactional
    public ClubMemberResponseCommand transferOwnership(UUID clubId, UUID membershipId, UUID actorUserId)
            throws ServiceException {
        Club club = support.requireActiveClub(clubId);
        if (!club.getOwnerId().equals(actorUserId)) {
            throw new ServiceException("Chỉ chủ câu lạc bộ mới có thể chuyển quyền sở hữu.");
        }
        ClubMember successor = requireMember(clubId, membershipId);
        if (successor.getUserId().equals(actorUserId)) {
            throw new ServiceException("Bạn đã là chủ câu lạc bộ.");
        }
        if (successor.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new ServiceException("Chỉ chuyển quyền sở hữu cho thành viên đang hoạt động.");
        }

        memberRepository.findByClubIdAndUserId(clubId, actorUserId).ifPresent(previous -> {
            previous.setRole(ClubRole.ADMIN);
            memberRepository.save(previous);
        });
        successor.setRole(ClubRole.OWNER);
        ClubMember saved = memberRepository.save(successor);

        club.setOwnerId(successor.getUserId());
        support.syncConversation(clubRepository.save(club));

        eventPublisher.ownershipTransferred(club, actorUserId, successor.getUserId());
        return ClubSupport.toResponse(saved);
    }

    /** Giải tán là đổi trạng thái, không xóa: lịch sử trận, thành tích và tin nhắn vẫn còn nguyên. */
    @Override
    @Transactional
    public void disbandClub(UUID clubId, UUID actorUserId) throws ServiceException {
        Club club = support.requireActiveClub(clubId);
        if (!club.getOwnerId().equals(actorUserId)) {
            throw new ServiceException("Chỉ chủ câu lạc bộ mới có thể giải tán câu lạc bộ.");
        }
        assertNoLiveTournamentCommitment(clubId);

        List<UUID> memberIds = support.activeMemberIds(clubId);
        club.setActive(false);
        club.setDisbandedAt(LocalDateTime.now());
        club.setDisbandedBy(actorUserId);
        Club saved = clubRepository.save(club);

        invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING).forEach(invitation -> {
            invitation.setStatus(ClubInvitationStatus.CANCELLED);
            invitation.setRespondedAt(LocalDateTime.now());
            invitationRepository.save(invitation);
        });

        eventPublisher.clubDisbanded(saved, actorUserId, memberIds);
    }

    /** CLB còn đội giữ suất (kể cả đội đang chờ đủ người) trong một giải chưa kết thúc thì phải rút trước. */
    private void assertNoLiveTournamentCommitment(UUID clubId) throws ServiceException {
        for (TournamentRegistration registration : tournamentRegistrationRepository.findByClubId(clubId)) {
            if (!TournamentLifecyclePolicy.holdsSeat(registration.getStatus())) continue;
            Tournament tournament = tournamentRepository.findById(registration.getTournamentId()).orElse(null);
            if (tournament == null) continue;
            if (LIVE_TOURNAMENT_STATUSES.contains(tournament.getStatus())) {
                throw new ServiceException("Câu lạc bộ đang tham gia giải \"" + tournament.getName()
                        + "\". Hãy rút đăng ký khỏi giải trước khi giải tán.");
            }
        }
    }

    // ---------------------------------------------------------------- nội bộ

    /** Chủ CLB và quản trị viên đang hoạt động — những người thực sự bấm được nút duyệt. */
    private List<UUID> managerIds(Club club) {
        List<UUID> managers = new ArrayList<>();
        managers.add(club.getOwnerId());
        memberRepository.findByClubIdAndStatus(club.getClubId(), ClubMemberStatus.ACTIVE).stream()
                .filter(member -> member.getRole() == ClubRole.OWNER || member.getRole() == ClubRole.ADMIN)
                .map(ClubMember::getUserId)
                .forEach(managers::add);
        return managers.stream().filter(Objects::nonNull).distinct().toList();
    }

    private ClubMember requireMember(UUID clubId, UUID membershipId) throws ServiceException {
        return memberRepository.findById(membershipId)
                .filter(item -> item.getClubId().equals(clubId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy thành viên của câu lạc bộ."));
    }

    /** Tối đa 5 thẻ, bỏ trùng không phân biệt hoa thường, giữ cách viết của lần xuất hiện đầu tiên. */
    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        tags.stream()
                .map(ClubSupport::normalize)
                .filter(Objects::nonNull)
                .forEach(tag -> unique.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag));
        return unique.values().stream().limit(5).toList();
    }
}
