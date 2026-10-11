package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** CLB và giải đấu toàn sàn cho admin (chỉ đọc). Bản nháp giải đấu là riêng của ban tổ chức nên không hiện. */
public interface AdminCommunityService {
    Page<ClubRow> getClubs(ClubState state, SportType sportType, String keyword, int page, int size);

    /** {@code status} là trạng thái hiển thị (mở/đóng đăng ký suy ra từ ngày), cùng luật với trang người chơi. */
    Page<TournamentRow> getTournaments(TournamentStatus status, SportType sportType, String keyword, int page, int size);

    enum ClubState { ACTIVE, DISBANDED }

    record ClubRow(UUID clubId, String name, SportType sportType, String city, String logoUrl, UUID ownerId,
                   ClubPrivacy privacy, boolean disbanded, LocalDateTime disbandedAt, Integer eloRating,
                   Integer matchCount, LocalDateTime createdAt, long memberCount) {
        /** Projection JPQL: "đã giải tán" suy từ disbandedAt. */
        public ClubRow(UUID clubId, String name, SportType sportType, String city, String logoUrl, UUID ownerId,
                       ClubPrivacy privacy, LocalDateTime disbandedAt, Integer eloRating, Integer matchCount,
                       LocalDateTime createdAt, Long memberCount) {
            this(clubId, name, sportType, city, logoUrl, ownerId, privacy, disbandedAt != null, disbandedAt,
                    eloRating, matchCount, createdAt, memberCount == null ? 0 : memberCount);
        }
    }

    record TournamentRow(UUID tournamentId, String name, SportType sportType, TournamentFormat format,
                         TournamentStatus status, UUID organizerId, UUID venueId, Integer maxParticipants,
                         long currentParticipants, Double entryFee, Double prizePool, LocalDate registrationOpenDate,
                         LocalDate registrationCloseDate, LocalDate startDate, LocalDate endDate,
                         LocalDateTime createdAt) {
    }
}
