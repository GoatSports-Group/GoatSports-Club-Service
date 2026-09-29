package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.SportType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Phần của club-service trong ghép trận CLB đấu CLB (ai-service gọi nội bộ). */
public interface ClubMatchmakingService {

    /**
     * Hồ sơ để ghép trận; rỗng nếu CLB không tồn tại hoặc đã giải tán. {@code memberIds} là thành viên đang hoạt động,
     * để ai-service kiểm tra đồng đội chọn từ CLB (ghép đôi / đội không đại diện CLB).
     */
    Optional<Profile> getProfile(UUID clubId);

    /**
     * Ghi kết quả giao hữu cho cả hai CLB: thắng/hòa/thua, ELO và lịch sử trận. Gọi lại cùng phiên thì trả kết quả
     * đã ghi (ai-service thử lại khi lần trước lỗi mạng).
     *
     * @return ELO mới theo clubId
     */
    Map<UUID, Integer> applyFriendlyResult(FriendlyResult command) throws ServiceException;

    record Profile(UUID clubId, String name, String logoUrl, SportType sportType, int eloRating,
                   List<UUID> managerIds, long activeMemberCount, int matchCount, double winRate,
                   List<UUID> memberIds) { }

    record Side(UUID clubId, int score) { }

    record FriendlyResult(UUID sessionId, SportType sportType, String playFormat, Side first, Side second) { }
}
