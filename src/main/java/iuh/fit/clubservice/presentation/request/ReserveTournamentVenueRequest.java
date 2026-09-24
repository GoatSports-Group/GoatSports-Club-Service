package iuh.fit.clubservice.presentation.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveTournamentVenueRequest {
    private UUID venueId;
    @NotNull(message = "Sân không được để trống")
    private UUID courtId;
    private UUID fixtureId;
    @NotNull(message = "Ngày thi đấu không được để trống")
    private LocalDate playDate;
    @NotNull(message = "Giờ bắt đầu không được để trống")
    private LocalTime startTime;
    @NotNull(message = "Giờ kết thúc không được để trống")
    private LocalTime endTime;
}
