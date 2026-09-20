package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.ReservationStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentVenueReservationResponseCommand {
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
