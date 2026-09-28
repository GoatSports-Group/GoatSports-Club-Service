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
import java.time.LocalDateTime;
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

        boolean inProgress = tournament.getStatus() == TournamentStatus.IN_PROGRESS;
        if (inProgress && command.getFixtureId() == null) {
            throw new ServiceException("Giải đang diễn ra: chỉ xếp sân cho một trận cụ thể.");
        }
        TournamentFixture fixture = null;
        if (command.getFixtureId() != null) {
            fixture = fixtureRepository.findById(command.getFixtureId())
                    .filter(item -> item.getTournamentId().equals(command.getTournamentId()))
                    .orElseThrow(() -> new ServiceException("Không tìm thấy trận đấu của giải này."));
            if (fixture.getStatus() == MatchStatus.COMPLETED) throw new ServiceException("Trận này đã có kết quả.");
            if (inProgress && fixture.getReservationId() != null && reservationRepository.findById(fixture.getReservationId())
                    .filter(item -> item.getStatus() == ReservationStatus.CONFIRMED || item.getStatus() == ReservationStatus.PENDING)
                    .isPresent()) {
                throw new ServiceException("Trận này đã có lịch; giải đang diễn ra nên không đổi lịch được.");
            }
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
        // Lich cua giai da khep lai hoac cua tran da co ket qua la du lieu lich su, khong duoc bo.
        if (tournament.getStatus() == TournamentStatus.COMPLETED || tournament.getStatus() == TournamentStatus.CANCELLED) {
            throw new ServiceException("Giải đấu đã kết thúc, không thể bỏ lịch sân.");
        }
        // Giai dang dien ra: nguoi choi da nhan lich, bo giua chung se lam lech ca giai.
        if (tournament.getStatus() == TournamentStatus.IN_PROGRESS) {
            throw new ServiceException("Giải đang diễn ra, không thể bỏ lịch sân giữa chừng.");
        }
        boolean played = fixtureRepository.findByTournamentId(tournamentId).stream()
                .anyMatch(item -> reservationId.equals(item.getReservationId()) && item.getStatus() == MatchStatus.COMPLETED);
        if (played) throw new ServiceException("Trận này đã có kết quả, không thể bỏ lịch sân.");
        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.save(reservation);

        fixtureRepository.findByTournamentId(tournamentId).stream()
                .filter(item -> reservationId.equals(item.getReservationId()))
                .forEach(item -> {
                    item.setReservationId(null);
                    fixtureRepository.save(item);
                });
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public TournamentVenueReservationResponseCommand rescheduleReservation(UUID reservationId,
                                                                          ReserveTournamentVenueCommand command)
            throws ServiceException {
        command.validate();
        Tournament tournament = requireTournament(command.getTournamentId());
        TournamentGuards.assertOrganizer(tournament, command.getActorUserId());
        if (tournament.getStatus() == TournamentStatus.COMPLETED || tournament.getStatus() == TournamentStatus.CANCELLED) {
            throw new ServiceException("Giải đấu đã kết thúc hoặc đã hủy, không thể xếp lại sân.");
        }
        TournamentVenueReservation reservation = reservationRepository.findById(reservationId)
                .filter(item -> item.getTournamentId().equals(command.getTournamentId()))
                .filter(item -> item.getStatus() == ReservationStatus.CONFIRMED || item.getStatus() == ReservationStatus.PENDING)
                .orElseThrow(() -> new ServiceException("Không tìm thấy lịch sân đang hiệu lực của giải này."));
        List<TournamentFixture> matches = fixtureRepository.findByTournamentId(command.getTournamentId()).stream()
                .filter(item -> reservationId.equals(item.getReservationId())).toList();
        if (matches.stream().anyMatch(item -> item.getStatus() == MatchStatus.COMPLETED)) {
            throw new ServiceException("Trận này đã có kết quả, không thể xếp lại sân.");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean underway = !now.isBefore(LocalDateTime.of(reservation.getPlayDate(), reservation.getStartTime()))
                && now.isBefore(LocalDateTime.of(reservation.getPlayDate(), reservation.getEndTime()));
        if (underway || matches.stream().anyMatch(item -> item.getStatus() == MatchStatus.IN_PROGRESS)) {
            throw new ServiceException("Trận đang diễn ra, không thể xếp lại sân.");
        }
        assertWithinHeldCourts(tournament, command);
        // Khóa các lịch trùng giờ (trừ chính lịch này) rồi mới đổi, tránh hai trận cùng chiếm một khung giờ.
        boolean clash = reservationRepository.findActiveOverlaps(
                        command.getCourtId(), command.getPlayDate(), command.getStartTime(), command.getEndTime())
                .stream().anyMatch(other -> !other.getReservationId().equals(reservationId));
        if (clash) throw new ServiceException("Khung giờ này đã được xếp cho một trận khác.");

        reservation.setCourtId(command.getCourtId());
        reservation.setPlayDate(command.getPlayDate());
        reservation.setStartTime(command.getStartTime());
        reservation.setEndTime(command.getEndTime());
        return toResponse(reservationRepository.save(reservation));
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
