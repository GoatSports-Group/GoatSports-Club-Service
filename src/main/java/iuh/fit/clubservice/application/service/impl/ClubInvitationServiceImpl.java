package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.response.ClubInvitationResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubMemberResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubEventPublisher;
import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.service.ClubInvitationService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClubInvitationServiceImpl implements ClubInvitationService {
    private final ClubSupport support;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubInvitationRepository invitationRepository;
    private final ClubEventPublisher eventPublisher;

    @Override
    @Transactional
    public ClubInvitationResponseCommand inviteMember(UUID clubId, UUID inviteeId, String message,
                                                      UUID actorUserId) throws ServiceException {
        Club club = support.requireActiveClub(clubId);
        support.assertManager(club, actorUserId);
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
        // Mời lại sau khi bị từ chối thì được, nhưng không gửi hai lời mời cùng lúc.
        if (invitationRepository.findByClubIdAndInviteeIdAndStatus(
                clubId, inviteeId, ClubInvitationStatus.PENDING).isPresent()) {
            throw new ServiceException("Đã có lời mời đang chờ người này trả lời.");
        }

        ClubInvitation invitation = invitationRepository.save(ClubInvitation.builder()
                .clubId(clubId)
                .inviteeId(inviteeId)
                .invitedBy(actorUserId)
                .status(ClubInvitationStatus.PENDING)
                .message(ClubSupport.normalize(message))
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
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional
    public ClubMemberResponseCommand acceptInvitation(UUID invitationId, UUID userId) throws ServiceException {
        ClubInvitation invitation = requirePendingInvitation(invitationId, userId);
        Club club = support.requireActiveClub(invitation.getClubId());

        ClubMember member = memberRepository.findByClubIdAndUserId(club.getClubId(), userId)
                .orElseGet(ClubMember::new);
        if (member.getStatus() == ClubMemberStatus.BANNED) {
            throw new ServiceException("Bạn đã bị cấm tham gia câu lạc bộ này.");
        }
        member.setClubId(club.getClubId());
        member.setUserId(userId);
        member.setRole(ClubRole.MEMBER);
        // Chính CLB đã mời nên không bắt chờ duyệt thêm một vòng nữa.
        member.setStatus(ClubMemberStatus.ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        member.setRespondedBy(invitation.getInvitedBy());
        member.setRespondedAt(LocalDateTime.now());
        ClubMember saved = memberRepository.save(member);

        close(invitation, ClubInvitationStatus.ACCEPTED);
        support.syncConversation(club);
        eventPublisher.invitationAnswered(club, invitation, true);
        return ClubSupport.toResponse(saved);
    }

    @Override
    @Transactional
    public void declineInvitation(UUID invitationId, UUID userId) throws ServiceException {
        ClubInvitation invitation = requirePendingInvitation(invitationId, userId);
        close(invitation, ClubInvitationStatus.DECLINED);
        clubRepository.findById(invitation.getClubId())
                .ifPresent(club -> eventPublisher.invitationAnswered(club, invitation, false));
    }

    @Override
    @Transactional
    public void cancelInvitation(UUID clubId, UUID invitationId, UUID actorUserId) throws ServiceException {
        Club club = support.requireActiveClub(clubId);
        support.assertManager(club, actorUserId);
        ClubInvitation invitation = invitationRepository.findById(invitationId)
                .filter(item -> item.getClubId().equals(clubId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy lời mời."));
        if (invitation.getStatus() != ClubInvitationStatus.PENDING) {
            throw new ServiceException("Người chơi đã trả lời lời mời này.");
        }
        close(invitation, ClubInvitationStatus.CANCELLED);
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

    private void close(ClubInvitation invitation, ClubInvitationStatus status) {
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
                .club(support.toResponse(club))
                .build();
    }
}
