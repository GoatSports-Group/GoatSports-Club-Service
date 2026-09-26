package iuh.fit.clubservice.application.service.impl;

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
                .status(TournamentStatus.IN_PROGRESS).build();
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
    void releasesScheduleOfUnplayedMatch() throws Exception {
        TournamentFixture fixture = TournamentFixture.builder()
                .tournamentId(tournamentId).reservationId(reservationId).status(MatchStatus.SCHEDULED).build();
        lenient().when(fixtureRepository.findByTournamentId(tournamentId)).thenReturn(List.of(fixture));

        service.releaseReservation(tournamentId, reservationId, organizerId);

        verify(reservationRepository).save(any());
        assertThat(fixture.getReservationId()).isNull();
    }
}
