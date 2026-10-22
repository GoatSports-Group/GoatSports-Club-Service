package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.infrastructure.persistence.mapper.TournamentRegistrationEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaTournamentRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TournamentRegistrationRepositoryImpl implements TournamentRegistrationRepository {
    private final JpaTournamentRegistrationRepository jpaTournamentRegistrationRepository;
    private final TournamentRegistrationEntityMapper tournamentRegistrationEntityMapper;

    @Override
    public Map<UUID, Long> countByTournamentIdsAndStatuses(Collection<UUID> tournamentIds,
                                                          Collection<RegistrationStatus> statuses) {
        if (tournamentIds.isEmpty()) return Map.of();
        return jpaTournamentRegistrationRepository.countGroupedByTournament(tournamentIds, statuses).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    @Override
    public TournamentRegistration save(TournamentRegistration registration) {
        return tournamentRegistrationEntityMapper.toDomain(jpaTournamentRegistrationRepository.save(tournamentRegistrationEntityMapper.toEntity(registration)));
    }

    @Override
    public Optional<TournamentRegistration> findById(UUID registrationId) {
        return jpaTournamentRegistrationRepository.findById(registrationId).map(tournamentRegistrationEntityMapper::toDomain);
    }

    @Override
    public List<TournamentRegistration> findByTournamentId(UUID tournamentId) {
        return jpaTournamentRegistrationRepository.findByTournamentId(tournamentId).stream()
                .map(tournamentRegistrationEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<TournamentRegistration> findByTournamentIdAndStatus(UUID tournamentId, RegistrationStatus status) {
        return jpaTournamentRegistrationRepository.findByTournamentIdAndStatus(tournamentId, status).stream()
                .map(tournamentRegistrationEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<TournamentRegistration> findByClubId(UUID clubId) {
        return jpaTournamentRegistrationRepository.findByClubId(clubId).stream()
                .map(tournamentRegistrationEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TournamentRegistration> findByTournamentIdAndPlayerId(UUID tournamentId, UUID playerId) {
        return jpaTournamentRegistrationRepository.findByTournamentIdAndPlayerId(tournamentId, playerId).map(tournamentRegistrationEntityMapper::toDomain);
    }

    @Override
    public long countByTournamentIdAndStatus(UUID tournamentId, RegistrationStatus status) {
        return jpaTournamentRegistrationRepository.countByTournamentIdAndStatus(tournamentId, status);
    }

    @Override
    public long countDistinctTournamentIdByClubId(UUID clubId) {
        return jpaTournamentRegistrationRepository.countDistinctTournamentIdByClubId(clubId);
    }

    @Override
    public List<TournamentRegistration> findByStatusAndPaymentDeadlineBefore(RegistrationStatus status,
                                                                           LocalDateTime deadline) {
        return jpaTournamentRegistrationRepository.findByStatusAndPaymentDeadlineBefore(status, deadline).stream()
                .map(tournamentRegistrationEntityMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<TournamentRegistration> findByStatus(RegistrationStatus status) {
        return jpaTournamentRegistrationRepository.findByStatus(status).stream()
                .map(tournamentRegistrationEntityMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<UUID> findTournamentIdsByParticipant(UUID userId, List<RegistrationStatus> statuses) {
        return jpaTournamentRegistrationRepository.findTournamentIdsByParticipant(userId, statuses);
    }

    @Override
    public void deleteById(UUID registrationId) {
        jpaTournamentRegistrationRepository.deleteById(registrationId);
    }
}
