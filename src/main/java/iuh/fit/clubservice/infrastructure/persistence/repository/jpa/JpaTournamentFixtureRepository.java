package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentFixtureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaTournamentFixtureRepository extends JpaRepository<TournamentFixtureEntity, UUID> {
    List<TournamentFixtureEntity> findByTournamentIdOrderByRoundNumberAscMatchNumberAsc(UUID tournamentId);
    void deleteByTournamentId(UUID tournamentId);
}
