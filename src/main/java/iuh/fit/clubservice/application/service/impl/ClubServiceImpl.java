package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.*;
import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.dto.request.UpdateClubCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubActivityCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.external.ClubEventPublisher;
import iuh.fit.clubservice.application.port.external.ClubMediaStorage;
import iuh.fit.clubservice.application.port.persistence.*;
import iuh.fit.clubservice.application.service.ClubService;
import iuh.fit.clubservice.domain.entity.*;
import iuh.fit.clubservice.domain.enums.*;
import iuh.fit.clubservice.domain.service.ClubAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClubServiceImpl implements ClubService {
    private static final Set<TournamentStatus> LIVE_TOURNAMENT_STATUSES = EnumSet.of(
            TournamentStatus.PUBLISHED, TournamentStatus.REGISTRATION_OPEN,
            TournamentStatus.REGISTRATION_CLOSED, TournamentStatus.IN_PROGRESS);

    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubActivityRepository activityRepository;
    private final ClubInvitationRepository invitationRepository;
    private final TournamentRegistrationRepository tournamentRegistrationRepository;
    private final TournamentFixtureRepository tournamentFixtureRepository;
    private final TournamentRepository tournamentRepository;
    private final ClubConversationSync conversationSync;
    private final ClubEventPublisher eventPublisher;
    private final ClubMediaStorage mediaStorage;

    @Override
    public Page<ClubResponseCommand> searchClubs(SportType sportType, String city, String keyword,
                                                Pageable pageable) {
        return clubRepository.searchClubs(sportType, normalize(city), normalize(keyword), pageable)
                .map(this::toResponse);
    }

    @Override
    public ClubResponseCommand getClubDetails(UUID clubId) throws ServiceException {
        return toResponse(requireClub(clubId));
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
        return toResponse(syncConversation(club));
    }

    @Override
    @Transactional
    public ClubMemberResponseCommand joinClub(JoinClubCommand command) throws ServiceException {
        Club club = requireActiveClub(command.getClubId());
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
            syncConversation(club);
        } else {
            eventPublisher.membershipRequested(club, saved, null, managerIds(club));
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ClubMemberResponseCommand respondMembership(RespondClubMemberCommand command) throws ServiceException {
        ClubMember member = memberRepository.findById(command.getMembershipId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy yêu cầu tham gia."));
        Club club = requireActiveClub(member.getClubId());
        assertManager(club, command.getActorUserId());
        if (member.getStatus() != ClubMemberStatus.PENDING) {
            throw new ServiceException("Yêu cầu tham gia đã được xử lý.");
        }
        member.setStatus(command.isAccepted() ? ClubMemberStatus.ACTIVE : ClubMemberStatus.REJECTED);
        member.setJoinedAt(command.isAccepted() ? LocalDateTime.now() : null);
        member.setRespondedBy(command.getActorUserId());
        member.setRespondedAt(LocalDateTime.now());
        ClubMember saved = memberRepository.save(member);
        if (command.isAccepted()) syncConversation(club);
        // Bi tu choi cung phai duoc bao, khong de nguoi ta cho mai.
        eventPublisher.membershipResponded(club, saved, command.isAccepted());
        return toResponse(saved);
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
        clubRepository.findById(clubId).ifPresent(this::syncConversation);
    }

    @Override
    public List<ClubMemberResponseCommand> getClubMembers(UUID clubId, UUID requesterId) throws ServiceException {
        Club club = requireClub(clubId);
        boolean manager = requesterId != null && isManager(club, requesterId);
        return memberRepository.findByClubId(clubId).stream()
                .filter(member -> member.getStatus() == ClubMemberStatus.ACTIVE
                        || (manager && member.getStatus() == ClubMemberStatus.PENDING))
                .map(this::toResponse).toList();
    }

    @Override
    public Page<ClubMemberResponseCommand> getClubMembersPage(UUID clubId, UUID requesterId,
            ClubMemberStatus status, Pageable pageable) throws ServiceException {
        Club club = requireClub(clubId);
        ClubMemberStatus requestedStatus = status == null ? ClubMemberStatus.ACTIVE : status;
        if (requestedStatus != ClubMemberStatus.ACTIVE
                && (requesterId == null || !isManager(club, requesterId))) {
            throw new ServiceException("Chỉ ban quản trị mới có thể xem các yêu cầu tham gia.");
        }
        return memberRepository.findByClubIdAndStatus(clubId, requestedStatus, pageable).map(this::toResponse);
    }

    @Override
    public Optional<ClubMemberResponseCommand> getMembership(UUID clubId, UUID userId) {
        return memberRepository.findByClubIdAndUserId(clubId, userId).map(this::toResponse);
    }

    @Override
    public List<ClubActivityResponseCommand> getClubActivities(UUID clubId) {
        return activityRepository.findByClubId(clubId).stream().map(this::toResponse).toList();
    }

    @Override
    public Page<ClubActivityResponseCommand> getClubActivitiesPage(UUID clubId, Pageable pageable) {
        return activityRepository.findUpcomingByClubId(clubId, LocalDateTime.now(), pageable)
                .map(this::toResponse);
    }

    @Override
    public List<ClubRecentMatchResponseCommand> getClubRecentMatches(UUID clubId, int limit)
            throws ServiceException {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return collectClubMatches(clubId).stream().limit(safeLimit).toList();
    }

    @Override
    public Page<ClubRecentMatchResponseCommand> getClubMatchesPage(UUID clubId, Pageable pageable)
            throws ServiceException {
        List<ClubRecentMatchResponseCommand> matches = collectClubMatches(clubId);
        int start = Math.toIntExact(Math.min(pageable.getOffset(), matches.size()));
        int end = Math.min(start + pageable.getPageSize(), matches.size());
        return new PageImpl<>(matches.subList(start, end), pageable, matches.size());
    }

    private List<ClubRecentMatchResponseCommand> collectClubMatches(UUID clubId) throws ServiceException {
        requireClub(clubId);
        List<ClubRecentMatchResponseCommand> matches = new ArrayList<>();
        for (TournamentRegistration clubRegistration : tournamentRegistrationRepository.findByClubId(clubId)) {
            for (TournamentFixture fixture : tournamentFixtureRepository
                    .findByTournamentId(clubRegistration.getTournamentId())) {
                if (fixture.getStatus() != MatchStatus.COMPLETED
                        || fixture.getScore1() == null || fixture.getScore2() == null) continue;
                boolean clubIsFirst = clubRegistration.getRegistrationId().equals(fixture.getRegistration1Id());
                boolean clubIsSecond = clubRegistration.getRegistrationId().equals(fixture.getRegistration2Id());
                if (!clubIsFirst && !clubIsSecond) continue;

                UUID opponentRegistrationId = clubIsFirst
                        ? fixture.getRegistration2Id() : fixture.getRegistration1Id();
                String opponentName = opponentRegistrationId == null ? "Đối thủ chờ xác định"
                        : tournamentRegistrationRepository.findById(opponentRegistrationId)
                        .map(TournamentRegistration::getTeamName)
                        .filter(name -> name != null && !name.isBlank())
                        .orElse("Đội bạn");
                int clubScore = clubIsFirst ? fixture.getScore1() : fixture.getScore2();
                int opponentScore = clubIsFirst ? fixture.getScore2() : fixture.getScore1();
                String result = clubScore > opponentScore ? "WIN" : clubScore < opponentScore ? "LOSS" : "DRAW";
                LocalDateTime playedAt = fixture.getCreatedAt() != null
                        ? fixture.getCreatedAt() : fixture.getUpdatedAt();
                String tournamentName = tournamentRepository.findById(fixture.getTournamentId())
                        .map(Tournament::getName).orElse("Giải đấu GOAT Sports");
                matches.add(ClubRecentMatchResponseCommand.builder()
                        .matchId(fixture.getFixtureId())
                        .tournamentId(fixture.getTournamentId())
                        .tournamentName(tournamentName)
                        .opponentName(opponentName)
                        .playedAt(playedAt)
                        .clubScore(clubScore)
                        .opponentScore(opponentScore)
                        .result(result)
                        .build());
            }
        }
        return matches.stream()
                .sorted(java.util.Comparator.comparing(ClubRecentMatchResponseCommand::getPlayedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }

    @Override
    @Transactional
    public ClubActivityResponseCommand createClubActivity(CreateClubActivityCommand command) throws ServiceException {
        command.validate();
        Club club = requireActiveClub(command.getClubId());
        assertManager(club, command.getCreatorId());
        ClubActivity activity = activityRepository.save(ClubActivity.builder()
                .clubId(command.getClubId())
                .title(command.getTitle().trim())
                .description(command.getDescription())
                .venueId(command.getVenueId())
                .startAt(command.getStartAt())
                .endAt(command.getEndAt())
                .createdByUserId(command.getCreatorId())
                .build());
        // Nguoi tao khong can tu bao minh.
        List<UUID> receivers = activeMemberIds(club.getClubId()).stream()
                .filter(userId -> !userId.equals(command.getCreatorId())).toList();
        eventPublisher.activityCreated(club, activity, receivers);
        return toResponse(activity);
    }

    /**
     * Day toan bo danh sach thanh vien dang hoat dong sang social-service.
     * Dong bo ca danh sach thay vi them/bot tung nguoi nen lan goi loi se tu khoi o lan doi thanh vien ke tiep.
     */
    /** Chủ CLB và quản trị viên đang hoạt động — những người thực sự bấm được nút duyệt. */
    private List<UUID> managerIds(Club club) {
        List<UUID> managers = new ArrayList<>();
        managers.add(club.getOwnerId());
        memberRepository.findByClubIdAndStatus(club.getClubId(), ClubMemberStatus.ACTIVE).stream()
                .filter(member -> member.getRole() == ClubRole.OWNER || member.getRole() == ClubRole.ADMIN)
                .map(ClubMember::getUserId)
                .forEach(managers::add);
        return managers.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private Club syncConversation(Club club) {
        conversationSync.sync(club, activeMemberIds(club.getClubId())).ifPresent(conversationId -> {
            if (!conversationId.equals(club.getConversationId())) {
                club.setConversationId(conversationId);
                clubRepository.save(club);
            }
        });
        return club;
    }

    @Override
    @Transactional
    public ClubResponseCommand updateClub(UpdateClubCommand command) throws ServiceException {
        command.validate();
        Club club = requireActiveClub(command.getClubId());
        assertManager(club, command.getActorUserId());
        if (command.getName() != null) club.setName(command.getName().trim());
        if (command.getDescription() != null) club.setDescription(command.getDescription());
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
        // Ten CLB doi thi ten chat nhom phai doi theo.
        return toResponse(syncConversation(saved));
    }

    /** Chi chu CLB duoc phong hoac ha quyen; ADMIN tu phong ADMIN khac se lam quyen tran ra khong kiem soat. */
    @Override
    @Transactional
    public ClubMemberResponseCommand changeMemberRole(UUID clubId, UUID membershipId, ClubRole role,
                                                      UUID actorUserId) throws ServiceException {
        Club club = requireActiveClub(clubId);
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
        return toResponse(memberRepository.save(member));
    }

    @Override
    @Transactional
    public void removeMember(UUID clubId, UUID membershipId, boolean ban, UUID actorUserId)
            throws ServiceException {
        Club club = requireActiveClub(clubId);
        assertManager(club, actorUserId);
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
        syncConversation(club);
    }

    @Override
    @Transactional
    public ClubActivityResponseCommand updateClubActivity(UpdateClubActivityCommand command)
            throws ServiceException {
        command.validate();
        Club club = requireActiveClub(command.getClubId());
        assertManager(club, command.getActorUserId());
        ClubActivity activity = requireActivity(command.getClubId(), command.getActivityId());
        activity.setTitle(command.getTitle().trim());
        activity.setDescription(command.getDescription());
        activity.setVenueId(command.getVenueId());
        activity.setStartAt(command.getStartAt());
        activity.setEndAt(command.getEndAt());
        return toResponse(activityRepository.save(activity));
    }

    @Override
    @Transactional
    public void deleteClubActivity(UUID clubId, UUID activityId, UUID actorUserId) throws ServiceException {
        Club club = requireActiveClub(clubId);
        assertManager(club, actorUserId);
        requireActivity(clubId, activityId);
        activityRepository.deleteById(activityId);
    }

    private List<UUID> activeMemberIds(UUID clubId) {
        return memberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream().map(ClubMember::getUserId).toList();
    }

    private ClubMember requireMember(UUID clubId, UUID membershipId) throws ServiceException {
        return memberRepository.findById(membershipId)
                .filter(item -> item.getClubId().equals(clubId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy thành viên của câu lạc bộ."));
    }

    private ClubActivity requireActivity(UUID clubId, UUID activityId) throws ServiceException {
        return activityRepository.findById(activityId)
                .filter(item -> item.getClubId().equals(clubId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy hoạt động của câu lạc bộ."));
    }

    @Override
    @Transactional
    public ClubMemberResponseCommand transferOwnership(UUID clubId, UUID membershipId, UUID actorUserId)
            throws ServiceException {
        Club club = requireActiveClub(clubId);
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
        syncConversation(clubRepository.save(club));

        eventPublisher.ownershipTransferred(club, actorUserId, successor.getUserId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void disbandClub(UUID clubId, UUID actorUserId) throws ServiceException {
        Club club = requireActiveClub(clubId);
        if (!club.getOwnerId().equals(actorUserId)) {
            throw new ServiceException("Chỉ chủ câu lạc bộ mới có thể giải tán câu lạc bộ.");
        }
        assertNoLiveTournamentCommitment(clubId);

        List<UUID> memberIds = activeMemberIds(clubId);
        club.setActive(false);
        club.setDisbandedAt(LocalDateTime.now());
        club.setDisbandedBy(actorUserId);
        Club saved = clubRepository.save(club);

        invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING)
                .forEach(invitation -> closeInvitation(invitation, ClubInvitationStatus.CANCELLED));

        eventPublisher.clubDisbanded(saved, actorUserId, memberIds);
    }

    private void assertNoLiveTournamentCommitment(UUID clubId) throws ServiceException {
        for (TournamentRegistration registration : tournamentRegistrationRepository.findByClubId(clubId)) {
            RegistrationStatus status = registration.getStatus();
            if (status != RegistrationStatus.PENDING_ELIGIBILITY
                    && status != RegistrationStatus.PENDING_PAYMENT
                    && status != RegistrationStatus.CONFIRMED) {
                continue;
            }
            Tournament tournament = tournamentRepository.findById(registration.getTournamentId()).orElse(null);
            if (tournament == null) continue;
            if (LIVE_TOURNAMENT_STATUSES.contains(tournament.getStatus())) {
                throw new ServiceException("Câu lạc bộ đang tham gia giải \"" + tournament.getName()
                        + "\". Hãy rút đăng ký khỏi giải trước khi giải tán.");
            }
        }
    }

    private Club requireClub(UUID clubId) throws ServiceException {
        return clubRepository.findById(clubId).orElseThrow(() -> new ServiceException("Không tìm thấy câu lạc bộ."));
    }

    private Club requireActiveClub(UUID clubId) throws ServiceException {
        Club club = requireClub(clubId);
        if (isDisbanded(club)) throw new ServiceException("Câu lạc bộ đã giải tán.");
        return club;
    }

    private boolean isDisbanded(Club club) {
        return club.getDisbandedAt() != null || Boolean.FALSE.equals(club.getActive());
    }

    private void assertManager(Club club, UUID userId) throws ServiceException {
        if (!isManager(club, userId)) throw new ServiceException("Bạn không có quyền quản lý câu lạc bộ.");
    }

    private boolean isManager(Club club, UUID userId) {
        return ClubAccessPolicy.isManager(club,
                memberRepository.findByClubIdAndUserId(club.getClubId(), userId).orElse(null), userId);
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        tags.stream()
                .map(this::normalize)
                .filter(java.util.Objects::nonNull)
                .forEach(tag -> unique.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag));
        return unique.values().stream().limit(5).toList();
    }

    @Override
    public List<MyClubMembershipResponseCommand> getMyMemberships(UUID userId, ClubMemberStatus status) {
        return memberRepository.findByUserIdAndStatus(userId, status).stream()
                .map(member -> clubRepository.findById(member.getClubId())
                        .filter(club -> !isDisbanded(club))
                        .map(club -> MyClubMembershipResponseCommand.builder()
                                .membershipId(member.getMembershipId())
                                .role(member.getRole())
                                .status(member.getStatus())
                                .joinedAt(member.getJoinedAt())
                                .requestedAt(member.getRequestedAt() == null ? member.getCreatedAt() : member.getRequestedAt())
                                .club(toResponse(club))
                                .build())
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public List<ClubActivityResponseCommand> getMyUpcomingActivities(UUID userId, int limit) {
        List<UUID> clubIds = activeClubIdsForUser(userId);
        // Lay tu bay gio tro di: hoat dong da qua khong con de lam gi o man hinh "sap toi".
        return activityRepository.findUpcomingByClubIds(clubIds, LocalDateTime.now()).stream()
                .limit(limit <= 0 ? 10 : limit)
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Page<ClubActivityResponseCommand> getMyUpcomingActivitiesPage(UUID userId, Pageable pageable) {
        List<UUID> clubIds = activeClubIdsForUser(userId);
        return activityRepository.findUpcomingByClubIds(clubIds, LocalDateTime.now(), pageable)
                .map(this::toResponse);
    }

    private List<UUID> activeClubIdsForUser(UUID userId) {
        return memberRepository.findByUserIdAndStatus(userId, ClubMemberStatus.ACTIVE).stream()
                .map(ClubMember::getClubId)
                .filter(clubId -> clubRepository.findById(clubId)
                        .map(club -> !isDisbanded(club))
                        .orElse(false))
                .toList();
    }

    @Override
    @Transactional
    public ClubInvitationResponseCommand inviteMember(UUID clubId, UUID inviteeId, String message,
                                                      UUID actorUserId) throws ServiceException {
        Club club = requireActiveClub(clubId);
        assertManager(club, actorUserId);
        if (inviteeId == null) throw new ServiceException("Thiếu người được mời.");
        if (inviteeId.equals(actorUserId)) throw new ServiceException("Không thể tự mời chính mình.");

        ClubMember existing = memberRepository.findByClubIdAndUserId(clubId, inviteeId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == ClubMemberStatus.ACTIVE) {
                throw new ServiceException("Người này đã là thành viên của câu lạc bộ.");
            }
            if (existing.getStatus() == ClubMemberStatus.PENDING) {
                throw new ServiceException("Người này đang có yêu cầu tham gia chờ duyệt.");
            }
            if (existing.getStatus() == ClubMemberStatus.BANNED) {
                throw new ServiceException("Người này đang bị cấm tham gia câu lạc bộ.");
            }
        }
        // Moi lai sau khi bi tu choi thi duoc, nhung khong gui hai loi moi cung luc.
        if (invitationRepository.findByClubIdAndInviteeIdAndStatus(
                clubId, inviteeId, ClubInvitationStatus.PENDING).isPresent()) {
            throw new ServiceException("Đã có lời mời đang chờ người này trả lời.");
        }

        ClubInvitation invitation = invitationRepository.save(ClubInvitation.builder()
                .clubId(clubId)
                .inviteeId(inviteeId)
                .invitedBy(actorUserId)
                .status(ClubInvitationStatus.PENDING)
                .message(normalize(message))
                .build());
        eventPublisher.invitationSent(club, invitation);
        return toResponse(invitation, club);
    }

    @Override
    public List<ClubInvitationResponseCommand> getMyInvitations(UUID userId) {
        return invitationRepository.findByInviteeIdAndStatus(userId, ClubInvitationStatus.PENDING).stream()
                .map(invitation -> clubRepository.findById(invitation.getClubId())
                        .map(club -> toResponse(invitation, club))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional
    public ClubMemberResponseCommand acceptInvitation(UUID invitationId, UUID userId) throws ServiceException {
        ClubInvitation invitation = requirePendingInvitation(invitationId, userId);
        Club club = requireActiveClub(invitation.getClubId());

        ClubMember member = memberRepository.findByClubIdAndUserId(club.getClubId(), userId)
                .orElseGet(ClubMember::new);
        if (member.getStatus() == ClubMemberStatus.BANNED) {
            throw new ServiceException("Bạn đã bị cấm tham gia câu lạc bộ này.");
        }
        member.setClubId(club.getClubId());
        member.setUserId(userId);
        member.setRole(ClubRole.MEMBER);
        // Chinh CLB da moi nen khong bat cho duyet them mot vong nua.
        member.setStatus(ClubMemberStatus.ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        member.setRespondedBy(invitation.getInvitedBy());
        member.setRespondedAt(LocalDateTime.now());
        ClubMember saved = memberRepository.save(member);

        closeInvitation(invitation, ClubInvitationStatus.ACCEPTED);
        syncConversation(club);
        eventPublisher.invitationAnswered(club, invitation, true);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void declineInvitation(UUID invitationId, UUID userId) throws ServiceException {
        ClubInvitation invitation = requirePendingInvitation(invitationId, userId);
        closeInvitation(invitation, ClubInvitationStatus.DECLINED);
        clubRepository.findById(invitation.getClubId())
                .ifPresent(club -> eventPublisher.invitationAnswered(club, invitation, false));
    }

    @Override
    @Transactional
    public void cancelInvitation(UUID clubId, UUID invitationId, UUID actorUserId) throws ServiceException {
        Club club = requireActiveClub(clubId);
        assertManager(club, actorUserId);
        ClubInvitation invitation = invitationRepository.findById(invitationId)
                .filter(item -> item.getClubId().equals(clubId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy lời mời."));
        if (invitation.getStatus() != ClubInvitationStatus.PENDING) {
            throw new ServiceException("Người chơi đã trả lời lời mời này.");
        }
        closeInvitation(invitation, ClubInvitationStatus.CANCELLED);
    }

    private ClubInvitation requirePendingInvitation(UUID invitationId, UUID userId) throws ServiceException {
        ClubInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy lời mời."));
        if (!invitation.getInviteeId().equals(userId)) {
            throw new ServiceException("Lời mời này không dành cho bạn.");
        }
        if (invitation.getStatus() != ClubInvitationStatus.PENDING) {
            throw new ServiceException("Lời mời đã được xử lý.");
        }
        return invitation;
    }

    private void closeInvitation(ClubInvitation invitation, ClubInvitationStatus status) {
        invitation.setStatus(status);
        invitation.setRespondedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
    }

    private ClubInvitationResponseCommand toResponse(ClubInvitation invitation, Club club) {
        return ClubInvitationResponseCommand.builder()
                .invitationId(invitation.getInvitationId())
                .inviteeId(invitation.getInviteeId())
                .invitedBy(invitation.getInvitedBy())
                .status(invitation.getStatus())
                .message(invitation.getMessage())
                .createdAt(invitation.getCreatedAt())
                .respondedAt(invitation.getRespondedAt())
                .club(toResponse(club))
                .build();
    }

    private ClubResponseCommand toResponse(Club c) {
        return ClubResponseCommand.builder()
                .clubId(c.getClubId()).ownerId(c.getOwnerId()).name(c.getName()).description(c.getDescription())
                .logoUrl(c.getLogoUrl()).bannerUrl(c.getBannerUrl()).city(c.getCity()).location(c.getLocation()).tags(c.getTags())
                .sportType(c.getSportType()).privacy(c.getPrivacy())
                .approvalMode(c.getApprovalMode()).conversationId(c.getConversationId()).active(c.getActive())
                .disbandedAt(c.getDisbandedAt())
                .winCount(c.getWinCount()).lossCount(c.getLossCount()).drawCount(c.getDrawCount())
                .matchCount(c.getMatchCount()).winRate(c.getWinRate())
                .memberCount(memberRepository.countByClubIdAndStatus(c.getClubId(), ClubMemberStatus.ACTIVE))
                .tournamentCount(tournamentRegistrationRepository.countDistinctTournamentIdByClubId(c.getClubId()))
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt()).build();
    }

    private ClubMemberResponseCommand toResponse(ClubMember m) {
        return ClubMemberResponseCommand.builder().membershipId(m.getMembershipId()).clubId(m.getClubId())
                .userId(m.getUserId()).role(m.getRole()).status(m.getStatus())
                .introMessage(m.getIntroMessage()).requestedAt(m.getRequestedAt()).joinedAt(m.getJoinedAt())
                .respondedBy(m.getRespondedBy()).respondedAt(m.getRespondedAt()).build();
    }

    private ClubActivityResponseCommand toResponse(ClubActivity a) {
        return ClubActivityResponseCommand.builder().activityId(a.getActivityId()).clubId(a.getClubId())
                .title(a.getTitle()).description(a.getDescription()).startAt(a.getStartAt()).endAt(a.getEndAt())
                .venueId(a.getVenueId()).createdBy(a.getCreatedByUserId()).createdAt(a.getCreatedAt()).build();
    }
}
