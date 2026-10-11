package iuh.fit.clubservice.presentation.controller;

import iuh.fit.clubservice.application.service.AdminCommunityService;
import iuh.fit.clubservice.application.service.AdminCommunityService.ClubRow;
import iuh.fit.clubservice.application.service.AdminCommunityService.ClubState;
import iuh.fit.clubservice.application.service.AdminCommunityService.TournamentRow;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class CommunityAdminController {
    private final AdminCommunityService adminCommunityService;

    @GetMapping("/clubs")
    @PreAuthorize("hasAuthority('GET_ALL_CLUBS')")
    public Page<ClubRow> clubs(
            @RequestParam(required = false) ClubState state,
            @RequestParam(required = false) SportType sportType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminCommunityService.getClubs(state, sportType, keyword, page, size);
    }

    @GetMapping("/tournaments")
    @PreAuthorize("hasAuthority('GET_ALL_TOURNAMENTS')")
    public Page<TournamentRow> tournaments(
            @RequestParam(required = false) TournamentStatus status,
            @RequestParam(required = false) SportType sportType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminCommunityService.getTournaments(status, sportType, keyword, page, size);
    }
}
