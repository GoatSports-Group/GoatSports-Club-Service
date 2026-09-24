package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.ReserveTournamentVenueCommand;
import iuh.fit.clubservice.application.dto.response.TournamentVenueReservationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentVenueReservationRepository;
import iuh.fit.clubservice.application.service.TournamentScheduleService;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.entity.TournamentVenueReservation;
import iuh.fit.clubservice.domain.enums.MatchStatus;
import iuh.fit.clubservice.domain.enums.ReservationStatus;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TournamentScheduleServiceImpl implements TournamentScheduleService {
    private final TournamentRepository tournamentRepository;
    private final TournamentFixtureRepository fixtureRepository;
    private final TournamentVenueReservationRepository reservationRepository;

    /**
     * Xếp một trận vào một sân + khung giờ. Sân nằm trong danh sách sân giải đã giữ bên venue-service,
     * nên ở đây chỉ cần kiểm tra phạm vi (sân/ngày/giờ của giải) và chống trùng giữa các trận của chính giải.
     */
    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentVenueReservationResponseCommand reserveVenue(ReserveTournamentVenueCommand command)
            throws ServiceException {
        command.validate();
        Tournament tournament = requireTournament(command.getTournamentId());
        TournamentGuards.assertOrganizer(tournament, command.getActorUserId());
        if (tournament.getStatus() == TournamentStatus.COMPLETED || tournament.getStatus() == TournamentStatus.CANCELLED) {
            throw new ServiceException("Giải đấu đã kết thúc hoặc đã hủy.");
        }
        assertWithinHeldCourts(tournament, command);

        // Khóa các lịch cùng sân cùng ngày trước khi kiểm tra, tránh hai trận cùng chiếm một khung giờ.
        List<TournamentVenueReservation> overlaps = reservationRepository.findActiveOverlaps(
                command.getCourtId(), command.getPlayDate(), command.getStartTime(), command.getEndTime());
        if (!overlaps.isEmpty()) {
            throw new ServiceException("Khung giờ này đã được xếp cho một trận khác.");
        }

        TournamentFixture fixture = null;
        if (command.getFixtureId() != null) {
            fixture = fixtureRepository.findById(command.getFixtureId())
                    .filter(item -> item.getTournamentId().equals(command.getTournamentId()))
                    .orElseThrow(() -> new ServiceException("Không tìm thấy trận đấu của giải này."));
            if (fixture.getStatus() == MatchStatus.COMPLETED) throw new ServiceException("Trận này đã có kết quả.");
        }

        TournamentVenueReservation reservation = reservationRepository.save(TournamentVenueReservation.builder()
                .tournamentId(command.getTournamentId())
                .venueId(tournament.getVenueId())
                .courtId(command.getCourtId())
                .playDate(command.getPlayDate())
                .startTime(command.getStartTime())
                .endTime(command.getEndTime())
                .status(ReservationStatus.CONFIRMED)
                .build());

        if (fixture != null) {
            // Trận đã có lịch cũ thì trả lịch cũ, một trận chỉ giữ một khung giờ.
            if (fixture.getReservationId() != null) release(fixture.getReservationId());
            fixture.setReservationId(reservation.getReservationId());
            fixtureRepository.save(fixture);
        }
        return toResponse(reservation);
    }

    @Override
    public List<TournamentVenueReservationResponseCommand> getReservations(UUID tournamentId) {
        return reservationRepository.findByTournamentId(tournamentId).stream()
                .map(TournamentScheduleServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public void releaseReservation(UUID tournamentId, UUID reservationId, UUID actorUserId) throws ServiceException {
        Tournament tournament = requireTournament(tournamentId);
        TournamentGuards.assertOrganizer(tournament, actorUserId);
        TournamentVenueReservation reservation = reservationRepository.findById(reservationId)
                .filter(item -> item.getTournamentId().equals(tournamentId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy lịch sân của giải này."));
        if (reservation.getStatus() == ReservationStatus.RELEASED
                || reservation.getStatus() == ReservationStatus.CANCELLED) return;
        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.save(reservation);

        fixtureRepository.findByTournamentId(tournamentId).stream()
                .filter(item -> reservationId.equals(item.getReservationId()))
                .forEach(item -> {
                    item.setReservationId(null);
                    fixtureRepository.save(item);
                });
    }

    /** Sân, ngày và khung giờ phải nằm trong phần giải đã giữ; khung giờ không ngắn hơn một trận. */
    private void assertWithinHeldCourts(Tournament tournament, ReserveTournamentVenueCommand command)
            throws ServiceException {
        if (tournament.getVenueId() == null || tournament.getCourtIds() == null) {
            throw new ServiceException("Giải chưa gắn cơ sở và sân thi đấu.");
        }
        if (!tournament.getCourtIds().contains(command.getCourtId())) {
            throw new ServiceException("Sân này không nằm trong danh sách sân của giải.");
        }
        if (command.getPlayDate().isBefore(tournament.getStartDate()) || command.getPlayDate().isAfter(tournament.getEndDate())) {
            throw new ServiceException("Ngày thi đấu phải nằm trong thời gian diễn ra giải.");
        }
        if (command.getStartTime().isBefore(tournament.getDailyStartTime())
                || command.getEndTime().isAfter(tournament.getDailyEndTime())) {
            throw new ServiceException("Giờ thi đấu phải nằm trong khung " + tournament.getDailyStartTime()
                    + "–" + tournament.getDailyEndTime() + " mà giải đã giữ sân.");
        }
        if (Duration.between(command.getStartTime(), command.getEndTime()).toMinutes()
                < (tournament.getMatchDurationMinutes() == null ? 0 : tournament.getMatchDurationMinutes())) {
            throw new ServiceException("Khung giờ ngắn hơn thời lượng một trận (" + tournament.getMatchDurationMinutes() + " phút).");
        }
    }

    private void release(UUID reservationId) {
        reservationRepository.findById(reservationId).ifPresent(old -> {
            old.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(old);
        });
    }

    private Tournament requireTournament(UUID id) throws ServiceException {
        return tournamentRepository.findById(id).orElseThrow(() -> new ServiceException("Không tìm thấy giải đấu."));
    }

    private static TournamentVenueReservationResponseCommand toResponse(TournamentVenueReservation reservation) {
        return TournamentVenueReservationResponseCommand.builder()
                .reservationId(reservation.getReservationId())
                .tournamentId(reservation.getTournamentId())
                .venueId(reservation.getVenueId())
                .courtId(reservation.getCourtId())
                .bookingId(reservation.getBookingId())
                .playDate(reservation.getPlayDate())
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .status(reservation.getStatus())
                .build();
    }
}
