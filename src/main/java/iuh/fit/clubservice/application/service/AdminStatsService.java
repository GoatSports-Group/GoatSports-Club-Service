package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.domain.enums.TournamentStatus;

import java.util.Map;

/** Số liệu CLB và giải đấu toàn nền tảng cho dashboard admin. */
public interface AdminStatsService {
    CommunityStats getCommunityStats();

    /** {@code tournamentsByStatus}: theo trạng thái hiển thị (mở/đóng đăng ký suy ra từ ngày). */
    record CommunityStats(long activeClubs, long disbandedClubs, long tournaments,
                          Map<TournamentStatus, Long> tournamentsByStatus) { }
}
