package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaTournamentRepository extends JpaRepository<TournamentEntity, UUID>, JpaSpecificationExecutor<TournamentEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TournamentEntity t where t.tournamentId = :tournamentId")
    Optional<TournamentEntity> findByIdForUpdate(@Param("tournamentId") UUID tournamentId);
}
