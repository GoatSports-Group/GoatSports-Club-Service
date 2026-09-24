package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;

import java.util.List;
import java.util.UUID;

public interface TournamentLineupRepository {
    TournamentLineup save(TournamentLineup lineup);

    List<TournamentLineup> saveAll(List<TournamentLineup> lineups);

    List<TournamentLineup> findByRegistrationId(UUID registrationId);

    void deleteByRegistrationId(UUID registrationId);

    List<TournamentLineup> findByPlayerIdAndMemberStatus(UUID playerId, LineupMemberStatus memberStatus);

    void deleteById(UUID lineupId);
}
