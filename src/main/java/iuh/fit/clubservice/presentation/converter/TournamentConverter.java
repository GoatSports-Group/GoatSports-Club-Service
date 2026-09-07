package iuh.fit.clubservice.presentation.converter;

import iuh.fit.clubservice.application.dto.request.*;
import iuh.fit.clubservice.presentation.request.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class TournamentConverter {
    public CreateTournamentCommand toCommand(UUID organizerId, CreateTournamentRequest request) {
        return CreateTournamentCommand.builder().organizerId(organizerId).name(request.getName())
                .description(request.getDescription()).sportType(request.getSportType()).format(request.getFormat())
                .maxParticipants(request.getMaxParticipants()).entryFee(request.getEntryFee()).prizePool(request.getPrizePool())
                .registrationOpenDate(request.getRegistrationOpenDate()).registrationCloseDate(request.getRegistrationCloseDate())
                .startDate(request.getStartDate()).endDate(request.getEndDate()).rules(request.getRules()).build();
    }
    public RegisterTournamentTeamCommand toCommand(UUID tournamentId, UUID registeredBy,
                                                    RegisterTournamentTeamRequest request) {
        List<RegisterTournamentTeamCommand.LineupItem> lineups = request.getLineups() == null ? List.of()
                : request.getLineups().stream().map(item -> RegisterTournamentTeamCommand.LineupItem.builder()
                .playerId(item.getPlayerId()).playerName(item.getPlayerName()).lineupRole(item.getLineupRole())
                .shirtNumber(item.getShirtNumber()).build()).toList();
        return RegisterTournamentTeamCommand.builder().tournamentId(tournamentId).registeredBy(registeredBy)
                .playerId(request.getPlayerId()).clubId(request.getClubId()).teamName(request.getTeamName())
                .type(request.getType()).skillLevel(request.getSkillLevel()).lineups(lineups).build();
    }
    public UpdateFixtureResultCommand toCommand(UUID fixtureId, UUID actorId, UpdateFixtureResultRequest request) {
        return UpdateFixtureResultCommand.builder().fixtureId(fixtureId).actorUserId(actorId)
                .score1(request.getScore1()).score2(request.getScore2()).build();
    }
}
