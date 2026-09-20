package iuh.fit.clubservice.infrastructure;

import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.SportType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Dữ liệu mẫu cho môi trường dev. Bật bằng `app.demo-data.enabled=true`.
 *
 * <p>Dùng chung quy ước sinh id với auth-service và venue-service, nên:
 * <ul>
 *   <li>{@code clubs.owner_id} và {@code club_members.user_id} trỏ đúng người trong auth-service</li>
 *   <li>{@code club_activities.venue_id} trỏ đúng sân trong venue-service</li>
 *   <li>{@code tournament_registrations.registered_by} trỏ đúng người đăng ký</li>
 * </ul>
 * Không service nào phải gọi service khác lúc seed.
 */
@Slf4j
@Component
@Order(20)
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    @PersistenceContext
    private EntityManager entityManager;

    private static UUID userId(String email) {
        return UUID.nameUUIDFromBytes(("goatsports:user:" + email).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID id(String kind, String key) {
        return UUID.nameUUIDFromBytes(("goatsports:" + kind + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static final String HUY = "huy.danggia@goatsports.vn";
    private static final String TUNG = "tung.vuthanh@goatsports.vn";
    private static final String NHAT_MINH = "minh.hoangnhat@goatsports.vn";
    private static final String KHANH_LINH = "linh.buikhanh@goatsports.vn";
    private static final String DUC_ANH = "anh.ngoduc@goatsports.vn";
    private static final String HAI_DANG = "dang.trinhhai@goatsports.vn";
    private static final String THU_TRANG = "trang.lythu@goatsports.vn";
    private static final String BAO_NGOC = "ngoc.caobao@goatsports.vn";

    private record MemberSeed(String email, ClubRole role) {}

    private record ClubSeed(String name, String ownerEmail, SportType sportType, String description,
                            ClubPrivacy privacy, ClubApprovalMode approvalMode,
                            List<MemberSeed> members) {}

    private record ActivitySeed(String clubName, String title, String description,
                                String venueName, int daysFromNow, LocalTime startTime, int hours,
                                String creatorEmail) {}

    private record RegistrationSeed(String email, String teamName) {}

    private static final List<ClubSeed> CLUBS = List.of(
            new ClubSeed("CLB Bóng Đá Sài Gòn Phủi", HUY, SportType.FOOTBALL,
                    "Đá giao hữu sân 7 tối thứ 3 và thứ 6 hàng tuần tại Bình Thạnh. Ưu tiên anh em đi làm.",
                    ClubPrivacy.PUBLIC, ClubApprovalMode.MANUAL,
                    List.of(new MemberSeed(HUY, ClubRole.OWNER),
                            new MemberSeed(TUNG, ClubRole.ADMIN),
                            new MemberSeed(NHAT_MINH, ClubRole.MEMBER),
                            new MemberSeed(HAI_DANG, ClubRole.MEMBER))),

            new ClubSeed("CLB Cầu Lông Phú Nhuận", KHANH_LINH, SportType.BADMINTON,
                    "Sinh hoạt sáng thứ 7 và chủ nhật, có HLV hướng dẫn cho người mới.",
                    ClubPrivacy.PUBLIC, ClubApprovalMode.AUTO,
                    List.of(new MemberSeed(KHANH_LINH, ClubRole.OWNER),
                            new MemberSeed(THU_TRANG, ClubRole.MEMBER),
                            new MemberSeed(BAO_NGOC, ClubRole.MEMBER)))
    );

    private static final List<ActivitySeed> ACTIVITIES = List.of(
            new ActivitySeed("CLB Bóng Đá Sài Gòn Phủi", "Đá giao hữu tuần này",
                    "Tập trung trước 30 phút để khởi động. Mang cả áo sáng và áo tối.",
                    "Sân Thể Thao Bình Thạnh", 3, LocalTime.of(19, 0), 2, HUY),
            new ActivitySeed("CLB Cầu Lông Phú Nhuận", "Buổi tập kỹ thuật đập cầu",
                    "HLV hướng dẫn nhóm mới, ai có vợt riêng thì mang theo.",
                    "Trung Tâm Cầu Lông Phú Nhuận", 5, LocalTime.of(8, 0), 2, KHANH_LINH)
    );

    private static final String TOURNAMENT_NAME = "Giải Bóng Đá Phong Trào Sài Gòn Mở Rộng 2026";

    private static final List<RegistrationSeed> REGISTRATIONS = List.of(
            new RegistrationSeed(HUY, "Sài Gòn Phủi"),
            new RegistrationSeed(TUNG, "Thanh Đa FC"),
            new RegistrationSeed(NHAT_MINH, "Quận 7 United"),
            new RegistrationSeed(HAI_DANG, "Hà Nội Trẻ"),
            new RegistrationSeed(DUC_ANH, "Cầu Giấy FC")
    );

    @Override
    @Transactional
    public void run(String... args) {
        int clubs = 0;
        int members = 0;
        for (ClubSeed club : CLUBS) {
            clubs += insertClub(club);
            for (MemberSeed member : club.members()) {
                members += insertMember(club, member);
            }
        }
        int activities = 0;
        for (ActivitySeed activity : ACTIVITIES) {
            activities += insertActivity(activity);
        }
        int tournaments = insertTournament();
        int registrations = 0;
        for (RegistrationSeed registration : REGISTRATIONS) {
            registrations += insertRegistration(registration);
        }
        log.info("Dữ liệu mẫu: {} CLB, {} thành viên, {} hoạt động, {} giải đấu, {} đăng ký"
                        + " (bỏ qua bản ghi đã có)",
                clubs, members, activities, tournaments, registrations);
    }

    private int insertClub(ClubSeed seed) {
        return entityManager.createNativeQuery("""
                        insert into clubs (club_id, name, description, sport_type, owner_id,
                                           approval_mode, privacy, conversation_id, active,
                                           win_count, loss_count, draw_count, match_count, win_rate,
                                           created_at, created_by, updated_at, updated_by)
                        select :clubId, :name, :description, :sportType, :ownerId,
                               :approvalMode, :privacy, :conversationId, true,
                               0, 0, 0, 0, 0,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from clubs where club_id = :clubId)
                        """)
                .setParameter("clubId", id("club", seed.name()))
                .setParameter("name", seed.name())
                .setParameter("description", seed.description())
                .setParameter("sportType", seed.sportType().name())
                .setParameter("ownerId", userId(seed.ownerEmail()))
                .setParameter("approvalMode", seed.approvalMode().name())
                .setParameter("privacy", seed.privacy().name())
                // Cung quy uoc voi social-service nen CLB tro dung toi nhom chat da seed ben do.
                .setParameter("conversationId", id("conversation", id("club", seed.name()).toString()))
                .executeUpdate();
    }

    private int insertMember(ClubSeed club, MemberSeed member) {
        return entityManager.createNativeQuery("""
                        insert into club_members (membership_id, club_id, user_id, role, status,
                                                  joined_at, responded_by, responded_at,
                                                  created_at, created_by, updated_at, updated_by)
                        select :membershipId, :clubId, :userId, :role, 'ACTIVE',
                               :joinedAt, :ownerId, :joinedAt,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from club_members where membership_id = :membershipId)
                        """)
                .setParameter("membershipId", id("membership", club.name() + "|" + member.email()))
                .setParameter("clubId", id("club", club.name()))
                .setParameter("userId", userId(member.email()))
                .setParameter("role", member.role().name())
                .setParameter("joinedAt", LocalDateTime.now().minusDays(20))
                .setParameter("ownerId", userId(club.ownerEmail()))
                .executeUpdate();
    }

    private int insertActivity(ActivitySeed seed) {
        LocalDateTime startAt = LocalDate.now().plusDays(seed.daysFromNow()).atTime(seed.startTime());
        return entityManager.createNativeQuery("""
                        insert into club_activities (activity_id, club_id, title, description, venue_id,
                                                     start_at, end_at, created_by_user_id,
                                                     created_at, created_by, updated_at, updated_by)
                        select :activityId, :clubId, :title, :description, :venueId,
                               :startAt, :endAt, :creatorId,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from club_activities where activity_id = :activityId)
                        """)
                .setParameter("activityId", id("activity", seed.clubName() + "|" + seed.title()))
                .setParameter("clubId", id("club", seed.clubName()))
                .setParameter("title", seed.title())
                .setParameter("description", seed.description())
                // Cung quy uoc voi venue-service nen sinh hoat tro dung toi san that.
                .setParameter("venueId", id("venue", seed.venueName()))
                .setParameter("startAt", startAt)
                .setParameter("endAt", startAt.plusHours(seed.hours()))
                .setParameter("creatorId", userId(seed.creatorEmail()))
                .executeUpdate();
    }

    private int insertTournament() {
        return entityManager.createNativeQuery("""
                        insert into tournaments (tournament_id, name, description, sport_type, format,
                                                 status, organizer_id, max_participants, entry_fee, prize_pool,
                                                 registration_open_date, registration_close_date,
                                                 start_date, end_date,
                                                 created_at, created_by, updated_at, updated_by)
                        select :tournamentId, :name, :description, 'FOOTBALL', 'SINGLE_ELIMINATION',
                               'REGISTRATION_OPEN', :organizerId, 8, 0, 5000000,
                               :openDate, :closeDate,
                               :startDate, :endDate,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from tournaments where tournament_id = :tournamentId)
                        """)
                .setParameter("tournamentId", id("tournament", TOURNAMENT_NAME))
                .setParameter("name", TOURNAMENT_NAME)
                .setParameter("description",
                        "Giải đấu loại trực tiếp dành cho các đội phong trào khu vực TP.HCM, thi đấu sân 7 người.")
                .setParameter("organizerId", userId(HUY))
                .setParameter("openDate", LocalDate.now().minusDays(5))
                .setParameter("closeDate", LocalDate.now().plusDays(10))
                .setParameter("startDate", LocalDate.now().plusDays(14))
                .setParameter("endDate", LocalDate.now().plusDays(16))
                .executeUpdate();
    }

    private int insertRegistration(RegistrationSeed seed) {
        return entityManager.createNativeQuery("""
                        insert into tournament_registrations (registration_id, tournament_id, player_id,
                                                              team_name, type, status, registered_by,
                                                              registered_at, confirmed_at,
                                                              created_at, created_by, updated_at, updated_by)
                        select :registrationId, :tournamentId, :playerId,
                               :teamName, 'INDIVIDUAL', 'CONFIRMED', :playerId,
                               :registeredAt, :registeredAt,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (
                            select 1 from tournament_registrations where registration_id = :registrationId)
                        """)
                .setParameter("registrationId", id("registration", TOURNAMENT_NAME + "|" + seed.email()))
                .setParameter("tournamentId", id("tournament", TOURNAMENT_NAME))
                .setParameter("playerId", userId(seed.email()))
                .setParameter("teamName", seed.teamName())
                .setParameter("registeredAt", LocalDateTime.now().minusDays(2))
                .executeUpdate();
    }
}
