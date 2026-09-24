package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaTournamentRegistrationRepository extends JpaRepository<TournamentRegistrationEntity, UUID> {
    List<TournamentRegistrationEntity> findByTournamentId(UUID tournamentId);

    List<TournamentRegistrationEntity> findByTournamentIdAndStatus(UUID tournamentId, RegistrationStatus status);

    List<TournamentRegistrationEntity> findByClubId(UUID clubId);

    Optional<TournamentRegistrationEntity> findByTournamentIdAndPlayerId(UUID tournamentId, UUID playerId);

    long countByTournamentIdAndStatus(UUID tournamentId, RegistrationStatus status);

    @Query("select count(distinct registration.tournamentId) from TournamentRegistrationEntity registration "
            + "where registration.clubId = :clubId")
    long countDistinctTournamentIdByClubId(@Param("clubId") UUID clubId);

    List<TournamentRegistrationEntity> findByStatusAndPaymentDeadlineBefore(
            RegistrationStatus status, LocalDateTime deadline
    );

    List<TournamentRegistrationEntity> findByStatus(RegistrationStatus status);

    @Query("""
            select distinct registration.tournamentId from TournamentRegistrationEntity registration
            where registration.status in :statuses
              and (registration.registeredBy = :userId or registration.playerId = :userId
                   or registration.registrationId in (select lineup.registrationId from TournamentLineupEntity lineup
                                                      where lineup.playerId = :userId
                                                        and lineup.memberStatus = iuh.fit.clubservice.domain.enums.LineupMemberStatus.ACCEPTED))
            """)
    List<UUID> findTournamentIdsByParticipant(@Param("userId") UUID userId,
                                              @Param("statuses") List<RegistrationStatus> statuses);
}
