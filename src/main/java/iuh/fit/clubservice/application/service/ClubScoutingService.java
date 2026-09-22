package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.response.ScoutedPlayerResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;

import java.util.List;
import java.util.UUID;

public interface ClubScoutingService {
    List<ScoutedPlayerResponseCommand> findCandidates(UUID clubId, UUID actorUserId, Double radiusKm, Integer limit)
            throws ServiceException;
}
