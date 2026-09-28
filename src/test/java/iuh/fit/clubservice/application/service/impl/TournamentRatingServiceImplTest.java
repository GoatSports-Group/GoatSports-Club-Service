package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.external.SportRatingPort;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.application.port.persistence.TournamentFixtureRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentLineupRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.LineupMemberStatus;
import iuh.fit.clubservice.domain.enums.MatchStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TournamentRatingServiceImplTest {
    @Mock private TournamentFixtureRepository fixtureRepository;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentLineupRepository lineupRepository;
    @Mock private PlayerProfileProvider playerProfileProvider;
    @Mock private SportRatingPort sportRatingPort;
    @Mock private TournamentEventPublisher eventPublisher;
    @InjectMocks private TournamentRatingServiceImpl service;

    private final UUID tournamentId = UUID.randomUUID();
    private final UUID fixtureId = UUID.randomUUID();
    private final UUID soloReg = UUID.randomUUID(), teamReg = UUID.randomUUID();
    private final UUID solo = UUID.randomUUID(), captain = UUID.randomUUID(), mate = UUID.randomUUID(), declined = UUID.randomUUID();
    private Tournament tournament;
    private TournamentFixture fixture;

    @BeforeEach
    void setUp() {
        tournament = Tournament.builder().tournamentId(tournamentId).name("Giải cầu lông mở rộng")
                .sportType(SportType.BADMINTON).status(TournamentStatus.IN_PROGRESS).build();
        fixture = TournamentFixture.builder().fixtureId(fixtureId).tournamentId(tournamentId).roundName("Bán kết")
                .matchNumber(1).registration1Id(soloReg).registration2Id(teamReg).score1(21).score2(15)
                .status(MatchStatus.COMPLETED).ratingSynced(false).build();
        when(fixtureRepository.findById(fixtureId)).thenReturn(Optional.of(fixture));
        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findById(soloReg)).thenReturn(Optional.of(TournamentRegistration.builder()
                .registrationId(soloReg).type(RegistrationType.INDIVIDUAL).playerId(solo).build()));
        when(playerProfileProvider.findFullName(solo)).thenReturn(Optional.of("Nguyễn Minh"));
        when(registrationRepository.findById(teamReg)).thenReturn(Optional.of(TournamentRegistration.builder()
                .registrationId(teamReg).type(RegistrationType.TEAM).teamName("CLB Phú Nhuận").clubId(UUID.randomUUID()).build()));
        when(lineupRepository.findByRegistrationId(teamReg)).thenReturn(List.of(
                lineup(captain, LineupMemberStatus.ACCEPTED), lineup(mate, LineupMemberStatus.ACCEPTED),
                lineup(declined, LineupMemberStatus.DECLINED)));
        when(fixtureRepository.markRatingSynced(fixtureId, 21, 15)).thenReturn(true);
    }

    @Test
    void ratesEveryAcceptedPlayerAndNotifiesEachOneWithTheirOwnPointChange() throws Exception {
        when(sportRatingPort.rateTournamentMatch(any())).thenReturn(List.of(
                new SportRatingPort.RatedPlayer(solo, 1000, 1024, "WIN", true),
                new SportRatingPort.RatedPlayer(captain, 1200, 1182, "LOSS", true),
                new SportRatingPort.RatedPlayer(mate, 1100, 1084, "LOSS", true)));

        assertThat(service.syncFixture(fixtureId)).isTrue();

        ArgumentCaptor<SportRatingPort.TournamentMatch> match = ArgumentCaptor.forClass(SportRatingPort.TournamentMatch.class);
        verify(sportRatingPort).rateTournamentMatch(match.capture());
        assertThat(match.getValue().first().playerIds()).containsExactly(solo);
        assertThat(match.getValue().first().name()).isEqualTo("Nguyễn Minh");
        // Người đã từ chối lời mời không thi đấu nên không bị tính ELO.
        assertThat(match.getValue().second().playerIds()).containsExactly(captain, mate);
        assertThat(match.getValue().title()).isEqualTo("Bán kết · Trận 1 · Giải cầu lông mở rộng");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publish(eq(TournamentEventPublisher.MATCH_RATED), eq(tournament), eq(captain), any(), payload.capture());
        assertThat(payload.getValue()).containsEntry("delta", "-18").containsEntry("score", "15–21")
                .containsEntry("opponentName", "Nguyễn Minh").containsEntry("newElo", "1182");
        verify(eventPublisher, times(3)).publish(eq(TournamentEventPublisher.MATCH_RATED), any(), any(), any(), anyMap());
    }

    @Test
    void retryThatChangesNothingSendsNoSecondNotification() throws Exception {
        when(sportRatingPort.rateTournamentMatch(any())).thenReturn(List.of(
                new SportRatingPort.RatedPlayer(solo, 1000, 1024, "WIN", false)));

        assertThat(service.syncFixture(fixtureId)).isTrue();

        verify(eventPublisher, never()).publish(anyString(), any(), any(), any(), anyMap());
    }

    @Test
    void keepsTheFixturePendingWhenTheRatingServiceIsDown() throws Exception {
        when(sportRatingPort.rateTournamentMatch(any())).thenThrow(new ServiceException("down"));

        assertThat(service.syncFixture(fixtureId)).isFalse();

        verify(fixtureRepository, never()).markRatingSynced(any(), any(), any());
    }

    @Test
    void scoreEditedWhileRatingStaysPendingAndSendsNothing() throws Exception {
        when(sportRatingPort.rateTournamentMatch(any())).thenReturn(List.of(
                new SportRatingPort.RatedPlayer(solo, 1000, 1024, "WIN", true)));
        when(fixtureRepository.markRatingSynced(fixtureId, 21, 15)).thenReturn(false);

        assertThat(service.syncFixture(fixtureId)).isFalse();

        verify(eventPublisher, never()).publish(anyString(), any(), any(), any(), anyMap());
    }

    @Test
    void cancelledTournamentIsNotRated() throws Exception {
        tournament.setStatus(TournamentStatus.CANCELLED);

        assertThat(service.syncFixture(fixtureId)).isTrue();

        verify(sportRatingPort, never()).rateTournamentMatch(any());
        verify(fixtureRepository).markRatingSynced(fixtureId, 21, 15);
    }

    private TournamentLineup lineup(UUID playerId, LineupMemberStatus status) {
        return TournamentLineup.builder().registrationId(teamReg).playerId(playerId).memberStatus(status).build();
    }
}
