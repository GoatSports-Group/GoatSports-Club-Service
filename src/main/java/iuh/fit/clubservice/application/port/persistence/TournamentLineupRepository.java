package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.TournamentLineup;

import java.util.List;
import java.util.UUID;

public interface TournamentLineupRepository {
    TournamentLineup save(TournamentLineup lineup);
    List<TournamentLineup> saveAll(List<TournamentLineup> lineups);
    List<TournamentLineup> findByRegistrationId(UUID registrationId);
    void deleteByRegistrationId(UUID registrationId);
}
