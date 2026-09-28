package iuh.fit.clubservice.application.service;

import java.util.List;
import java.util.UUID;

/**
 * ELO của người chơi theo kết quả trận giải đấu. Tỉ số lưu trước, ELO tính sau (sau commit, hoặc job thử lại
 * khi auth-service lỗi): nhập tỉ số không bao giờ hỏng vì dịch vụ ELO tạm không phản hồi.
 */
public interface TournamentRatingService {
    /** true: đã xong (hoặc không có gì để tính); false: chưa tính được, để lần sau thử lại. */
    boolean syncFixture(UUID fixtureId);

    List<UUID> findPendingFixtureIds();
}
