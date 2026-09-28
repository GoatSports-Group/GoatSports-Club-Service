package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.SportType;

import java.util.List;
import java.util.UUID;

/** Hồ sơ thể thao và ELO của người chơi (auth-service): tạo hồ sơ khi đăng ký giải, tính ELO khi trận có kết quả. */
public interface SportRatingPort {
    /** Chưa có hồ sơ môn này thì tạo ở trình độ thấp nhất; có rồi thì thôi. */
    void ensureProfile(UUID userId, SportType sportType) throws ServiceException;

    /** Gọi lại cùng fixtureId (sửa tỉ số, thử lại) là an toàn: auth-service hoàn lần cũ hoặc bỏ qua nếu không đổi. */
    List<RatedPlayer> rateTournamentMatch(TournamentMatch match) throws ServiceException;

    record Side(String name, int score, List<UUID> playerIds) { }

    record TournamentMatch(UUID fixtureId, UUID tournamentId, SportType sportType, String title, Side first, Side second) { }

    record RatedPlayer(UUID userId, int oldElo, int newElo, String outcome, boolean changed) {
        public int delta() {
            return newElo - oldElo;
        }
    }
}
