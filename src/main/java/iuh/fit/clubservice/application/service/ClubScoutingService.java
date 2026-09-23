package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.ScoutingQueryCommand;
import iuh.fit.clubservice.application.dto.response.ScoutedPlayerResponseCommand;
import iuh.fit.clubservice.application.dto.response.SentInvitationResponseCommand;
import iuh.fit.clubservice.application.dto.response.ShortlistEntryResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;

import java.util.List;
import java.util.UUID;

public interface ClubScoutingService {
    List<ScoutedPlayerResponseCommand> findCandidates(UUID clubId, UUID actorUserId, ScoutingQueryCommand query)
            throws ServiceException;

    List<ShortlistEntryResponseCommand> getShortlist(UUID clubId, UUID actorUserId) throws ServiceException;

    ShortlistEntryResponseCommand saveShortlistEntry(UUID clubId, UUID actorUserId, UUID playerId, String note)
            throws ServiceException;

    void removeShortlistEntry(UUID clubId, UUID actorUserId, UUID playerId) throws ServiceException;

    List<SentInvitationResponseCommand> getSentInvitations(UUID clubId, UUID actorUserId) throws ServiceException;
}
