package iuh.fit.clubservice.presentation.controller;

import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.dto.request.UpdateClubActivityCommand;
import iuh.fit.clubservice.application.dto.request.UpdateClubCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.security.CurrentUserProvider;
import iuh.fit.clubservice.application.service.ClubService;
import iuh.fit.clubservice.application.dto.response.MyClubMembershipResponseCommand;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.application.dto.response.ClubInvitationResponseCommand;
import iuh.fit.clubservice.presentation.request.InviteClubMemberRequest;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.presentation.annotation.ApiMessage;
import iuh.fit.clubservice.presentation.converter.ClubConverter;
import iuh.fit.clubservice.presentation.request.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/clubs")
@RequiredArgsConstructor
public class ClubController {
    private final ClubService clubService;
    private final ClubConverter converter;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/search") @ApiMessage("Lấy danh sách câu lạc bộ thành công")
    public Page<ClubResponseCommand> search(@RequestParam(required = false) SportType sportType,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return clubService.searchClubs(sportType, city, keyword, pageable);
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
        return clubService.getMyInvitations(requireUser());
    }

    @PostMapping("/{clubId}/invitations") @ResponseStatus(HttpStatus.CREATED)
    @ApiMessage("Gửi lời mời thành công")
    public ClubInvitationResponseCommand invite(@PathVariable UUID clubId,
            @Valid @RequestBody InviteClubMemberRequest request) throws ServiceException {
        return clubService.inviteMember(clubId, request.getInviteeId(), request.getMessage(), requireUser());
    }

    @PostMapping("/invitations/{invitationId}/accept") @ApiMessage("Đã tham gia câu lạc bộ")
    public ClubMemberResponseCommand acceptInvitation(@PathVariable UUID invitationId) throws ServiceException {
        return clubService.acceptInvitation(invitationId, requireUser());
    }

    @PostMapping("/invitations/{invitationId}/decline") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineInvitation(@PathVariable UUID invitationId) throws ServiceException {
        clubService.declineInvitation(invitationId, requireUser());
    }

    @GetMapping("/me/activities") @ApiMessage("Lấy hoạt động sắp tới thành công")
    public List<ClubActivityResponseCommand> myUpcomingActivities(
            @RequestParam(defaultValue = "10") int limit) throws ServiceException {
        return clubService.getMyUpcomingActivities(requireUser(), limit);
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

    @GetMapping("/{clubId}/members") @ApiMessage("Lấy danh sách thành viên thành công")
    public List<ClubMemberResponseCommand> members(@PathVariable UUID clubId) throws ServiceException {
        return clubService.getClubMembers(clubId, currentUserProvider.getCurrentUserId().orElse(null));
    }

    @GetMapping("/{clubId}/membership/me") @ApiMessage("Lấy trạng thái thành viên thành công")
    public ResponseEntity<ClubMemberResponseCommand> myMembership(@PathVariable UUID clubId) throws ServiceException {
        return clubService.getMembership(clubId, requireUser()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{clubId}/activities") @ApiMessage("Lấy hoạt động câu lạc bộ thành công")
    public List<ClubActivityResponseCommand> activities(@PathVariable UUID clubId) {
        return clubService.getClubActivities(clubId);
    }

    @PostMapping("/{clubId}/activities") @ResponseStatus(HttpStatus.CREATED) @ApiMessage("Tạo hoạt động thành công")
    public ClubActivityResponseCommand createActivity(@PathVariable UUID clubId,
            @Valid @RequestBody CreateClubActivityRequest request) throws ServiceException {
        return clubService.createClubActivity(converter.toCommand(clubId, requireUser(), request));
    }

    @PatchMapping("/{clubId}") @ApiMessage("Cập nhật câu lạc bộ thành công")
    public ClubResponseCommand update(@PathVariable UUID clubId,
            @Valid @RequestBody UpdateClubRequest request) throws ServiceException {
        return clubService.updateClub(UpdateClubCommand.builder()
                .clubId(clubId).actorUserId(requireUser()).name(request.getName())
                .description(request.getDescription()).logoUrl(request.getLogoUrl())
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

    @PutMapping("/{clubId}/activities/{activityId}") @ApiMessage("Cập nhật hoạt động thành công")
    public ClubActivityResponseCommand updateActivity(@PathVariable UUID clubId, @PathVariable UUID activityId,
            @Valid @RequestBody UpdateClubActivityRequest request) throws ServiceException {
        return clubService.updateClubActivity(UpdateClubActivityCommand.builder()
                .clubId(clubId).activityId(activityId).actorUserId(requireUser())
                .title(request.getTitle()).description(request.getDescription()).venueId(request.getVenueId())
                .startAt(request.getStartAt()).endAt(request.getEndAt()).build());
    }

    @DeleteMapping("/{clubId}/activities/{activityId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteActivity(@PathVariable UUID clubId, @PathVariable UUID activityId) throws ServiceException {
        clubService.deleteClubActivity(clubId, activityId, requireUser());
    }

    private UUID requireUser() throws ServiceException {
        return currentUserProvider.getCurrentUserId().orElseThrow(() -> new ServiceException("Bạn cần đăng nhập để thực hiện thao tác này."));
    }
}
