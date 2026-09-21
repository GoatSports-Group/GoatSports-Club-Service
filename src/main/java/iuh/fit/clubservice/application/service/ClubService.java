package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.CreateClubActivityCommand;
import iuh.fit.clubservice.application.dto.request.CreateClubCommand;
import iuh.fit.clubservice.application.dto.request.JoinClubCommand;
import iuh.fit.clubservice.application.dto.request.RespondClubMemberCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubActivityCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubCommand;
import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.SportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubService {
    ClubInvitationResponseCommand inviteMember(UUID clubID, UUID inviteeId, String message, UUID actorUserId) throws ServiceException;

    List<ClubInvitationResponseCommand> getMyInvitations(UUID userId);

    ClubMemberResponseCommand acceptInvitation(UUID invitationId, UUID userId) throws ServiceException;

    void declineInvitation(UUID invitationId, UUID userId) throws ServiceException;

    List<MyClubMembershipResponseCommand> getMyMemberships(UUID userId, ClubMemberStatus status);

    List<ClubActivityResponseCommand> getMyUpcomingActivities(UUID userId, int limit);

    Page<ClubActivityResponseCommand> getMyUpcomingActivitiesPage(UUID userId, Pageable pageable);

    Page<ClubResponseCommand> searchClubs(SportType sportType, String city, String keyword, Pageable pageable);

    ClubResponseCommand getClubDetails(UUID clubId) throws ServiceException;

    ClubResponseCommand createClub(CreateClubCommand command);

    ClubMemberResponseCommand joinClub(JoinClubCommand command) throws ServiceException;

    ClubMemberResponseCommand respondMembership(RespondClubMemberCommand command) throws ServiceException;

    void leaveClub(UUID clubId, UUID userId) throws ServiceException;

    List<ClubMemberResponseCommand> getClubMembers(UUID clubId, UUID requesterId) throws ServiceException;

    Page<ClubMemberResponseCommand> getClubMembersPage(UUID clubId, UUID requesterId,
            ClubMemberStatus status, Pageable pageable) throws ServiceException;

    Optional<ClubMemberResponseCommand> getMembership(UUID clubId, UUID userId);

    List<ClubActivityResponseCommand> getClubActivities(UUID clubId);

    Page<ClubActivityResponseCommand> getClubActivitiesPage(UUID clubId, Pageable pageable);

    List<ClubRecentMatchResponseCommand> getClubRecentMatches(UUID clubId, int limit) throws ServiceException;

    ClubActivityResponseCommand createClubActivity(CreateClubActivityCommand command) throws ServiceException;

    ClubResponseCommand updateClub(UpdateClubCommand command) throws ServiceException;

    ClubMemberResponseCommand changeMemberRole(UUID clubId, UUID membershipId, ClubRole role, UUID actorUserId)
            throws ServiceException;

    void removeMember(UUID clubId, UUID membershipId, boolean ban, UUID actorUserId) throws ServiceException;

    ClubActivityResponseCommand updateClubActivity(UpdateClubActivityCommand command) throws ServiceException;

    void deleteClubActivity(UUID clubId, UUID activityId, UUID actorUserId) throws ServiceException;
}
