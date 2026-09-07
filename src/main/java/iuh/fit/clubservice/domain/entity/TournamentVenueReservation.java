package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentVenueReservation extends BaseEntity {
    private UUID reservationId;
    private UUID tournamentId;
    private UUID venueId;
    private UUID courtId;
    private UUID bookingId;
    private LocalDate playDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private ReservationStatus status;
}
