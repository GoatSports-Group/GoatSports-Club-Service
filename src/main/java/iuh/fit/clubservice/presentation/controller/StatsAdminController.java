package iuh.fit.clubservice.presentation.controller;

import iuh.fit.clubservice.application.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Số liệu CLB và giải đấu toàn nền tảng cho dashboard admin. */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class StatsAdminController {
    private final AdminStatsService adminStatsService;

    @GetMapping("/community")
    @PreAuthorize("hasAuthority('GET_PLATFORM_STATS')")
    public AdminStatsService.CommunityStats community() {
        return adminStatsService.getCommunityStats();
    }
}
