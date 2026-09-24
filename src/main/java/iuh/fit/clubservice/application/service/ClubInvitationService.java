package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.response.ClubInvitationResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubMemberResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;

import java.util.List;
import java.util.UUID;

/**
 * CLB chủ động mời người chơi (thường từ bảng tuyển quân). Người được mời nhận lời thì vào thẳng CLB,
 * không qua bước duyệt vì chính ban quản trị đã mời.
 */
public interface ClubInvitationService {

    ClubInvitationResponseCommand inviteMember(UUID clubId, UUID inviteeId, String message, UUID actorUserId)
            throws ServiceException;

    List<ClubInvitationResponseCommand> getMyInvitations(UUID userId);

    ClubMemberResponseCommand acceptInvitation(UUID invitationId, UUID userId) throws ServiceException;

    void declineInvitation(UUID invitationId, UUID userId) throws ServiceException;

    void cancelInvitation(UUID clubId, UUID invitationId, UUID actorUserId) throws ServiceException;
}
