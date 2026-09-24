package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.entity.Tournament;

import java.util.UUID;

/**
 * Giu san cua giai ben venue-service: chan nguoi choi dat san trong khung gio thi dau suot thoi gian giai.
 * venue-service la noi kiem tra co so thuoc chu san, san cung mon va khong trung luot dat hien co.
 */
public interface VenueCourtHold {
    void hold(Tournament tournament, boolean dryRun) throws ServiceException;

    void release(UUID tournamentId);
}
