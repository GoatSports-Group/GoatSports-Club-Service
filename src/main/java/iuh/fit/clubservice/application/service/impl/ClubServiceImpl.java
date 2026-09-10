package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.*;
import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.*;
import iuh.fit.clubservice.application.service.ClubService;
import iuh.fit.clubservice.domain.entity.*;
import iuh.fit.clubservice.domain.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClubServiceImpl implements ClubService {
    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubActivityRepository activityRepository;

    @Override
    public Page<ClubResponseCommand> searchClubs(SportType sportType, String keyword, Pageable pageable) {
        return clubRepository.searchClubs(sportType, normalize(keyword), pageable).map(this::toResponse);
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
        return toResponse(club);
    }

    @Override
    @Transactional
    public ClubMemberResponseCommand joinClub(JoinClubCommand command) throws ServiceException {
        Club club = requireClub(command.getClubId());
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
        member.setJoinedAt(status == ClubMemberStatus.ACTIVE ? LocalDateTime.now() : null);
        member.setRespondedAt(null);
        member.setRespondedBy(null);
        return toResponse(memberRepository.save(member));
    }

    @Override
    @Transactional
    public ClubMemberResponseCommand respondMembership(RespondClubMemberCommand command) throws ServiceException {
        ClubMember member = memberRepository.findById(command.getMembershipId())
                .orElseThrow(() -> new ServiceException("Không tìm thấy yêu cầu tham gia."));
        Club club = requireClub(member.getClubId());
        assertManager(club, command.getActorUserId());
        if (member.getStatus() != ClubMemberStatus.PENDING) {
            throw new ServiceException("Yêu cầu tham gia đã được xử lý.");
        }
        member.setStatus(command.isAccepted() ? ClubMemberStatus.ACTIVE : ClubMemberStatus.REJECTED);
        member.setJoinedAt(command.isAccepted() ? LocalDateTime.now() : null);
        member.setRespondedBy(command.getActorUserId());
        member.setRespondedAt(LocalDateTime.now());
        return toResponse(memberRepository.save(member));
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
    public Optional<ClubMemberResponseCommand> getMembership(UUID clubId, UUID userId) {
        return memberRepository.findByClubIdAndUserId(clubId, userId).map(this::toResponse);
    }

    @Override
    public List<ClubActivityResponseCommand> getClubActivities(UUID clubId) {
        return activityRepository.findByClubId(clubId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ClubActivityResponseCommand createClubActivity(CreateClubActivityCommand command) throws ServiceException {
        command.validate();
        Club club = requireClub(command.getClubId());
        assertManager(club, command.getCreatorId());
        return toResponse(activityRepository.save(ClubActivity.builder()
                .clubId(command.getClubId())
                .title(command.getTitle().trim())
                .description(command.getDescription())
                .venueId(command.getVenueId())
                .startAt(command.getStartAt())
                .endAt(command.getEndAt())
                .createdByUserId(command.getCreatorId())
                .build()));
    }

    private Club requireClub(UUID clubId) throws ServiceException {
        return clubRepository.findById(clubId).orElseThrow(() -> new ServiceException("Không tìm thấy câu lạc bộ."));
    }

    private void assertManager(Club club, UUID userId) throws ServiceException {
        if (!isManager(club, userId)) throw new ServiceException("Bạn không có quyền quản lý câu lạc bộ.");
    }

    private boolean isManager(Club club, UUID userId) {
        if (club.getOwnerId().equals(userId)) return true;
        return memberRepository.findByClubIdAndUserId(club.getClubId(), userId)
                .map(member -> member.getStatus() == ClubMemberStatus.ACTIVE && member.getRole() == ClubRole.ADMIN)
                .orElse(false);
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private ClubResponseCommand toResponse(Club c) {
        return ClubResponseCommand.builder()
                .clubId(c.getClubId()).ownerId(c.getOwnerId()).name(c.getName()).description(c.getDescription())
                .logoUrl(c.getLogoUrl()).sportType(c.getSportType()).privacy(c.getPrivacy())
                .approvalMode(c.getApprovalMode()).conversationId(c.getConversationId()).active(c.getActive())
                .winCount(c.getWinCount()).lossCount(c.getLossCount()).drawCount(c.getDrawCount())
                .matchCount(c.getMatchCount()).winRate(c.getWinRate())
                .memberCount(memberRepository.countByClubIdAndStatus(c.getClubId(), ClubMemberStatus.ACTIVE))
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt()).build();
    }

    private ClubMemberResponseCommand toResponse(ClubMember m) {
        return ClubMemberResponseCommand.builder().membershipId(m.getMembershipId()).clubId(m.getClubId())
                .userId(m.getUserId()).role(m.getRole()).status(m.getStatus()).joinedAt(m.getJoinedAt())
                .respondedBy(m.getRespondedBy()).respondedAt(m.getRespondedAt()).build();
    }

    private ClubActivityResponseCommand toResponse(ClubActivity a) {
        return ClubActivityResponseCommand.builder().activityId(a.getActivityId()).clubId(a.getClubId())
                .title(a.getTitle()).description(a.getDescription()).startAt(a.getStartAt()).endAt(a.getEndAt())
                .venueId(a.getVenueId()).createdBy(a.getCreatedByUserId()).createdAt(a.getCreatedAt()).build();
    }
}
