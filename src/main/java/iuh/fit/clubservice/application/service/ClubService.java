package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.CreateClubActivityCommand;
import iuh.fit.clubservice.application.dto.request.CreateClubCommand;
import iuh.fit.clubservice.application.dto.request.JoinClubCommand;
import iuh.fit.clubservice.application.dto.request.RespondClubMemberCommand;
import iuh.fit.clubservice.application.dto.response.ClubActivityResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubMemberResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.SportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubService {
    Page<ClubResponseCommand> searchClubs(SportType sportType, String keyword, Pageable pageable);
    ClubResponseCommand getClubDetails(UUID clubId) throws ServiceException;
    ClubResponseCommand createClub(CreateClubCommand command);
    ClubMemberResponseCommand joinClub(JoinClubCommand command) throws ServiceException;
    ClubMemberResponseCommand respondMembership(RespondClubMemberCommand command) throws ServiceException;
    void leaveClub(UUID clubId, UUID userId) throws ServiceException;
    List<ClubMemberResponseCommand> getClubMembers(UUID clubId, UUID requesterId) throws ServiceException;
    Optional<ClubMemberResponseCommand> getMembership(UUID clubId, UUID userId);
    List<ClubActivityResponseCommand> getClubActivities(UUID clubId);
    ClubActivityResponseCommand createClubActivity(CreateClubActivityCommand command) throws ServiceException;
}
