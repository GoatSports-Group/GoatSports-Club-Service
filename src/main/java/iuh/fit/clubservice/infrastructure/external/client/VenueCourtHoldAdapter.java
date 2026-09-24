package iuh.fit.clubservice.infrastructure.external.client;

import feign.FeignException;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.VenueCourtHold;
import iuh.fit.clubservice.domain.entity.Tournament;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VenueCourtHoldAdapter implements VenueCourtHold {
    private final VenueClient venueClient;

    @Override
    public void hold(Tournament tournament, boolean dryRun) throws ServiceException {
        try {
            venueClient.holdCourts(tournament.getTournamentId(), dryRun, new VenueClient.HoldPayload(
                    tournament.getOrganizerId(), tournament.getVenueId(), tournament.getSportType().name(),
                    tournament.getCourtIds(), tournament.getStartDate(), tournament.getEndDate(),
                    tournament.getDailyStartTime(), tournament.getDailyEndTime()));
        } catch (FeignException exception) {
            // 4xx la loi nghiep vu cua venue (san bi dat, khong phai co so cua ban...): dua nguyen thong diep len.
            if (exception.status() >= 400 && exception.status() < 500) {
                throw new ServiceException(RemoteErrors.message(exception, "Cơ sở không giữ được sân cho giải."));
            }
            log.warn("Không giữ được sân cho giải {}: {}", tournament.getTournamentId(), exception.getMessage());
            throw new ServiceException("Không kết nối được hệ thống sân, vui lòng thử lại sau.");
        } catch (RuntimeException exception) {
            log.warn("Không giữ được sân cho giải {}: {}", tournament.getTournamentId(), exception.getMessage());
            throw new ServiceException("Không kết nối được hệ thống sân, vui lòng thử lại sau.");
        }
    }

    @Override
    public void release(UUID tournamentId) {
        try {
            venueClient.releaseCourts(tournamentId);
        } catch (RuntimeException exception) {
            // Tra san loi chi de lai san bi khoa thua; huy giai van phai thanh cong. Goi lai la idempotent.
            log.warn("Không trả được sân của giải {}: {}", tournamentId, exception.getMessage());
        }
    }
}
