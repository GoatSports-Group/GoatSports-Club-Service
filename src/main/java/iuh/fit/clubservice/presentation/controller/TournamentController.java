package iuh.fit.clubservice.presentation.controller;

import iuh.fit.clubservice.application.exception.UnauthenticatedException;
import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand;
import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.security.CurrentUserProvider;
import iuh.fit.clubservice.application.service.TournamentRegistrationService;
import iuh.fit.clubservice.application.service.TournamentFixtureService;
import iuh.fit.clubservice.application.service.TournamentScheduleService;
import iuh.fit.clubservice.application.service.TournamentService;
import iuh.fit.clubservice.application.service.TournamentTeamService;
import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.domain.enums.*;
import iuh.fit.clubservice.domain.service.SportParticipationPolicy;
import iuh.fit.clubservice.presentation.annotation.ApiMessage;
import iuh.fit.clubservice.presentation.converter.TournamentConverter;
import iuh.fit.clubservice.presentation.request.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    private final TournamentFixtureService fixtureService;
    private final TournamentScheduleService scheduleService;
    private final TournamentRegistrationService registrationService;
    private final TournamentTeamService teamService;
    private final TournamentConverter converter;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/search")
    @ApiMessage("Lấy danh sách giải đấu thành công")
    public Page<TournamentResponseCommand> search(@RequestParam(required = false) SportType sportType,
            @RequestParam(required = false) TournamentStatus status,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return tournamentService.searchTournaments(sportType, status, keyword, pageable);
    }

    @GetMapping("/play-formats")
    @ApiMessage("Lấy hình thức thi đấu thành công")
    public List<SportParticipationPolicy.PlayFormat> playFormats(@RequestParam SportType sportType) {
        return SportParticipationPolicy.formatsOf(sportType);
    }

    @GetMapping("/me")
    @ApiMessage("Lấy giải đấu của tôi thành công")
    public Page<TournamentResponseCommand> mine(@RequestParam(defaultValue = "PARTICIPATING") String role,
            Pageable pageable) throws ServiceException {
        return tournamentService.getMyTournaments(requireUser(), "ORGANIZING".equalsIgnoreCase(role), pageable);
    }

    @GetMapping("/invitations/me")
    @ApiMessage("Lấy lời mời vào đội thành công")
    public List<TournamentTeamService.TeamInvitation> myInvitations() throws ServiceException {
        return teamService.getMyInvitations(requireUser());
    }

    @GetMapping("/{tournamentId}")
    public TournamentResponseCommand details(@PathVariable UUID tournamentId) throws ServiceException {
        return tournamentService.getTournamentDetails(tournamentId, currentUserProvider.getCurrentUserId().orElse(null));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TournamentResponseCommand create(@Valid @RequestBody CreateTournamentRequest request) throws ServiceException {
        return tournamentService.createTournament(converter.toCommand(requireUser(), request));
    }

    @PutMapping("/{tournamentId}")
    @ApiMessage("Cập nhật giải đấu thành công")
    public TournamentResponseCommand update(@PathVariable UUID tournamentId,
            @Valid @RequestBody CreateTournamentRequest request) throws ServiceException {
        return tournamentService.updateTournament(tournamentId, converter.toCommand(requireUser(), request));
    }

    @PostMapping("/{tournamentId}/register")
    public TournamentRegistrationResponseCommand register(@PathVariable UUID tournamentId,
            @Valid @RequestBody RegisterTournamentTeamRequest request) throws ServiceException {
        return registrationService.register(converter.toCommand(tournamentId, requireUser(), request));
    }

    @PostMapping("/{tournamentId}/registrations/{registrationId}/respond")
    @ApiMessage("Đã trả lời lời mời vào đội")
    public TournamentRegistrationResponseCommand respond(@PathVariable UUID tournamentId, @PathVariable UUID registrationId,
            @Valid @RequestBody RespondInvitationRequest request) throws ServiceException {
        return teamService.respondInvitation(tournamentId, registrationId, requireUser(), request.accept());
    }

    @PostMapping("/{tournamentId}/registrations/{registrationId}/members")
    @ApiMessage("Đã gửi lời mời vào đội")
    public TournamentRegistrationResponseCommand invite(@PathVariable UUID tournamentId, @PathVariable UUID registrationId,
            @Valid @RequestBody InviteMembersRequest request) throws ServiceException {
        List<RegisterTournamentTeamCommand.LineupItem> invitees = request.members().stream()
                .map(item -> RegisterTournamentTeamCommand.LineupItem.builder().playerId(item.getPlayerId())
                        .playerName(item.getPlayerName()).lineupRole(item.getLineupRole())
                        .shirtNumber(item.getShirtNumber()).build()).toList();
        return teamService.inviteMembers(tournamentId, registrationId, requireUser(), invitees);
    }

    @DeleteMapping("/{tournamentId}/registrations/{registrationId}/members/{playerId}")
    @ApiMessage("Đã cập nhật đội hình")
    public TournamentRegistrationResponseCommand removeMember(@PathVariable UUID tournamentId,
            @PathVariable UUID registrationId, @PathVariable UUID playerId) throws ServiceException {
        return teamService.removeMember(tournamentId, registrationId, requireUser(), playerId);
    }

    @PostMapping("/{tournamentId}/registrations/{registrationId}/checkout")
    @ApiMessage("Đã tạo mã thanh toán lệ phí")
    public TournamentRegistrationService.FeeCheckout checkout(@PathVariable UUID tournamentId,
            @PathVariable UUID registrationId) throws ServiceException {
        return registrationService.checkout(tournamentId, registrationId, requireUser());
    }

    @DeleteMapping("/{tournamentId}/registrations/{registrationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID tournamentId, @PathVariable UUID registrationId,
            @RequestParam(required = false) @Size(max = 300) String reason)
            throws ServiceException {
        registrationService.cancel(tournamentId, registrationId, requireUser(), reason);
    }

    @GetMapping("/{tournamentId}/teams")
    public List<TournamentRegistrationResponseCommand> teams(@PathVariable UUID tournamentId) {
        return registrationService.getTeams(tournamentId);
    }

    @PostMapping("/{tournamentId}/generate-fixtures")
    public List<TournamentFixtureResponseCommand> generate(@PathVariable UUID tournamentId) throws ServiceException {
        return fixtureService.generateFixtures(tournamentId, requireUser());
    }

    @GetMapping("/{tournamentId}/fixtures") public List<TournamentFixtureResponseCommand> fixtures(@PathVariable UUID tournamentId) {
        return fixtureService.getFixtures(tournamentId);
    }

    @PutMapping("/{tournamentId}/fixtures/{fixtureId}/result")
    public TournamentFixtureResponseCommand result(@PathVariable UUID tournamentId, @PathVariable UUID fixtureId,
            @Valid @RequestBody UpdateFixtureResultRequest request) throws ServiceException {
        return fixtureService.updateFixtureResult(converter.toCommand(fixtureId, requireUser(), request));
    }

    @GetMapping("/{tournamentId}/standings") public List<TournamentStandingResponseCommand> standings(@PathVariable UUID tournamentId) {
        return fixtureService.getStandings(tournamentId);
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

    @GetMapping("/{tournamentId}/reservations") @ApiMessage("Lấy lịch sân thi đấu thành công")
    public List<TournamentVenueReservationResponseCommand> reservations(@PathVariable UUID tournamentId) {
        return scheduleService.getReservations(tournamentId);
    }

    @PostMapping("/{tournamentId}/reservations") @ResponseStatus(HttpStatus.CREATED)
    @ApiMessage("Đã xếp sân cho trận đấu")
    public TournamentVenueReservationResponseCommand reserve(@PathVariable UUID tournamentId,
            @Valid @RequestBody ReserveTournamentVenueRequest request) throws ServiceException {
        return scheduleService.reserveVenue(converter.toCommand(tournamentId, requireUser(), request));
    }

    @DeleteMapping("/{tournamentId}/reservations/{reservationId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID tournamentId, @PathVariable UUID reservationId) throws ServiceException {
        scheduleService.releaseReservation(tournamentId, reservationId, requireUser());
    }

    private UUID requireUser() throws ServiceException {
        return currentUserProvider.getCurrentUserId().orElseThrow(() -> new UnauthenticatedException("Bạn cần đăng nhập để thực hiện thao tác này."));
    }

    public record RespondInvitationRequest(@NotNull(message = "Chọn nhận lời hoặc từ chối") Boolean accept) { }

    public record InviteMembersRequest(
            @NotEmpty(message = "Chọn ít nhất một người để mời") @Size(max = 30) @Valid
            List<RegisterTournamentTeamRequest.LineupItemRequest> members) { }
}
