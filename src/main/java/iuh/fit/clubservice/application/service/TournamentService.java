package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.CreateTournamentCommand;
import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand;
import iuh.fit.clubservice.application.dto.request.ReserveTournamentVenueCommand;
import iuh.fit.clubservice.application.dto.request.UpdateFixtureResultCommand;
import iuh.fit.clubservice.application.dto.response.TournamentFixtureResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentRegistrationResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentStandingResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentVenueReservationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.enums.SportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TournamentService {
    Page<TournamentResponseCommand> searchTournaments(SportType sportType, TournamentStatus status, String keyword, Pageable pageable);

    TournamentResponseCommand getTournamentDetails(UUID tournamentId) throws ServiceException;

    TournamentResponseCommand createTournament(CreateTournamentCommand command) throws ServiceException;

    TournamentRegistrationResponseCommand registerTeam(RegisterTournamentTeamCommand command) throws ServiceException;

    void cancelRegistration(UUID tournamentId, UUID registrationId, UUID actorUserId) throws ServiceException;

    List<TournamentRegistrationResponseCommand> getTournamentTeams(UUID tournamentId);

    List<TournamentFixtureResponseCommand> generateFixtures(UUID tournamentId, UUID actorUserId) throws ServiceException;

    List<TournamentFixtureResponseCommand> getFixtures(UUID tournamentId);

    TournamentFixtureResponseCommand updateFixtureResult(UpdateFixtureResultCommand command) throws ServiceException;

    List<TournamentStandingResponseCommand> getStandings(UUID tournamentId);

    List<TournamentEligibilityRule> getEligibilityRules(UUID tournamentId);

    TournamentResponseCommand changeStatus(UUID tournamentId, TournamentStatus target, UUID actorUserId)
            throws ServiceException;

    TournamentVenueReservationResponseCommand reserveVenue(ReserveTournamentVenueCommand command)
            throws ServiceException;

    List<TournamentVenueReservationResponseCommand> getReservations(UUID tournamentId);

    void releaseReservation(UUID tournamentId, UUID reservationId, UUID actorUserId) throws ServiceException;
}
