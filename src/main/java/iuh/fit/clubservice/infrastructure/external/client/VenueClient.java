package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.infrastructure.config.VenueClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@FeignClient(
        name = "GOATSPORTS-VENUE-SERVICE",
        url = "${venue.service.url}",
        path = "/internal/tournament-slots",
        configuration = VenueClientConfig.class
)
public interface VenueClient {

    @PostMapping
    void holdSlot(@RequestBody SlotPayload payload);

    @DeleteMapping
    void releaseSlot(@RequestBody SlotPayload payload);

    record SlotPayload(UUID venueCourtId, LocalDate playDate, LocalTime startTime, LocalTime endTime) { }
}
