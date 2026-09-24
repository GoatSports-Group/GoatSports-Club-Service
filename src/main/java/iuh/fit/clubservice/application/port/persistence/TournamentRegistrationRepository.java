package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;

import java.time.LocalDateTime;
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

    List<TournamentRegistration> findByStatusAndPaymentDeadlineBefore(RegistrationStatus status, LocalDateTime deadline);

    List<TournamentRegistration> findByStatus(RegistrationStatus status);

    List<UUID> findTournamentIdsByParticipant(UUID userId, List<RegistrationStatus> statuses);

    void deleteById(UUID registrationId);
}
