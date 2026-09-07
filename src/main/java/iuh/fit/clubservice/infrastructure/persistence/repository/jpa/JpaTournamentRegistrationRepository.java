package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaTournamentRegistrationRepository extends JpaRepository<TournamentRegistrationEntity, UUID> {
    List<TournamentRegistrationEntity> findByTournamentId(UUID tournamentId);
    List<TournamentRegistrationEntity> findByTournamentIdAndStatus(UUID tournamentId, RegistrationStatus status);
    Optional<TournamentRegistrationEntity> findByTournamentIdAndPlayerId(UUID tournamentId, UUID playerId);
    long countByTournamentIdAndStatus(UUID tournamentId, RegistrationStatus status);
}
