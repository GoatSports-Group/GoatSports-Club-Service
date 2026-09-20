package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.domain.enums.ReservationStatus;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentVenueReservationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface JpaTournamentVenueReservationRepository
        extends JpaRepository<TournamentVenueReservationEntity, UUID> {

    List<TournamentVenueReservationEntity> findByTournamentIdOrderByPlayDateAscStartTimeAsc(UUID tournamentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from TournamentVenueReservationEntity r
            where r.courtId = :courtId and r.playDate = :playDate
              and r.status in :activeStatuses
              and r.startTime < :endTime and r.endTime > :startTime
            """)
    List<TournamentVenueReservationEntity> findActiveOverlapsForUpdate(
            @Param("courtId") UUID courtId,
            @Param("playDate") LocalDate playDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("activeStatuses") List<ReservationStatus> activeStatuses);
}
