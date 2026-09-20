package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.application.exception.ServiceException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface VenueSlotReservation {
    void hold(UUID courtId, LocalDate playDate, LocalTime startTime, LocalTime endTime)
            throws ServiceException;

    void release(UUID courtId, LocalDate playDate, LocalTime startTime, LocalTime endTime);
}
