package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.ReserveTournamentVenueCommand;
import iuh.fit.clubservice.application.dto.response.TournamentVenueReservationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;

import java.util.List;
import java.util.UUID;

/**
 * Xếp trận vào sân + khung giờ. Sân đã được giữ nguyên khối bên venue-service lúc công bố giải,
 * nên ở đây chỉ chia khung giờ giữa các trận của chính giải — không gọi sang venue-service.
 */
public interface TournamentScheduleService {

    TournamentVenueReservationResponseCommand reserveVenue(ReserveTournamentVenueCommand command) throws ServiceException;

    List<TournamentVenueReservationResponseCommand> getReservations(UUID tournamentId);

    void releaseReservation(UUID tournamentId, UUID reservationId, UUID actorUserId) throws ServiceException;
}
