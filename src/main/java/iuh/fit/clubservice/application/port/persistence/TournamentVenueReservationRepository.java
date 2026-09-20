package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.TournamentVenueReservation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentVenueReservationRepository {
    TournamentVenueReservation save(TournamentVenueReservation reservation);

    Optional<TournamentVenueReservation> findById(UUID reservationId);

    List<TournamentVenueReservation> findByTournamentId(UUID tournamentId);

    List<TournamentVenueReservation> findActiveOverlaps(UUID courtId, LocalDate playDate,
                                                        LocalTime startTime, LocalTime endTime);
}
