package iuh.fit.clubservice.infrastructure;

import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
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
    private static final String VAN_AN = "an.nguyenvan@goatsports.vn";
    private static final String MINH_QUAN = "quan.tranminh@goatsports.vn";
    private static final String HOANG_NAM = "nam.lehoang@goatsports.vn";
    private static final String GIA_HUY = "huy.phamgia@goatsports.vn";
    private static final String TUAN_KIET = "kiet.dotuan@goatsports.vn";
    private static final String VAN_MINH = "minh.nguyenvan@goatsports.vn";
    private static final String HOANG_PHUC = "phuc.tranhoang@goatsports.vn";
    private static final String MINH_DUC = "duc.ngominh@goatsports.vn";

    private record MemberSeed(String email, ClubRole role, ClubMemberStatus status,
                              String introMessage, int daysAgo) {}

    private record ClubSeed(String name, String ownerEmail, SportType sportType, String description,
                            String city, String location,
                            ClubPrivacy privacy, ClubApprovalMode approvalMode,
                            List<String> tags, int winCount, int lossCount, int drawCount,
                            List<MemberSeed> members) {}

    private record ActivitySeed(String clubName, String title, String description,
                                String venueName, int daysFromNow, LocalTime startTime, int hours,
                                String creatorEmail) {}

    private record RegistrationSeed(String email, String teamName) {}

    private record CompletedMatchSeed(String key, String opponentEmail, int clubScore,
                                      int opponentScore, int daysAgo) {}

    private static MemberSeed active(String email, ClubRole role, int daysAgo) {
        return new MemberSeed(email, role, ClubMemberStatus.ACTIVE, null, daysAgo);
    }

    private static MemberSeed pending(String email, String introMessage, int daysAgo) {
        return new MemberSeed(email, ClubRole.MEMBER, ClubMemberStatus.PENDING, introMessage, daysAgo);
    }

    private static final List<ClubSeed> CLUBS = List.of(
            new ClubSeed("CLB Bóng Đá Sài Gòn Phủi", HUY, SportType.FOOTBALL,
                    "Đá giao hữu sân 7 tối thứ 3 và thứ 6 hàng tuần tại Bình Thạnh. Ưu tiên anh em đi làm.",
                    "ho-chi-minh", "Quận Bình Thạnh, TP. Hồ Chí Minh",
                    ClubPrivacy.PUBLIC, ClubApprovalMode.MANUAL,
                    List.of("Giao lưu", "Thi đấu", "Cộng đồng"), 12, 3, 3,
                    List.of(active(HUY, ClubRole.OWNER, 180),
                            active(TUNG, ClubRole.ADMIN, 175),
                            active(NHAT_MINH, ClubRole.MEMBER, 160),
                            active(HAI_DANG, ClubRole.MEMBER, 148),
                            active(DUC_ANH, ClubRole.MEMBER, 126),
                            active(VAN_MINH, ClubRole.MEMBER, 92),
                            active(HOANG_PHUC, ClubRole.MEMBER, 64),
                            active(MINH_DUC, ClubRole.MEMBER, 35),
                            pending(VAN_AN, "Mình chơi vị trí tiền vệ, thường rảnh tối thứ 3 và thứ 6. Mong được tham gia tập thử cùng đội.", 1),
                            pending(MINH_QUAN, "Mình đã chơi sân 7 khoảng ba năm và muốn tìm đội sinh hoạt ổn định tại Bình Thạnh.", 1),
                            pending(HOANG_NAM, "Mình là thủ môn phong trào, có thể tham gia đều đặn các buổi tối trong tuần.", 2),
                            pending(GIA_HUY, "Mình ưu tiên tinh thần fair-play, có kinh nghiệm đá giải phong trào và muốn đồng hành lâu dài.", 2),
                            pending(TUAN_KIET, "Mình mới chuyển đến TP.HCM, muốn làm quen đội và tham gia các buổi giao hữu cuối tuần.", 3))),

            new ClubSeed("CLB Cầu Lông Phú Nhuận", KHANH_LINH, SportType.BADMINTON,
                    "Sinh hoạt sáng thứ 7 và chủ nhật, có HLV hướng dẫn cho người mới.",
                    "ho-chi-minh", "Quận Phú Nhuận, TP. Hồ Chí Minh",
                    ClubPrivacy.PUBLIC, ClubApprovalMode.AUTO,
                    List.of("Người mới", "Kỹ thuật", "Cuối tuần"), 9, 3, 2,
                    List.of(active(KHANH_LINH, ClubRole.OWNER, 150),
                            active(THU_TRANG, ClubRole.MEMBER, 122),
                            active(BAO_NGOC, ClubRole.MEMBER, 118),
                            active(VAN_AN, ClubRole.MEMBER, 84),
                            active(HOANG_NAM, ClubRole.MEMBER, 55),
                            active(TUAN_KIET, ClubRole.MEMBER, 28)))
    );

    private static final List<ActivitySeed> ACTIVITIES = List.of(
            new ActivitySeed("CLB Bóng Đá Sài Gòn Phủi", "Đá giao hữu tuần này",
                    "Tập trung trước 30 phút để khởi động. Mang cả áo sáng và áo tối.",
                    "Sân Thể Thao Bình Thạnh", 3, LocalTime.of(19, 0), 2, HUY),
            new ActivitySeed("CLB Bóng Đá Sài Gòn Phủi", "Tập chiến thuật chuyển trạng thái",
                    "Rèn pressing, thoát pressing và chuyển đổi nhanh theo đội hình sân 7.",
                    "Sân Thể Thao Bình Thạnh", 6, LocalTime.of(19, 30), 2, TUNG),
            new ActivitySeed("CLB Bóng Đá Sài Gòn Phủi", "Giao lưu nội bộ cuối tuần",
                    "Chia đội cân bằng và thi đấu ba hiệp. Thành viên đăng ký trước một ngày.",
                    "Sân Thể Thao Bình Thạnh", 9, LocalTime.of(17, 30), 2, HUY),
            new ActivitySeed("CLB Cầu Lông Phú Nhuận", "Buổi tập kỹ thuật đập cầu",
                    "HLV hướng dẫn nhóm mới, ai có vợt riêng thì mang theo.",
                    "Trung Tâm Cầu Lông Phú Nhuận", 5, LocalTime.of(8, 0), 2, KHANH_LINH),
            new ActivitySeed("CLB Cầu Lông Phú Nhuận", "Giao lưu đôi nam nữ",
                    "Ghép cặp luân phiên theo trình độ, ưu tiên thành viên mới tham gia.",
                    "Trung Tâm Cầu Lông Phú Nhuận", 8, LocalTime.of(7, 30), 2, KHANH_LINH),
            new ActivitySeed("CLB Cầu Lông Phú Nhuận", "Kiểm tra trình độ thành viên mới",
                    "Đánh giá kỹ thuật cơ bản để xếp nhóm tập phù hợp cho tháng tiếp theo.",
                    "Trung Tâm Cầu Lông Phú Nhuận", 12, LocalTime.of(8, 30), 2, KHANH_LINH)
    );

    private static final String TOURNAMENT_NAME = "Giải Bóng Đá Phong Trào Sài Gòn Mở Rộng 2026";
    private static final String COMPLETED_TOURNAMENT_NAME = "GOAT Community Cup 2026";
    private static final String BADMINTON_TOURNAMENT_NAME = "GOAT Shuttle League 2026";

    private static final List<RegistrationSeed> REGISTRATIONS = List.of(
            new RegistrationSeed(HUY, "Sài Gòn Phủi"),
            new RegistrationSeed(TUNG, "Thanh Đa FC"),
            new RegistrationSeed(NHAT_MINH, "Quận 7 United"),
            new RegistrationSeed(HAI_DANG, "Hà Nội Trẻ"),
            new RegistrationSeed(DUC_ANH, "Cầu Giấy FC")
    );

    /** 18 fixtures mirror the aggregate 12 wins, 3 draws and 3 losses of the demo football club. */
    private static final List<CompletedMatchSeed> COMPLETED_MATCHES = List.of(
            new CompletedMatchSeed("weekend", TUNG, 2, 1, 4),
            new CompletedMatchSeed("runners", NHAT_MINH, 0, 0, 11),
            new CompletedMatchSeed("dayend", HAI_DANG, 1, 3, 18),
            new CompletedMatchSeed("weekend-05", TUNG, 3, 1, 25),
            new CompletedMatchSeed("runners-05", NHAT_MINH, 2, 0, 32),
            new CompletedMatchSeed("dayend-05", HAI_DANG, 2, 1, 39),
            new CompletedMatchSeed("weekend-04", TUNG, 1, 1, 46),
            new CompletedMatchSeed("runners-04", NHAT_MINH, 4, 2, 53),
            new CompletedMatchSeed("dayend-04", HAI_DANG, 3, 0, 60),
            new CompletedMatchSeed("weekend-03", TUNG, 0, 2, 67),
            new CompletedMatchSeed("runners-03", NHAT_MINH, 2, 1, 74),
            new CompletedMatchSeed("dayend-03", HAI_DANG, 1, 0, 81),
            new CompletedMatchSeed("weekend-02", TUNG, 2, 2, 88),
            new CompletedMatchSeed("runners-02", NHAT_MINH, 3, 1, 95),
            new CompletedMatchSeed("dayend-02", HAI_DANG, 2, 0, 102),
            new CompletedMatchSeed("weekend-01", TUNG, 1, 2, 109),
            new CompletedMatchSeed("runners-01", NHAT_MINH, 4, 1, 116),
            new CompletedMatchSeed("dayend-01", HAI_DANG, 3, 2, 123)
    );

    /** 14 fixtures mirror the aggregate 9 wins, 2 draws and 3 losses of the demo badminton club. */
    private static final List<CompletedMatchSeed> BADMINTON_MATCHES = List.of(
            new CompletedMatchSeed("smash-05", THU_TRANG, 21, 15, 5),
            new CompletedMatchSeed("shuttle-05", BAO_NGOC, 21, 18, 12),
            new CompletedMatchSeed("rackets-04", VAN_AN, 15, 21, 19),
            new CompletedMatchSeed("smash-04", THU_TRANG, 21, 12, 26),
            new CompletedMatchSeed("shuttle-04", BAO_NGOC, 20, 20, 33),
            new CompletedMatchSeed("rackets-03", VAN_AN, 21, 19, 40),
            new CompletedMatchSeed("smash-03", THU_TRANG, 17, 21, 47),
            new CompletedMatchSeed("shuttle-03", BAO_NGOC, 22, 20, 54),
            new CompletedMatchSeed("rackets-02", VAN_AN, 21, 14, 61),
            new CompletedMatchSeed("smash-02", THU_TRANG, 19, 19, 68),
            new CompletedMatchSeed("shuttle-02", BAO_NGOC, 21, 17, 75),
            new CompletedMatchSeed("rackets-01", VAN_AN, 18, 21, 82),
            new CompletedMatchSeed("smash-01", THU_TRANG, 21, 16, 89),
            new CompletedMatchSeed("shuttle-01", BAO_NGOC, 21, 11, 96)
    );

    @Override
    @Transactional
    public void run(String... args) {
        int clubs = 0;
        int members = 0;
        int tags = 0;
        for (ClubSeed club : CLUBS) {
            clubs += insertClub(club);
            tags += insertTags(club);
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
        tournaments += insertCompletedTournament();
        registrations += insertCompletedClubRegistration();
        registrations += insertCompletedOpponentRegistration(TUNG, "Weekend FC");
        registrations += insertCompletedOpponentRegistration(NHAT_MINH, "Saigon Runners");
        registrations += insertCompletedOpponentRegistration(HAI_DANG, "Dayend");
        int fixtures = 0;
        for (int index = 0; index < COMPLETED_MATCHES.size(); index++) {
            CompletedMatchSeed match = COMPLETED_MATCHES.get(index);
            fixtures += insertCompletedFixture(match.key(), match.opponentEmail(), "Vòng bảng",
                    index + 1, match.clubScore(), match.opponentScore(), match.daysAgo());
        }
        tournaments += insertCompletedBadmintonTournament();
        registrations += insertCompletedBadmintonClubRegistration();
        registrations += insertCompletedBadmintonOpponentRegistration(THU_TRANG, "Smash Masters");
        registrations += insertCompletedBadmintonOpponentRegistration(BAO_NGOC, "Saigon Shuttle");
        registrations += insertCompletedBadmintonOpponentRegistration(VAN_AN, "Phú Nhuận Rackets");
        for (int index = 0; index < BADMINTON_MATCHES.size(); index++) {
            CompletedMatchSeed match = BADMINTON_MATCHES.get(index);
            fixtures += insertCompletedBadmintonFixture(match.key(), match.opponentEmail(), "Vòng bảng",
                    index + 1, match.clubScore(), match.opponentScore(), match.daysAgo());
        }
        log.info("Dữ liệu mẫu: {} CLB, {} thẻ, {} thành viên/yêu cầu, {} hoạt động, {} giải đấu, {} đăng ký, {} trận đấu"
                        + " (bỏ qua bản ghi đã có)",
                clubs, tags, members, activities, tournaments, registrations, fixtures);
    }

    private int insertClub(ClubSeed seed) {
        int matches = seed.winCount() + seed.lossCount() + seed.drawCount();
        double winRate = matches == 0 ? 0D : (double) seed.winCount() / matches;
        int inserted = entityManager.createNativeQuery("""
                        insert into clubs (club_id, name, description, sport_type, owner_id, city, location,
                                           approval_mode, privacy, conversation_id, active,
                                           win_count, loss_count, draw_count, match_count, win_rate,
                                           created_at, created_by, updated_at, updated_by)
                        select :clubId, :name, :description, :sportType, :ownerId, :city, :location,
                               :approvalMode, :privacy, :conversationId, true,
                               :winCount, :lossCount, :drawCount, :matchCount, :winRate,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from clubs where club_id = :clubId)
                        """)
                .setParameter("clubId", id("club", seed.name()))
                .setParameter("name", seed.name())
                .setParameter("description", seed.description())
                .setParameter("sportType", seed.sportType().name())
                .setParameter("city", seed.city())
                .setParameter("location", seed.location())
                .setParameter("ownerId", userId(seed.ownerEmail()))
                .setParameter("approvalMode", seed.approvalMode().name())
                .setParameter("privacy", seed.privacy().name())
                .setParameter("winCount", seed.winCount())
                .setParameter("lossCount", seed.lossCount())
                .setParameter("drawCount", seed.drawCount())
                .setParameter("matchCount", matches)
                .setParameter("winRate", winRate)
                // Cung quy uoc voi social-service nen CLB tro dung toi nhom chat da seed ben do.
                .setParameter("conversationId", id("conversation", id("club", seed.name()).toString()))
                .executeUpdate();

        entityManager.createNativeQuery("""
                        update clubs
                        set win_count = :winCount, loss_count = :lossCount, draw_count = :drawCount,
                            match_count = :matchCount, win_rate = :winRate,
                            conversation_id = coalesce(conversation_id, :conversationId),
                            updated_at = now(), updated_by = 'demo-seed'
                        where club_id = :clubId and created_by = 'demo-seed'
                        """)
                .setParameter("clubId", id("club", seed.name()))
                .setParameter("winCount", seed.winCount())
                .setParameter("lossCount", seed.lossCount())
                .setParameter("drawCount", seed.drawCount())
                .setParameter("matchCount", matches)
                .setParameter("winRate", winRate)
                .setParameter("conversationId", id("conversation", id("club", seed.name()).toString()))
                .executeUpdate();
        return inserted;
    }

    private int insertTags(ClubSeed seed) {
        int inserted = 0;
        for (int index = 0; index < seed.tags().size(); index++) {
            inserted += entityManager.createNativeQuery("""
                            insert into club_tags (club_id, display_order, tag)
                            select :clubId, :displayOrder, :tag
                            where exists (select 1 from clubs where club_id = :clubId)
                              and not exists (
                                  select 1 from club_tags
                                  where club_id = :clubId and tag = :tag)
                            on conflict do nothing
                            """)
                    .setParameter("clubId", id("club", seed.name()))
                    .setParameter("displayOrder", index)
                    .setParameter("tag", seed.tags().get(index))
                    .executeUpdate();
        }
        return inserted;
    }

    private int insertMember(ClubSeed club, MemberSeed member) {
        if (member.status() == ClubMemberStatus.PENDING) {
            return entityManager.createNativeQuery("""
                            insert into club_members (membership_id, club_id, user_id, role, status,
                                                      intro_message, applied_at,
                                                      created_at, created_by, updated_at, updated_by)
                            select :membershipId, :clubId, :userId, :role, 'PENDING',
                                   :introMessage, :appliedAt,
                                   now(), 'demo-seed', now(), 'demo-seed'
                            where not exists (
                                select 1 from club_members
                                where membership_id = :membershipId
                                   or (club_id = :clubId and user_id = :userId))
                            """)
                    .setParameter("membershipId", id("membership", club.name() + "|" + member.email()))
                    .setParameter("clubId", id("club", club.name()))
                    .setParameter("userId", userId(member.email()))
                    .setParameter("role", member.role().name())
                    .setParameter("introMessage", member.introMessage())
                    .setParameter("appliedAt", LocalDateTime.now().minusDays(member.daysAgo()))
                    .executeUpdate();
        }

        return entityManager.createNativeQuery("""
                        insert into club_members (membership_id, club_id, user_id, role, status,
                                                  joined_at, responded_by, responded_at,
                                                  created_at, created_by, updated_at, updated_by)
                        select :membershipId, :clubId, :userId, :role, 'ACTIVE',
                               :joinedAt, :ownerId, :joinedAt,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (
                            select 1 from club_members
                            where membership_id = :membershipId
                               or (club_id = :clubId and user_id = :userId))
                        """)
                .setParameter("membershipId", id("membership", club.name() + "|" + member.email()))
                .setParameter("clubId", id("club", club.name()))
                .setParameter("userId", userId(member.email()))
                .setParameter("role", member.role().name())
                .setParameter("joinedAt", LocalDateTime.now().minusDays(member.daysAgo()))
                .setParameter("ownerId", userId(club.ownerEmail()))
                .executeUpdate();
    }

    private int insertActivity(ActivitySeed seed) {
        LocalDateTime startAt = LocalDate.now().plusDays(seed.daysFromNow()).atTime(seed.startTime());
        int inserted = entityManager.createNativeQuery("""
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

        entityManager.createNativeQuery("""
                        update club_activities
                        set description = :description, start_at = :startAt, end_at = :endAt,
                            updated_at = now(), updated_by = 'demo-seed'
                        where activity_id = :activityId and created_by = 'demo-seed'
                        """)
                .setParameter("activityId", id("activity", seed.clubName() + "|" + seed.title()))
                .setParameter("description", seed.description())
                .setParameter("startAt", startAt)
                .setParameter("endAt", startAt.plusHours(seed.hours()))
                .executeUpdate();
        return inserted;
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

    private int insertCompletedTournament() {
        return entityManager.createNativeQuery("""
                        insert into tournaments (tournament_id, name, description, sport_type, format,
                                                 status, organizer_id, max_participants, entry_fee, prize_pool,
                                                 registration_open_date, registration_close_date,
                                                 start_date, end_date,
                                                 created_at, created_by, updated_at, updated_by)
                        select :tournamentId, :name, :description, 'FOOTBALL', 'SINGLE_ELIMINATION',
                               'COMPLETED', :organizerId, 8, 0, 3000000,
                               :openDate, :closeDate, :startDate, :endDate,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from tournaments where tournament_id = :tournamentId)
                        """)
                .setParameter("tournamentId", id("tournament", COMPLETED_TOURNAMENT_NAME))
                .setParameter("name", COMPLETED_TOURNAMENT_NAME)
                .setParameter("description", "Giải bóng đá cộng đồng dành cho các câu lạc bộ phong trào GOAT Sports.")
                .setParameter("organizerId", userId(HUY))
                .setParameter("openDate", LocalDate.now().minusDays(165))
                .setParameter("closeDate", LocalDate.now().minusDays(145))
                .setParameter("startDate", LocalDate.now().minusDays(130))
                .setParameter("endDate", LocalDate.now().minusDays(4))
                .executeUpdate();
    }

    private int insertCompletedClubRegistration() {
        String clubName = CLUBS.get(0).name();
        return entityManager.createNativeQuery("""
                        insert into tournament_registrations (registration_id, tournament_id, player_id, club_id,
                                                              team_name, type, status, registered_by,
                                                              registered_at, confirmed_at,
                                                              created_at, created_by, updated_at, updated_by)
                        select :registrationId, :tournamentId, :playerId, :clubId,
                               :teamName, 'CLUB', 'CONFIRMED', :playerId,
                               :registeredAt, :registeredAt,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from tournament_registrations where registration_id = :registrationId)
                        """)
                .setParameter("registrationId", completedRegistrationId("club"))
                .setParameter("tournamentId", id("tournament", COMPLETED_TOURNAMENT_NAME))
                .setParameter("playerId", userId(HUY))
                .setParameter("clubId", id("club", clubName))
                .setParameter("teamName", clubName)
                .setParameter("registeredAt", LocalDateTime.now().minusDays(45))
                .executeUpdate();
    }

    private int insertCompletedOpponentRegistration(String email, String teamName) {
        return entityManager.createNativeQuery("""
                        insert into tournament_registrations (registration_id, tournament_id, player_id,
                                                              team_name, type, status, registered_by,
                                                              registered_at, confirmed_at,
                                                              created_at, created_by, updated_at, updated_by)
                        select :registrationId, :tournamentId, :playerId,
                               :teamName, 'INDIVIDUAL', 'CONFIRMED', :playerId,
                               :registeredAt, :registeredAt,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from tournament_registrations where registration_id = :registrationId)
                        """)
                .setParameter("registrationId", completedRegistrationId(email))
                .setParameter("tournamentId", id("tournament", COMPLETED_TOURNAMENT_NAME))
                .setParameter("playerId", userId(email))
                .setParameter("teamName", teamName)
                .setParameter("registeredAt", LocalDateTime.now().minusDays(45))
                .executeUpdate();
    }

    private int insertCompletedFixture(String key, String opponentEmail, String roundName, int matchNumber,
                                       int clubScore, int opponentScore, int daysAgo) {
        UUID fixtureId = id("fixture", COMPLETED_TOURNAMENT_NAME + "|" + key);
        LocalDateTime scheduledAt = LocalDateTime.now().minusDays(daysAgo).withHour(19).withMinute(0)
                .withSecond(0).withNano(0);
        int inserted = entityManager.createNativeQuery("""
                        insert into tournament_fixtures (fixture_id, tournament_id, round_name, round_number,
                                                         match_number, registration1_id, registration2_id,
                                                         score1, score2, winner_registration_id, status,
                                                         created_at, created_by, updated_at, updated_by)
                        select :fixtureId, :tournamentId, :roundName, 1,
                               :matchNumber, :registration1Id, :registration2Id,
                               :score1, :score2,
                               case when :score1 = :score2 then null
                                    when :score1 > :score2 then :registration1Id else :registration2Id end,
                               'COMPLETED',
                               :scheduledAt, 'demo-seed', :scheduledAt, 'demo-seed'
                        where not exists (select 1 from tournament_fixtures where fixture_id = :fixtureId)
                        """)
                .setParameter("fixtureId", fixtureId)
                .setParameter("tournamentId", id("tournament", COMPLETED_TOURNAMENT_NAME))
                .setParameter("roundName", roundName)
                .setParameter("matchNumber", matchNumber)
                .setParameter("registration1Id", completedRegistrationId("club"))
                .setParameter("registration2Id", completedRegistrationId(opponentEmail))
                .setParameter("score1", clubScore)
                .setParameter("score2", opponentScore)
                .setParameter("scheduledAt", scheduledAt)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        update tournament_fixtures
                        set created_at = :scheduledAt, updated_at = :scheduledAt
                        where fixture_id = :fixtureId and created_by = 'demo-seed'
                        """)
                .setParameter("fixtureId", fixtureId)
                .setParameter("scheduledAt", scheduledAt)
                .executeUpdate();
        return inserted;
    }

    private UUID completedRegistrationId(String key) {
        return id("registration", COMPLETED_TOURNAMENT_NAME + "|" + key);
    }

    private int insertCompletedBadmintonTournament() {
        return entityManager.createNativeQuery("""
                        insert into tournaments (tournament_id, name, description, sport_type, format,
                                                 status, organizer_id, max_participants, entry_fee, prize_pool,
                                                 registration_open_date, registration_close_date,
                                                 start_date, end_date,
                                                 created_at, created_by, updated_at, updated_by)
                        select :tournamentId, :name, :description, 'BADMINTON', 'ROUND_ROBIN',
                               'COMPLETED', :organizerId, 8, 0, 1500000,
                               :openDate, :closeDate, :startDate, :endDate,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from tournaments where tournament_id = :tournamentId)
                        """)
                .setParameter("tournamentId", id("tournament", BADMINTON_TOURNAMENT_NAME))
                .setParameter("name", BADMINTON_TOURNAMENT_NAME)
                .setParameter("description", "Giải cầu lông cộng đồng theo thể thức vòng tròn dành cho các câu lạc bộ GOAT Sports.")
                .setParameter("organizerId", userId(KHANH_LINH))
                .setParameter("openDate", LocalDate.now().minusDays(125))
                .setParameter("closeDate", LocalDate.now().minusDays(112))
                .setParameter("startDate", LocalDate.now().minusDays(100))
                .setParameter("endDate", LocalDate.now().minusDays(5))
                .executeUpdate();
    }

    private int insertCompletedBadmintonClubRegistration() {
        String clubName = CLUBS.get(1).name();
        return entityManager.createNativeQuery("""
                        insert into tournament_registrations (registration_id, tournament_id, player_id, club_id,
                                                              team_name, type, status, registered_by,
                                                              registered_at, confirmed_at,
                                                              created_at, created_by, updated_at, updated_by)
                        select :registrationId, :tournamentId, :playerId, :clubId,
                               :teamName, 'CLUB', 'CONFIRMED', :playerId,
                               :registeredAt, :registeredAt,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from tournament_registrations where registration_id = :registrationId)
                        """)
                .setParameter("registrationId", completedBadmintonRegistrationId("club"))
                .setParameter("tournamentId", id("tournament", BADMINTON_TOURNAMENT_NAME))
                .setParameter("playerId", userId(KHANH_LINH))
                .setParameter("clubId", id("club", clubName))
                .setParameter("teamName", clubName)
                .setParameter("registeredAt", LocalDateTime.now().minusDays(105))
                .executeUpdate();
    }

    private int insertCompletedBadmintonOpponentRegistration(String email, String teamName) {
        return entityManager.createNativeQuery("""
                        insert into tournament_registrations (registration_id, tournament_id, player_id,
                                                              team_name, type, status, registered_by,
                                                              registered_at, confirmed_at,
                                                              created_at, created_by, updated_at, updated_by)
                        select :registrationId, :tournamentId, :playerId,
                               :teamName, 'INDIVIDUAL', 'CONFIRMED', :playerId,
                               :registeredAt, :registeredAt,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from tournament_registrations where registration_id = :registrationId)
                        """)
                .setParameter("registrationId", completedBadmintonRegistrationId(email))
                .setParameter("tournamentId", id("tournament", BADMINTON_TOURNAMENT_NAME))
                .setParameter("playerId", userId(email))
                .setParameter("teamName", teamName)
                .setParameter("registeredAt", LocalDateTime.now().minusDays(105))
                .executeUpdate();
    }

    private int insertCompletedBadmintonFixture(String key, String opponentEmail, String roundName,
                                                 int matchNumber, int clubScore, int opponentScore, int daysAgo) {
        UUID fixtureId = id("fixture", BADMINTON_TOURNAMENT_NAME + "|" + key);
        LocalDateTime scheduledAt = LocalDateTime.now().minusDays(daysAgo).withHour(8).withMinute(0)
                .withSecond(0).withNano(0);
        int inserted = entityManager.createNativeQuery("""
                        insert into tournament_fixtures (fixture_id, tournament_id, round_name, round_number,
                                                         match_number, registration1_id, registration2_id,
                                                         score1, score2, winner_registration_id, status,
                                                         created_at, created_by, updated_at, updated_by)
                        select :fixtureId, :tournamentId, :roundName, 1,
                               :matchNumber, :registration1Id, :registration2Id,
                               :score1, :score2,
                               case when :score1 = :score2 then null
                                    when :score1 > :score2 then :registration1Id else :registration2Id end,
                               'COMPLETED',
                               :scheduledAt, 'demo-seed', :scheduledAt, 'demo-seed'
                        where not exists (select 1 from tournament_fixtures where fixture_id = :fixtureId)
                        """)
                .setParameter("fixtureId", fixtureId)
                .setParameter("tournamentId", id("tournament", BADMINTON_TOURNAMENT_NAME))
                .setParameter("roundName", roundName)
                .setParameter("matchNumber", matchNumber)
                .setParameter("registration1Id", completedBadmintonRegistrationId("club"))
                .setParameter("registration2Id", completedBadmintonRegistrationId(opponentEmail))
                .setParameter("score1", clubScore)
                .setParameter("score2", opponentScore)
                .setParameter("scheduledAt", scheduledAt)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        update tournament_fixtures
                        set created_at = :scheduledAt, updated_at = :scheduledAt
                        where fixture_id = :fixtureId and created_by = 'demo-seed'
                        """)
                .setParameter("fixtureId", fixtureId)
                .setParameter("scheduledAt", scheduledAt)
                .executeUpdate();
        return inserted;
    }

    private UUID completedBadmintonRegistrationId(String key) {
        return id("registration", BADMINTON_TOURNAMENT_NAME + "|" + key);
    }
}
