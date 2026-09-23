package iuh.fit.clubservice.presentation.controller;

import iuh.fit.clubservice.application.exception.UnauthenticatedException;
import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.security.CurrentUserProvider;
import iuh.fit.clubservice.application.service.TournamentService;
import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.domain.enums.*;
import iuh.fit.clubservice.presentation.annotation.ApiMessage;
import iuh.fit.clubservice.presentation.converter.TournamentConverter;
import iuh.fit.clubservice.presentation.request.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tournaments")
@RequiredArgsConstructor
public class TournamentController {
    private final TournamentService tournamentService;
    private final TournamentConverter converter;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/search") @ApiMessage("Lấy danh sách giải đấu thành công")
    public Page<TournamentResponseCommand> search(@RequestParam(required = false) SportType sportType,
            @RequestParam(required = false) TournamentStatus status,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return tournamentService.searchTournaments(sportType, status, keyword, pageable);
    }

    @GetMapping("/{tournamentId}") public TournamentResponseCommand details(@PathVariable UUID tournamentId) throws ServiceException {
        return tournamentService.getTournamentDetails(tournamentId);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TournamentResponseCommand create(@Valid @RequestBody CreateTournamentRequest request) throws ServiceException {
        return tournamentService.createTournament(converter.toCommand(requireUser(), request));
    }

    @PostMapping("/{tournamentId}/register")
    public TournamentRegistrationResponseCommand register(@PathVariable UUID tournamentId,
            @Valid @RequestBody RegisterTournamentTeamRequest request) throws ServiceException {
        return tournamentService.registerTeam(converter.toCommand(tournamentId, requireUser(), request));
    }

    @DeleteMapping("/{tournamentId}/registrations/{registrationId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID tournamentId, @PathVariable UUID registrationId) throws ServiceException {
        tournamentService.cancelRegistration(tournamentId, registrationId, requireUser());
    }

    @GetMapping("/{tournamentId}/teams") public List<TournamentRegistrationResponseCommand> teams(@PathVariable UUID tournamentId) {
        return tournamentService.getTournamentTeams(tournamentId);
    }

    @PostMapping("/{tournamentId}/generate-fixtures")
    public List<TournamentFixtureResponseCommand> generate(@PathVariable UUID tournamentId) throws ServiceException {
        return tournamentService.generateFixtures(tournamentId, requireUser());
    }

    @GetMapping("/{tournamentId}/fixtures") public List<TournamentFixtureResponseCommand> fixtures(@PathVariable UUID tournamentId) {
        return tournamentService.getFixtures(tournamentId);
    }

    @PutMapping("/{tournamentId}/fixtures/{fixtureId}/result")
    public TournamentFixtureResponseCommand result(@PathVariable UUID tournamentId, @PathVariable UUID fixtureId,
            @Valid @RequestBody UpdateFixtureResultRequest request) throws ServiceException {
        return tournamentService.updateFixtureResult(converter.toCommand(fixtureId, requireUser(), request));
    }

    @GetMapping("/{tournamentId}/standings") public List<TournamentStandingResponseCommand> standings(@PathVariable UUID tournamentId) {
        return tournamentService.getStandings(tournamentId);
    }

    @PatchMapping("/{tournamentId}/status") @ApiMessage("Cập nhật trạng thái giải đấu thành công")
    public TournamentResponseCommand changeStatus(@PathVariable UUID tournamentId,
            @Valid @RequestBody ChangeTournamentStatusRequest request) throws ServiceException {
        return tournamentService.changeStatus(tournamentId, request.getStatus(), requireUser());
    }

    @GetMapping("/{tournamentId}/eligibility-rules") @ApiMessage("Lấy điều kiện tham dự thành công")
    public List<TournamentEligibilityRule> eligibilityRules(@PathVariable UUID tournamentId) {
        return tournamentService.getEligibilityRules(tournamentId);
    }

    @GetMapping("/{tournamentId}/reservations") @ApiMessage("Lấy danh sách đặt sân thành công")
    public List<TournamentVenueReservationResponseCommand> reservations(@PathVariable UUID tournamentId) {
        return tournamentService.getReservations(tournamentId);
    }

    @PostMapping("/{tournamentId}/reservations") @ResponseStatus(HttpStatus.CREATED)
    @ApiMessage("Đặt sân cho giải đấu thành công")
    public TournamentVenueReservationResponseCommand reserve(@PathVariable UUID tournamentId,
            @Valid @RequestBody ReserveTournamentVenueRequest request) throws ServiceException {
        return tournamentService.reserveVenue(converter.toCommand(tournamentId, requireUser(), request));
    }

    @DeleteMapping("/{tournamentId}/reservations/{reservationId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID tournamentId, @PathVariable UUID reservationId) throws ServiceException {
        tournamentService.releaseReservation(tournamentId, reservationId, requireUser());
    }

    private UUID requireUser() throws ServiceException {
        return currentUserProvider.getCurrentUserId().orElseThrow(() -> new UnauthenticatedException("Bạn cần đăng nhập để thực hiện thao tác này."));
    }
}
