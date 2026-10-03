package iuh.fit.clubservice.presentation.controller;

import iuh.fit.clubservice.application.exception.UnauthenticatedException;
import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.dto.request.ScoutingQueryCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubActivityCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.security.CurrentUserProvider;
import iuh.fit.clubservice.application.dto.response.ScoutedPlayerResponseCommand;
import iuh.fit.clubservice.application.service.ClubScoutingService;
import iuh.fit.clubservice.application.service.ClubService;
import iuh.fit.clubservice.application.service.ClubFeaturedService;
import iuh.fit.clubservice.application.service.ClubActivityService;
import iuh.fit.clubservice.application.service.ClubInvitationService;
import iuh.fit.clubservice.application.service.ClubMatchService;
import iuh.fit.clubservice.application.dto.response.MyClubMembershipResponseCommand;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.application.dto.response.ClubInvitationResponseCommand;
import iuh.fit.clubservice.presentation.request.InviteClubMemberRequest;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.presentation.annotation.ApiMessage;
import iuh.fit.clubservice.presentation.converter.ClubConverter;
import iuh.fit.clubservice.presentation.request.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.DayOfWeek;
import java.util.*;

@RestController
@RequestMapping("/api/v1/clubs")
@RequiredArgsConstructor
public class ClubController {
    private final ClubService clubService;
    private final ClubActivityService activityService;
    private final ClubInvitationService invitationService;
    private final ClubMatchService matchService;
    private final ClubScoutingService scoutingService;
    private final ClubFeaturedService featuredService;
    private final ClubConverter converter;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/search") @ApiMessage("Lấy danh sách câu lạc bộ thành công")
    public Page<ClubResponseCommand> search(@RequestParam(required = false) SportType sportType,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return clubService.searchClubs(sportType, city, keyword, pageable);
    }

    @GetMapping("/featured") @ApiMessage("Lấy câu lạc bộ nổi bật thành công")
    public Page<FeaturedClubResponseCommand> featured(@RequestParam(required = false) List<SportType> sport,
            @RequestParam(required = false) List<String> city,
            @RequestParam(required = false) SportType filterSport,
            @RequestParam(required = false) String filterCity,
            @PageableDefault(size = 12) Pageable pageable) {
        return featuredService.getFeatured(currentUserProvider.getCurrentUserId().orElse(null), sport, city,
                filterSport, filterCity, PageRequest.of(pageable.getPageNumber(), Math.min(Math.max(pageable.getPageSize(), 1), 50)));
    }

    @GetMapping("/me") @ApiMessage("Lấy câu lạc bộ của bạn thành công")
    public List<MyClubMembershipResponseCommand> myClubs() throws ServiceException {
        return clubService.getMyMemberships(requireUser(), ClubMemberStatus.ACTIVE);
    }

    @GetMapping("/me/requests") @ApiMessage("Lấy yêu cầu tham gia đang chờ thành công")
    public List<MyClubMembershipResponseCommand> myPendingRequests() throws ServiceException {
        return clubService.getMyMemberships(requireUser(), ClubMemberStatus.PENDING);
    }

    @GetMapping("/me/invitations") @ApiMessage("Lấy lời mời vào câu lạc bộ thành công")
    public List<ClubInvitationResponseCommand> myInvitations() throws ServiceException {
        return invitationService.getMyInvitations(requireUser());
    }

    @PostMapping("/{clubId}/invitations") @ResponseStatus(HttpStatus.CREATED)
    @ApiMessage("Gửi lời mời thành công")
    public ClubInvitationResponseCommand invite(@PathVariable UUID clubId,
            @Valid @RequestBody InviteClubMemberRequest request) throws ServiceException {
        return invitationService.inviteMember(clubId, request.getInviteeId(), request.getMessage(), requireUser());
    }

    @PostMapping("/invitations/{invitationId}/accept") @ApiMessage("Đã tham gia câu lạc bộ")
    public ClubMemberResponseCommand acceptInvitation(@PathVariable UUID invitationId) throws ServiceException {
        return invitationService.acceptInvitation(invitationId, requireUser());
    }

    @PostMapping("/invitations/{invitationId}/decline") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineInvitation(@PathVariable UUID invitationId) throws ServiceException {
        invitationService.declineInvitation(invitationId, requireUser());
    }

    @GetMapping("/me/activities") @ApiMessage("Lấy hoạt động sắp tới thành công")
    public List<ClubActivityResponseCommand> myUpcomingActivities(
            @RequestParam(defaultValue = "10") int limit) throws ServiceException {
        return activityService.getMyUpcomingActivities(requireUser(), limit);
    }

    @GetMapping("/{clubId}") @ApiMessage("Lấy chi tiết câu lạc bộ thành công")
    public ClubResponseCommand details(@PathVariable UUID clubId) throws ServiceException {
        return clubService.getClubDetails(clubId);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @ApiMessage("Tạo câu lạc bộ thành công")
    public ClubResponseCommand create(@Valid @RequestBody CreateClubRequest request) throws ServiceException {
        return clubService.createClub(converter.toCommand(requireUser(), request));
    }

    @PostMapping("/{clubId}/join") @ApiMessage("Gửi yêu cầu tham gia câu lạc bộ thành công")
    public ClubMemberResponseCommand join(@PathVariable UUID clubId,
            @Valid @RequestBody(required = false) JoinClubRequest request) throws ServiceException {
        return clubService.joinClub(converter.toCommand(clubId, requireUser(), request));
    }

    @PutMapping("/{clubId}/members/{membershipId}/respond") @ApiMessage("Xử lý yêu cầu tham gia thành công")
    public ClubMemberResponseCommand respond(@PathVariable UUID clubId, @PathVariable UUID membershipId,
            @Valid @RequestBody RespondClubMemberRequest request) throws ServiceException {
        return clubService.respondMembership(converter.toCommand(membershipId, requireUser(), request));
    }

    @DeleteMapping("/{clubId}/leave") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID clubId) throws ServiceException { clubService.leaveClub(clubId, requireUser()); }

    @GetMapping("/{clubId}/scouting") @ApiMessage("Tìm người chơi phù hợp thành công")
    public List<ScoutedPlayerResponseCommand> scouting(
            @PathVariable UUID clubId,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Set<SkillLevel> skillLevels,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) DayOfWeek availableOn
    ) throws ServiceException {
        return scoutingService.findCandidates(clubId, requireUser(),
                new ScoutingQueryCommand(radiusKm, limit, skillLevels, position, availableOn));
    }

    @GetMapping("/{clubId}/scouting/shortlist") @ApiMessage("Lấy danh sách theo dõi thành công")
    public List<ShortlistEntryResponseCommand> shortlist(@PathVariable UUID clubId) throws ServiceException {
        return scoutingService.getShortlist(clubId, requireUser());
    }

    @PutMapping("/{clubId}/scouting/shortlist/{userId}") @ApiMessage("Đã lưu vào danh sách theo dõi")
    public ShortlistEntryResponseCommand saveShortlistEntry(@PathVariable UUID clubId, @PathVariable UUID userId,
            @Valid @RequestBody(required = false) SaveShortlistEntryRequest request) throws ServiceException {
        return scoutingService.saveShortlistEntry(clubId, requireUser(), userId,
                request == null ? null : request.getNote());
    }

    @DeleteMapping("/{clubId}/scouting/shortlist/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeShortlistEntry(@PathVariable UUID clubId, @PathVariable UUID userId) throws ServiceException {
        scoutingService.removeShortlistEntry(clubId, requireUser(), userId);
    }

    /** Tab "Mời bạn bè": trạng thái của từng người bạn với CLB (client lấy danh sách bạn từ social-service). */
    @PostMapping("/{clubId}/scouting/relations") @ApiMessage("Lấy trạng thái với câu lạc bộ thành công")
    public java.util.Map<UUID, iuh.fit.clubservice.application.dto.response.PlayerClubRelation> relations(
            @PathVariable UUID clubId,
            @Valid @RequestBody RelationLookupRequest request) throws ServiceException {
        return scoutingService.getRelations(clubId, requireUser(), request.userIds());
    }

    public record RelationLookupRequest(
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.Size(max = 500, message = "Mỗi lần chỉ tra tối đa 500 người")
            java.util.List<UUID> userIds) { }

    @GetMapping("/{clubId}/invitations") @ApiMessage("Lấy lời mời đã gửi thành công")
    public List<SentInvitationResponseCommand> sentInvitations(@PathVariable UUID clubId) throws ServiceException {
        return scoutingService.getSentInvitations(clubId, requireUser());
    }

    @PostMapping("/{clubId}/invitations/{invitationId}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelInvitation(@PathVariable UUID clubId, @PathVariable UUID invitationId) throws ServiceException {
        invitationService.cancelInvitation(clubId, invitationId, requireUser());
    }

    @PatchMapping("/{clubId}/owner") @ApiMessage("Chuyển quyền sở hữu câu lạc bộ thành công")
    public ClubMemberResponseCommand transferOwnership(@PathVariable UUID clubId,
                                                       @RequestParam UUID membershipId) throws ServiceException {
        return clubService.transferOwnership(clubId, membershipId, requireUser());
    }

    @PostMapping("/{clubId}/disband") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disband(@PathVariable UUID clubId) throws ServiceException {
        clubService.disbandClub(clubId, requireUser());
    }

    @GetMapping("/{clubId}/members") @ApiMessage("Lấy danh sách thành viên thành công")
    public List<ClubMemberResponseCommand> members(@PathVariable UUID clubId) throws ServiceException {
        return clubService.getClubMembers(clubId, currentUserProvider.getCurrentUserId().orElse(null));
    }

    @GetMapping("/me/activities/page") @ApiMessage("Lấy hoạt động sắp tới phân trang thành công")
    public Page<ClubActivityResponseCommand> myUpcomingActivitiesPage(
            @PageableDefault(size = 3) Pageable pageable) throws ServiceException {
        return activityService.getMyUpcomingActivitiesPage(requireUser(), pageable);
    }

    @GetMapping("/{clubId}/members/page") @ApiMessage("Lấy danh sách thành viên phân trang thành công")
    public Page<ClubMemberResponseCommand> membersPage(@PathVariable UUID clubId,
            @RequestParam(defaultValue = "ACTIVE") ClubMemberStatus status,
            @PageableDefault(size = 7, sort = "joinedAt", direction = Sort.Direction.ASC) Pageable pageable)
            throws ServiceException {
        return clubService.getClubMembersPage(clubId,
                currentUserProvider.getCurrentUserId().orElse(null), status, pageable);
    }

    @GetMapping("/{clubId}/membership/me") @ApiMessage("Lấy trạng thái thành viên thành công")
    public ResponseEntity<ClubMemberResponseCommand> myMembership(@PathVariable UUID clubId) throws ServiceException {
        return clubService.getMembership(clubId, requireUser()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{clubId}/activities") @ApiMessage("Lấy hoạt động câu lạc bộ thành công")
    public List<ClubActivityResponseCommand> activities(@PathVariable UUID clubId) {
        return activityService.getClubActivities(clubId);
    }

    @GetMapping("/{clubId}/activities/page") @ApiMessage("Lấy hoạt động câu lạc bộ phân trang thành công")
    public Page<ClubActivityResponseCommand> activitiesPage(@PathVariable UUID clubId,
            @PageableDefault(size = 3) Pageable pageable) {
        return activityService.getClubActivitiesPage(clubId, pageable);
    }

    @GetMapping("/{clubId}/matches/recent") @ApiMessage("Lấy các trận gần nhất của câu lạc bộ thành công")
    public List<ClubRecentMatchResponseCommand> recentMatches(@PathVariable UUID clubId,
            @RequestParam(defaultValue = "3") int limit) throws ServiceException {
        return matchService.getClubRecentMatches(clubId, limit);
    }

    @GetMapping("/{clubId}/matches/page") @ApiMessage("Lấy lịch sử thi đấu phân trang thành công")
    public Page<ClubRecentMatchResponseCommand> matchesPage(@PathVariable UUID clubId,
            @RequestParam(required = false) UUID tournamentId,
            @PageableDefault(size = 3) Pageable pageable) throws ServiceException {
        return matchService.getClubMatchesPage(clubId, tournamentId, pageable);
    }

    @GetMapping("/{clubId}/tournaments") @ApiMessage("Lấy các giải câu lạc bộ đã tham gia thành công")
    public List<iuh.fit.clubservice.application.dto.response.ClubTournamentResponseCommand> tournaments(
            @PathVariable UUID clubId) throws ServiceException {
        return matchService.getClubTournaments(clubId);
    }

    @PostMapping("/{clubId}/tournaments/{tournamentId}/share") @ResponseStatus(HttpStatus.CREATED)
    @ApiMessage("Đã đăng thành tích lên Cộng đồng")
    public java.util.Map<String, UUID> shareTournament(@PathVariable UUID clubId, @PathVariable UUID tournamentId,
            @Valid @RequestBody iuh.fit.clubservice.presentation.request.ShareHighlightRequest request)
            throws ServiceException {
        return java.util.Map.of("postId",
                matchService.shareTournament(clubId, tournamentId, requireUser(), request.title(), request.content()));
    }

    @PostMapping("/{clubId}/activities") @ResponseStatus(HttpStatus.CREATED) @ApiMessage("Tạo hoạt động thành công")
    public ClubActivityResponseCommand createActivity(@PathVariable UUID clubId,
            @Valid @RequestBody CreateClubActivityRequest request) throws ServiceException {
        return activityService.createClubActivity(converter.toCommand(clubId, requireUser(), request));
    }

    @PatchMapping("/{clubId}") @ApiMessage("Cập nhật câu lạc bộ thành công")
    public ClubResponseCommand update(@PathVariable UUID clubId,
            @Valid @RequestBody UpdateClubRequest request) throws ServiceException {
        return clubService.updateClub(UpdateClubCommand.builder()
                .clubId(clubId).actorUserId(requireUser()).name(request.getName())
                .description(request.getDescription()).logoUrl(request.getLogoUrl()).bannerUrl(request.getBannerUrl())
                .city(request.getCity()).location(request.getLocation()).tags(request.getTags())
                .privacy(request.getPrivacy()).approvalMode(request.getApprovalMode()).build());
    }

    @PatchMapping("/{clubId}/members/{membershipId}/role") @ApiMessage("Cập nhật vai trò thành viên thành công")
    public ClubMemberResponseCommand changeRole(@PathVariable UUID clubId, @PathVariable UUID membershipId,
            @Valid @RequestBody ChangeClubMemberRoleRequest request) throws ServiceException {
        return clubService.changeMemberRole(clubId, membershipId, request.getRole(), requireUser());
    }

    @DeleteMapping("/{clubId}/members/{membershipId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID clubId, @PathVariable UUID membershipId,
            @RequestParam(defaultValue = "false") boolean ban) throws ServiceException {
        clubService.removeMember(clubId, membershipId, ban, requireUser());
    }

    @PostMapping("/{clubId}/members/{membershipId}/unban") @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiMessage("Đã gỡ cấm người chơi")
    public void unbanMember(@PathVariable UUID clubId, @PathVariable UUID membershipId) throws ServiceException {
        clubService.unbanMember(clubId, membershipId, requireUser());
    }

    @PutMapping("/{clubId}/activities/{activityId}") @ApiMessage("Cập nhật hoạt động thành công")
    public ClubActivityResponseCommand updateActivity(@PathVariable UUID clubId, @PathVariable UUID activityId,
            @Valid @RequestBody UpdateClubActivityRequest request) throws ServiceException {
        return activityService.updateClubActivity(UpdateClubActivityCommand.builder()
                .clubId(clubId).activityId(activityId).actorUserId(requireUser())
                .title(request.getTitle()).description(request.getDescription()).venueId(request.getVenueId())
                .startAt(request.getStartAt()).endAt(request.getEndAt()).build());
    }

    @DeleteMapping("/{clubId}/activities/{activityId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteActivity(@PathVariable UUID clubId, @PathVariable UUID activityId) throws ServiceException {
        activityService.deleteClubActivity(clubId, activityId, requireUser());
    }

    private UUID requireUser() throws ServiceException {
        return currentUserProvider.getCurrentUserId().orElseThrow(() -> new UnauthenticatedException("Bạn cần đăng nhập để thực hiện thao tác này."));
    }
}
