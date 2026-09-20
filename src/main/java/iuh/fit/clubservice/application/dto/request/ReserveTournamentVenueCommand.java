package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.application.exception.ServiceException;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveTournamentVenueCommand {
    private UUID tournamentId;
    private UUID actorUserId;
    private UUID venueId;
    private UUID courtId;
    private UUID fixtureId;
    private LocalDate playDate;
    private LocalTime startTime;
    private LocalTime endTime;

    public void validate() throws ServiceException {
        if (venueId == null || courtId == null) throw new ServiceException("Thiếu thông tin cơ sở hoặc sân.");
        if (playDate == null || startTime == null || endTime == null) {
            throw new ServiceException("Thiếu ngày hoặc khung giờ đặt sân.");
        }
        if (!endTime.isAfter(startTime)) throw new ServiceException("Giờ kết thúc phải sau giờ bắt đầu.");
    }
}
