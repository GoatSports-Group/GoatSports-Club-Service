package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentFixtureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaTournamentFixtureRepository extends JpaRepository<TournamentFixtureEntity, UUID> {
    List<TournamentFixtureEntity> findByTournamentIdOrderByRoundNumberAscMatchNumberAsc(UUID tournamentId);

    List<TournamentFixtureEntity> findByRatingSyncedFalse(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update TournamentFixtureEntity fixture set fixture.ratingSynced = true "
            + "where fixture.fixtureId = :fixtureId and fixture.score1 = :score1 and fixture.score2 = :score2")
    int markRatingSynced(@org.springframework.data.repository.query.Param("fixtureId") UUID fixtureId,
                         @org.springframework.data.repository.query.Param("score1") Integer score1,
                         @org.springframework.data.repository.query.Param("score2") Integer score2);
    void deleteByTournamentId(UUID tournamentId);
}
