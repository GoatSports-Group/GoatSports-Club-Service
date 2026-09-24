package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.infrastructure.config.VenueClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "GOATSPORTS-VENUE-SERVICE",
        url = "${venue.service.url}",
        path = "/internal/tournament-court-holds",
        configuration = VenueClientConfig.class
)
public interface VenueClient {

    @PutMapping("/{tournamentId}")
    void holdCourts(@PathVariable("tournamentId") UUID tournamentId, @RequestParam("dryRun") boolean dryRun,
                    @RequestBody HoldPayload payload);

    @DeleteMapping("/{tournamentId}")
    void releaseCourts(@PathVariable("tournamentId") UUID tournamentId);

    record HoldPayload(UUID ownerId, UUID venueId, String sportType, List<UUID> courtIds,
                       LocalDate fromDate, LocalDate toDate, LocalTime dailyStart, LocalTime dailyEnd) { }
}
