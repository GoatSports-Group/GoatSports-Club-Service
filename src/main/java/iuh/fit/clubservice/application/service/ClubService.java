package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.CreateClubCommand;
import iuh.fit.clubservice.application.dto.request.JoinClubCommand;
import iuh.fit.clubservice.application.dto.request.RespondClubMemberCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubCommand;
import iuh.fit.clubservice.application.dto.response.ClubMemberResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubResponseCommand;
import iuh.fit.clubservice.application.dto.response.MyClubMembershipResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.SportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Câu lạc bộ và thành viên: tạo/sửa, xin vào/duyệt/rời, phân quyền, chuyển quyền sở hữu, giải tán.
 * Hoạt động, lời mời và lịch sử trận nằm ở {@link ClubActivityService}, {@link ClubInvitationService},
 * {@link ClubMatchService}.
 */
public interface ClubService {

    Page<ClubResponseCommand> searchClubs(SportType sportType, String city, String keyword, Pageable pageable);

    ClubResponseCommand getClubDetails(UUID clubId) throws ServiceException;

    ClubResponseCommand createClub(CreateClubCommand command);

    ClubResponseCommand updateClub(UpdateClubCommand command) throws ServiceException;

    ClubMemberResponseCommand joinClub(JoinClubCommand command) throws ServiceException;

    ClubMemberResponseCommand respondMembership(RespondClubMemberCommand command) throws ServiceException;

    void leaveClub(UUID clubId, UUID userId) throws ServiceException;

    List<ClubMemberResponseCommand> getClubMembers(UUID clubId, UUID requesterId) throws ServiceException;

    Page<ClubMemberResponseCommand> getClubMembersPage(UUID clubId, UUID requesterId,
            ClubMemberStatus status, Pageable pageable) throws ServiceException;

    Optional<ClubMemberResponseCommand> getMembership(UUID clubId, UUID userId);

    List<MyClubMembershipResponseCommand> getMyMemberships(UUID userId, ClubMemberStatus status);

    ClubMemberResponseCommand changeMemberRole(UUID clubId, UUID membershipId, ClubRole role, UUID actorUserId)
            throws ServiceException;

    void removeMember(UUID clubId, UUID membershipId, boolean ban, UUID actorUserId) throws ServiceException;

    ClubMemberResponseCommand transferOwnership(UUID clubId, UUID membershipId, UUID actorUserId)
            throws ServiceException;

    void disbandClub(UUID clubId, UUID actorUserId) throws ServiceException;
}
