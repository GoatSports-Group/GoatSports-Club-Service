package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.ReserveTournamentVenueCommand;
import iuh.fit.clubservice.application.dto.response.TournamentVenueReservationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentVenueReservationRepository;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.entity.TournamentVenueReservation;
import iuh.fit.clubservice.domain.enums.MatchStatus;
import iuh.fit.clubservice.domain.enums.ReservationStatus;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentScheduleServiceImplTest {
    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentFixtureRepository fixtureRepository;
    @Mock private TournamentVenueReservationRepository reservationRepository;
    @InjectMocks private TournamentScheduleServiceImpl service;

    private final UUID organizerId = UUID.randomUUID();
    private final UUID tournamentId = UUID.randomUUID();
    private final UUID reservationId = UUID.randomUUID();
    private Tournament tournament;

    @BeforeEach
    void setUp() {
        tournament = Tournament.builder().tournamentId(tournamentId).organizerId(organizerId)
                .status(TournamentStatus.PUBLISHED).build();
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(TournamentVenueReservation.builder()
                .reservationId(reservationId).tournamentId(tournamentId).status(ReservationStatus.CONFIRMED).build()));
    }

    @Test
    void refusesToReleaseScheduleOfCompletedTournament() {
        tournament.setStatus(TournamentStatus.COMPLETED);

        assertThatThrownBy(() -> service.releaseReservation(tournamentId, reservationId, organizerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã kết thúc");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void refusesToReleaseScheduleOfPlayedMatch() {
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(TournamentFixture.builder()
                .tournamentId(tournamentId).reservationId(reservationId).status(MatchStatus.COMPLETED).build()));

        assertThatThrownBy(() -> service.releaseReservation(tournamentId, reservationId, organizerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã có kết quả");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void refusesToReleaseScheduleWhileTournamentIsInProgress() {
        tournament.setStatus(TournamentStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.releaseReservation(tournamentId, reservationId, organizerId))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đang diễn ra");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void releasesScheduleOfUnplayedMatch() throws Exception {
        TournamentFixture fixture = TournamentFixture.builder()
                .tournamentId(tournamentId).reservationId(reservationId).status(MatchStatus.SCHEDULED).build();
        lenient().when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(fixture));

        service.releaseReservation(tournamentId, reservationId, organizerId);

        verify(reservationRepository).save(any());
        assertThat(fixture.getReservationId()).isNull();
    }

    // ---- xep lai san ----

    private final UUID courtA = UUID.randomUUID();
    private final UUID courtB = UUID.randomUUID();

    /** Giai 18:00-22:00, hai san, dien ra tu hom nay toi ngay mai; lich hien tai la ngay mai 18:00-19:00 san A. */
    private TournamentVenueReservation heldTomorrow() {
        LocalDate today = LocalDate.now();
        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        tournament.setVenueId(UUID.randomUUID());
        tournament.setCourtIds(List.of(courtA, courtB));
        tournament.setStartDate(today);
        tournament.setEndDate(today.plusDays(1));
        tournament.setDailyStartTime(LocalTime.of(0, 0));
        tournament.setDailyEndTime(LocalTime.of(23, 59));
        tournament.setMatchDurationMinutes(60);
        TournamentVenueReservation reservation = TournamentVenueReservation.builder()
                .reservationId(reservationId).tournamentId(tournamentId).courtId(courtA)
                .playDate(today.plusDays(1)).startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 0))
                .status(ReservationStatus.CONFIRMED).build();
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        return reservation;
    }

    private ReserveTournamentVenueCommand moveTo(
            UUID courtId, LocalDate date, int startHour) {
        return ReserveTournamentVenueCommand.builder()
                .tournamentId(tournamentId).actorUserId(organizerId).courtId(courtId).playDate(date)
                .startTime(LocalTime.of(startHour, 0)).endTime(LocalTime.of(startHour + 1, 0)).build();
    }

    @Test
    void reschedulesMatchInPlaceWhileTournamentIsInProgress() throws Exception {
        TournamentVenueReservation reservation = heldTomorrow();
        TournamentFixture fixture = TournamentFixture.builder()
                .tournamentId(tournamentId).reservationId(reservationId).status(MatchStatus.SCHEDULED).build();
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(fixture));
        LocalDate day = reservation.getPlayDate();
        // Chinh lich nay nam trong ket qua trung gio (doi 1 tieng tren cung san): khong tinh la trung.
        when(reservationRepository.findActiveOverlaps(courtB, day, LocalTime.of(20, 0), LocalTime.of(21, 0)))
                .thenReturn(List.of(reservation));
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        TournamentVenueReservationResponseCommand result = service.rescheduleReservation(reservationId, moveTo(courtB, day, 20));

        assertThat(result.getReservationId()).isEqualTo(reservationId);
        assertThat(reservation.getCourtId()).isEqualTo(courtB);
        assertThat(reservation.getStartTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(fixture.getReservationId()).isEqualTo(reservationId);
    }

    @Test
    void refusesToRescheduleOntoAnotherMatch() {
        TournamentVenueReservation reservation = heldTomorrow();
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of());
        LocalDate day = reservation.getPlayDate();
        when(reservationRepository.findActiveOverlaps(courtB, day, LocalTime.of(20, 0), LocalTime.of(21, 0)))
                .thenReturn(List.of(TournamentVenueReservation.builder().reservationId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.rescheduleReservation(reservationId, moveTo(courtB, day, 20)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("trận khác");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void refusesToRescheduleMatchThatIsUnderway() {
        TournamentVenueReservation reservation = heldTomorrow();
        LocalDateTime now = LocalDateTime.now();
        reservation.setPlayDate(now.toLocalDate());
        reservation.setStartTime(LocalTime.MIN); // ca ngay hom nay: luon "dang dien ra", ke ca quanh nua dem
        reservation.setEndTime(LocalTime.MAX);
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.rescheduleReservation(reservationId, moveTo(courtB, now.toLocalDate().plusDays(1), 20)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đang diễn ra");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void refusesToRescheduleAfterTournamentEndedOrMatchPlayed() {
        TournamentVenueReservation reservation = heldTomorrow();
        when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(TournamentFixture.builder()
                .tournamentId(tournamentId).reservationId(reservationId).status(MatchStatus.COMPLETED).build()));
        assertThatThrownBy(() -> service.rescheduleReservation(reservationId, moveTo(courtB, reservation.getPlayDate(), 20)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã có kết quả");

        tournament.setStatus(TournamentStatus.COMPLETED);
        assertThatThrownBy(() -> service.rescheduleReservation(reservationId, moveTo(courtB, reservation.getPlayDate(), 20)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("đã kết thúc");
        verify(reservationRepository, never()).save(any());
    }
}
