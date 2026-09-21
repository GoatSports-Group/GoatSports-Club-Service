package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentRegistrationRepository {
    TournamentRegistration save(TournamentRegistration registration);

    Optional<TournamentRegistration> findById(UUID registrationId);

    List<TournamentRegistration> findByTournamentId(UUID tournamentId);

    List<TournamentRegistration> findByTournamentIdAndStatus(UUID tournamentId, RegistrationStatus status);

    List<TournamentRegistration> findByClubId(UUID clubId);

    Optional<TournamentRegistration> findByTournamentIdAndPlayerId(UUID tournamentId, UUID playerId);

    long countByTournamentIdAndStatus(UUID tournamentId, RegistrationStatus status);

    long countDistinctTournamentIdByClubId(UUID clubId);

    void deleteById(UUID registrationId);
}
