package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.VenueSlotReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VenueSlotReservationAdapter implements VenueSlotReservation {
    private final VenueClient venueClient;

    @Override
    public void hold(UUID courtId, LocalDate playDate, LocalTime startTime, LocalTime endTime)
            throws ServiceException {
        try {
            venueClient.holdSlot(new VenueClient.SlotPayload(courtId, playDate, startTime, endTime));
        } catch (RuntimeException exception) {
            // Khong nuot loi o day: giu cho khong thanh cong ma van tao reservation
            // se cho hai ben cung tin la minh co san.
            log.warn("Không giữ được khung giờ {} {}-{} của sân {}: {}",
                    playDate, startTime, endTime, courtId, exception.getMessage());
            throw new ServiceException("Không giữ được khung giờ này tại cơ sở, vui lòng chọn giờ khác.");
        }
    }

    @Override
    public void release(UUID courtId, LocalDate playDate, LocalTime startTime, LocalTime endTime) {
        try {
            venueClient.releaseSlot(new VenueClient.SlotPayload(courtId, playDate, startTime, endTime));
        } catch (RuntimeException exception) {
            // Tra san that bai chi de lai mot khung gio bi giu thua, khong lam hong viec huy dat cho.
            log.warn("Không trả được khung giờ {} {}-{} của sân {}: {}",
                    playDate, startTime, endTime, courtId, exception.getMessage());
        }
    }
}
