package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubFriendlyMatch;

import java.util.List;
import java.util.UUID;

public interface ClubFriendlyMatchRepository {
    List<ClubFriendlyMatch> saveAll(List<ClubFriendlyMatch> matches);

    List<ClubFriendlyMatch> findBySessionId(UUID sessionId);

    List<ClubFriendlyMatch> findByClubId(UUID clubId);
}
