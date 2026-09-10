package iuh.fit.clubservice.presentation.controller;

import iuh.fit.clubservice.application.dto.response.*;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.security.CurrentUserProvider;
import iuh.fit.clubservice.application.service.ClubService;
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
            @RequestParam(required = false) String keyword, Pageable pageable) {
        return clubService.searchClubs(sportType, keyword, pageable);
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
            @RequestBody(required = false) JoinClubRequest ignored) throws ServiceException {
        return clubService.joinClub(converter.toCommand(clubId, requireUser()));
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
    private UUID requireUser() throws ServiceException {
        return currentUserProvider.getCurrentUserId().orElseThrow(() -> new ServiceException("Bạn cần đăng nhập để thực hiện thao tác này."));
    }
}
