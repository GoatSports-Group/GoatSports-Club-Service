package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.TournamentFixture;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentFixtureRepository {
    TournamentFixture save(TournamentFixture fixture);
    List<TournamentFixture> saveAll(List<TournamentFixture> fixtures);
    Optional<TournamentFixture> findById(UUID fixtureId);
    List<TournamentFixture> findByTournamentId(UUID tournamentId);
    void deleteByTournamentId(UUID tournamentId);
}
