package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.AdminStatsService;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceImplTest {
    @Mock private ClubRepository clubRepository;
    @Mock private TournamentRepository tournamentRepository;

    @Test
    void countsTournamentsByTheirDisplayedDateDerivedStatus() {
        LocalDate today = LocalDate.now();
        Tournament open = Tournament.builder().status(TournamentStatus.PUBLISHED)
                .registrationOpenDate(today.minusDays(2)).registrationCloseDate(today.plusDays(2)).build();
        Tournament upcoming = Tournament.builder().status(TournamentStatus.PUBLISHED)
                .registrationOpenDate(today.plusDays(3)).registrationCloseDate(today.plusDays(9)).build();
        Tournament draft = Tournament.builder().status(TournamentStatus.DRAFT).build();
        when(tournamentRepository.findAll()).thenReturn(List.of(open, upcoming, draft));
        when(clubRepository.countActive()).thenReturn(12L);
        when(clubRepository.countDisbanded()).thenReturn(2L);

        AdminStatsService.CommunityStats stats = new AdminStatsServiceImpl(clubRepository, tournamentRepository).getCommunityStats();

        assertThat(stats.tournaments()).isEqualTo(3);
        assertThat(stats.tournamentsByStatus())
                .containsEntry(TournamentStatus.REGISTRATION_OPEN, 1L)
                .containsEntry(TournamentStatus.PUBLISHED, 1L)
                .containsEntry(TournamentStatus.DRAFT, 1L)
                .containsEntry(TournamentStatus.COMPLETED, 0L);
        assertThat(stats.activeClubs()).isEqualTo(12);
    }
}
