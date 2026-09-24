package iuh.fit.clubservice.infrastructure;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

/**
 * Giải đấu mẫu đầy đủ để xem giao diện tab Giải đấu: nhánh đấu, bảng xếp hạng, lịch theo ngày và đội hình.
 * Bật cùng cờ {@code app.demo-data.enabled=true}, chạy sau {@link DemoDataSeeder}.
 *
 * <p>Id sinh theo cùng quy ước với auth-service và venue-service ({@code goatsports:<loại>:<khóa>}), nên chủ sân,
 * cơ sở, sân và đội trưởng đều trỏ đúng dữ liệu thật của các service kia. venue-service seed lịch giữ sân
 * (court_holds) cho các giải đang chạy theo cùng tên giải — đổi tên ở đây thì đổi cả bên đó.
 *
 * <p>Kết quả trận được "đá" theo thứ tự lịch: đội thắng vòng trước tự vào vòng sau, nên nhánh đấu luôn nhất quán.
 */
@Slf4j
@Component
@Order(21)
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class TournamentShowcaseSeeder implements CommandLineRunner {

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------- người chơi mẫu (tài khoản thật ở auth-service)

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

    /**
     * Họ tên đội trưởng (trùng dữ liệu mẫu của auth-service). Lưu kèm trong đội hình để khách chưa đăng nhập vẫn
     * thấy tên — tra tên người dùng ở auth-service cần đăng nhập.
     */
    private static final Map<String, String> CAPTAIN_NAMES = Map.ofEntries(
            Map.entry(HUY, "Đặng Gia Huy"), Map.entry(TUNG, "Vũ Thanh Tùng"),
            Map.entry(NHAT_MINH, "Hoàng Nhật Minh"), Map.entry(KHANH_LINH, "Bùi Khánh Linh"),
            Map.entry(DUC_ANH, "Ngô Đức Anh"), Map.entry(HAI_DANG, "Trịnh Hải Đăng"),
            Map.entry(THU_TRANG, "Lý Thu Trang"), Map.entry(BAO_NGOC, "Cao Bảo Ngọc"),
            Map.entry(VAN_AN, "Nguyễn Văn An"), Map.entry(MINH_QUAN, "Trần Minh Quân"),
            Map.entry(HOANG_NAM, "Lê Hoàng Nam"), Map.entry(GIA_HUY, "Phạm Gia Huy"),
            Map.entry(TUAN_KIET, "Đỗ Tuấn Kiệt"), Map.entry(VAN_MINH, "Nguyễn Văn Minh"),
            Map.entry(HOANG_PHUC, "Trần Hoàng Phúc"), Map.entry(MINH_DUC, "Ngô Minh Đức"));

    /** Chủ sân (VENUE_OWNER) sở hữu cơ sở tương ứng trong venue-service. */
    private static final String OWNER_BINH_THANH = "bao.tranquoc@goatsports.vn";
    private static final String OWNER_PHU_NHUAN = "huong.nguyenthimai@goatsports.vn";

    private static final String VENUE_BINH_THANH = "Sân Thể Thao Bình Thạnh";
    private static final String VENUE_PHU_NHUAN = "Trung Tâm Cầu Lông Phú Nhuận";
    private static final String VENUE_TAN_BINH = "Nhà Thi Đấu Tân Bình";

    /** Tên giải cũng là khóa sinh id — venue-service dùng cùng tên để giữ sân. */
    static final String FOOTBALL_7_CUP = "Cúp Sân 7 Bình Thạnh Mùa Thu 2026";
    static final String FUTSAL_LEAGUE = "Giải Futsal 5 Người Bình Thạnh 2026";
    static final String BASKETBALL_OPEN = "Giải Bóng Rổ 5x5 Tân Bình Open 2026";
    static final String VOLLEYBALL_CUP = "Giải Bóng Chuyền Tân Bình Mở Rộng 2026";
    static final String BADMINTON_DOUBLES = "Giải Cầu Lông Đôi Phú Nhuận 2026";

    // ---------------------------------------------------------------- mô tả dữ liệu

    /** Trạng thái đăng ký của đội; đội đang lập có một số người chưa nhận lời. */
    private enum TeamState { CONFIRMED, PENDING_PAYMENT, PENDING_MEMBERS }

    private record Team(String name, String captainEmail, TeamState state) {
        Team(String name, String captainEmail) {
            this(name, captainEmail, TeamState.CONFIRMED);
        }
    }

    /**
     * Một giải mẫu. Ngày tính lệch so với hôm nay. {@code roundDays}: ngày thi đấu của từng vòng (loại trực tiếp)
     * hoặc từng lượt (vòng tròn). {@code results}: tỷ số theo thứ tự lịch, trận chưa có trong danh sách là chưa đá.
     * {@code shirts}: số áo theo thứ tự vị trí (thủ môn/hậu vệ trước) — giao diện xếp sơ đồ theo số áo tăng dần.
     */
    private record Showcase(String name, String description, String sport, String playFormat, String format,
                            String status, String organizerEmail, String venueName, List<String> courts,
                            LocalTime dailyStart, LocalTime dailyEnd, int matchMinutes, int maxParticipants,
                            double fee, double prize, int openDay, int closeDay, int startDay, int endDay,
                            int onField, int captainSlot, List<Integer> shirts, List<Team> teams,
                            List<Integer> roundDays, List<int[]> results, List<String> rules) {}

    private static int[] s(int home, int away) {
        return new int[]{home, away};
    }

    private static final List<Showcase> SHOWCASES = List.of(
            new Showcase(FOOTBALL_7_CUP,
                    "Giải sân 7 phong trào loại trực tiếp dành cho 8 đội khu vực TP.HCM. Thi đấu buổi tối trên hai sân "
                            + "cỏ nhân tạo có đèn, mỗi trận 60 phút, hòa thì đá luân lưu (ghi tỷ số sau luân lưu).",
                    "FOOTBALL", "FOOTBALL_7", "SINGLE_ELIMINATION", "IN_PROGRESS", OWNER_BINH_THANH,
                    VENUE_BINH_THANH, List.of("Sân số 1 (7 người)", "Sân số 2 (7 người)"),
                    LocalTime.of(17, 0), LocalTime.of(22, 0), 60, 8, 1_200_000, 10_000_000,
                    -25, -6, -4, 3, 7, 5, List.of(1, 2, 4, 5, 6, 8, 10, 12, 14, 17),
                    List.of(new Team("Sài Gòn Phủi", HUY), new Team("Thanh Đa FC", TUNG),
                            new Team("Quận 7 United", NHAT_MINH), new Team("Hà Nội Trẻ", HAI_DANG),
                            new Team("Cầu Giấy FC", DUC_ANH), new Team("Bình Thạnh Warriors", VAN_MINH),
                            new Team("Gò Vấp Lions", HOANG_PHUC), new Team("Tân Phú Rangers", MINH_DUC)),
                    List.of(-4, 0, 3),
                    List.of(s(3, 1), s(2, 4), s(1, 0), s(2, 3)),
                    List.of("Mỗi đội đăng ký tối đa 14 cầu thủ, thay người không giới hạn.",
                            "Thẻ đỏ bị treo giò trận kế tiếp.", "Có mặt trước giờ thi đấu 20 phút để kiểm tra danh sách.")),

            new Showcase(FUTSAL_LEAGUE,
                    "Giải futsal vòng tròn một lượt cho 6 đội. Thắng 3 điểm, hòa 1 điểm; bằng điểm xét hiệu số rồi bàn thắng.",
                    "FOOTBALL", "FOOTBALL_5", "ROUND_ROBIN", "IN_PROGRESS", OWNER_BINH_THANH,
                    VENUE_BINH_THANH, List.of("Sân số 3 (5 người)"),
                    LocalTime.of(18, 0), LocalTime.of(21, 0), 50, 6, 600_000, 5_000_000,
                    -30, -12, -8, 1, 5, 3, List.of(1, 3, 5, 8, 9, 11, 14, 15),
                    List.of(new Team("Phú Nhuận Futsal", VAN_AN), new Team("Tân Bình Stars", MINH_QUAN),
                            new Team("Quận 3 Legends", HOANG_NAM), new Team("Bình Tân Eagles", GIA_HUY),
                            new Team("Thủ Đức Dragons", TUAN_KIET), new Team("Nhà Bè Sharks", KHANH_LINH)),
                    List.of(-8, -6, -4, -2, 1),
                    List.of(s(4, 2), s(1, 1), s(3, 0),
                            s(2, 3), s(5, 1), s(2, 2),
                            s(1, 0), s(3, 3), s(0, 2),
                            s(4, 1), s(2, 1), s(1, 2)),
                    List.of("Mỗi hiệp 20 phút, nghỉ giữa hiệp 5 phút.", "Đội hình thi đấu 5 người, tối đa 10 người đăng ký.")),

            new Showcase(BASKETBALL_OPEN,
                    "Giải bóng rổ 5x5 loại trực tiếp 4 đội tại nhà thi đấu Tân Bình. Luật FIBA rút gọn, 4 hiệp x 10 phút.",
                    "BASKETBALL", "BASKETBALL_5X5", "SINGLE_ELIMINATION", "COMPLETED", OWNER_BINH_THANH,
                    VENUE_TAN_BINH, List.of("Sân bóng rổ 1", "Sân bóng rổ 2"),
                    LocalTime.of(18, 0), LocalTime.of(22, 0), 90, 4, 800_000, 6_000_000,
                    -45, -25, -20, -18, 5, 4, List.of(4, 7, 10, 11, 23, 5, 8, 15),
                    List.of(new Team("Tân Bình Ballers", BAO_NGOC), new Team("Saigon Hoopers", THU_TRANG),
                            new Team("District 1 Kings", TUNG), new Team("Hanoi Buffaloes", DUC_ANH)),
                    List.of(-20, -18),
                    List.of(s(78, 65), s(71, 74), s(82, 79)),
                    List.of("Mỗi đội 5 người trên sân, tối đa 12 người đăng ký.")),

            new Showcase(VOLLEYBALL_CUP,
                    "Giải bóng chuyền 6 người vòng tròn, đang mở đăng ký. Đội đủ 6 người nhận lời mới giữ chỗ chính thức.",
                    "VOLLEYBALL", "VOLLEYBALL_6", "ROUND_ROBIN", "PUBLISHED", OWNER_BINH_THANH,
                    VENUE_TAN_BINH, List.of("Sân bóng chuyền 1"),
                    LocalTime.of(17, 0), LocalTime.of(21, 0), 60, 8, 500_000, 4_000_000,
                    -3, 7, 10, 12, 6, 1, List.of(1, 3, 5, 7, 9, 11, 2, 4),
                    List.of(new Team("Tân Bình Spikers", GIA_HUY),
                            new Team("Sài Gòn Phủi Volley", HUY, TeamState.PENDING_PAYMENT),
                            new Team("Phú Nhuận Blockers", THU_TRANG, TeamState.PENDING_MEMBERS)),
                    List.of(), List.of(),
                    List.of("Đấu 3 set thắng 2, set quyết định tính đến 15 điểm.")),

            new Showcase(BADMINTON_DOUBLES,
                    "Giải cầu lông đôi loại trực tiếp 8 cặp tại Phú Nhuận. Một set 21 điểm mỗi trận (ghi tỷ số set).",
                    "BADMINTON", "BADMINTON_DOUBLES", "SINGLE_ELIMINATION", "COMPLETED", OWNER_PHU_NHUAN,
                    VENUE_PHU_NHUAN, List.of("Sân A", "Sân B"),
                    LocalTime.of(8, 0), LocalTime.of(12, 0), 40, 8, 200_000, 3_000_000,
                    -50, -35, -31, -29, 2, 0, List.of(1, 2),
                    List.of(new Team("Smash Brothers", KHANH_LINH), new Team("Phú Nhuận Aces", BAO_NGOC),
                            new Team("Lông Vũ Xanh", VAN_AN), new Team("Cầu Bay", HOANG_NAM),
                            new Team("Saigon Shuttle", THU_TRANG), new Team("Net Kings", MINH_QUAN),
                            new Team("Racket Duo", TUAN_KIET), new Team("Drop Shot", GIA_HUY)),
                    List.of(-31, -30, -29),
                    List.of(s(21, 15), s(18, 21), s(21, 19), s(21, 12),
                            s(21, 17), s(19, 21),
                            s(21, 18)),
                    List.of("Giao cầu theo luật BWF.", "Vận động viên có mặt trước 15 phút."))
    );

    private static final List<String> FAMILY = List.of("Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan",
            "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý");
    private static final List<String> MIDDLE = List.of("Văn", "Minh", "Hoàng", "Quốc", "Thành", "Gia", "Đức",
            "Anh", "Hữu", "Tuấn", "Công", "Nhật");
    private static final List<String> GIVEN = List.of("Khoa", "Long", "Phúc", "Tài", "Hiếu", "Nam", "Trung",
            "Kiên", "Dũng", "Hải", "Sơn", "Thắng", "Lộc", "Vinh", "Bảo", "Khánh", "Tâm", "Toàn", "Quân", "Hưng",
            "Nhân", "Thịnh", "Đạt", "Tú", "Hào", "Lâm", "Phong", "Duy", "Khải", "Tiến", "Luân", "Việt");

    // ---------------------------------------------------------------- chạy

    @Override
    @Transactional
    public void run(String... args) {
        int tournaments = 0;
        int rows = 0;
        for (Showcase showcase : SHOWCASES) {
            tournaments += insertTournament(showcase);
            List<UUID> registrations = new ArrayList<>();
            for (int index = 0; index < showcase.teams().size(); index++) {
                registrations.add(registrationId(showcase, index));
                rows += insertTeam(showcase, index);
            }
            if (!showcase.roundDays().isEmpty()) {
                rows += "ROUND_ROBIN".equals(showcase.format())
                        ? seedRoundRobin(showcase, registrations)
                        : seedElimination(showcase, registrations);
            }
        }
        // Giải mẫu cũ tạo trước khi có cột điều lệ: đưa về danh sách rỗng thay vì null.
        rows += entityManager.createNativeQuery("""
                        update tournaments set rules = '[]'::jsonb where rules is null and created_by = 'demo-seed'
                        """).executeUpdate();
        log.info("Giải đấu trưng bày: {} giải mới, {} bản ghi đội/đội hình/trận/lịch sân (bỏ qua bản ghi đã có)",
                tournaments, rows);
    }

    // ---------------------------------------------------------------- giải + đội

    private int insertTournament(Showcase showcase) {
        LocalDate today = LocalDate.now();
        return entityManager.createNativeQuery("""
                        insert into tournaments (tournament_id, name, description, sport_type, format, status,
                                                 organizer_id, max_participants, entry_fee, prize_pool,
                                                 registration_open_date, registration_close_date, start_date, end_date,
                                                 rules, venue_id, court_ids, daily_start_time, daily_end_time,
                                                 match_duration_minutes, play_format,
                                                 created_at, created_by, updated_at, updated_by)
                        select :id, :name, :description, :sport, :format, :status,
                               :organizerId, :maxParticipants, :fee, :prize,
                               :openDate, :closeDate, :startDate, :endDate,
                               cast(:rules as jsonb), :venueId, cast(:courtIds as jsonb), :dailyStart, :dailyEnd,
                               :matchMinutes, :playFormat,
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from tournaments where tournament_id = :id)
                        """)
                .setParameter("id", tournamentId(showcase))
                .setParameter("name", showcase.name())
                .setParameter("description", showcase.description())
                .setParameter("sport", showcase.sport())
                .setParameter("format", showcase.format())
                .setParameter("status", showcase.status())
                .setParameter("organizerId", userId(showcase.organizerEmail()))
                .setParameter("maxParticipants", showcase.maxParticipants())
                .setParameter("fee", showcase.fee())
                .setParameter("prize", showcase.prize())
                .setParameter("openDate", today.plusDays(showcase.openDay()))
                .setParameter("closeDate", today.plusDays(showcase.closeDay()))
                .setParameter("startDate", today.plusDays(showcase.startDay()))
                .setParameter("endDate", today.plusDays(showcase.endDay()))
                .setParameter("rules", jsonArray(showcase.rules()))
                .setParameter("venueId", id("venue", showcase.venueName()))
                .setParameter("courtIds", jsonArray(showcase.courts().stream()
                        .map(court -> courtId(showcase, court).toString()).toList()))
                .setParameter("dailyStart", showcase.dailyStart())
                .setParameter("dailyEnd", showcase.dailyEnd())
                .setParameter("matchMinutes", showcase.matchMinutes())
                .setParameter("playFormat", showcase.playFormat())
                .executeUpdate();
    }

    /** Đăng ký đội + đội hình + khoản lệ phí (đã đóng hoặc đang chờ tùy trạng thái). */
    private int insertTeam(Showcase showcase, int teamIndex) {
        Team team = showcase.teams().get(teamIndex);
        UUID registrationId = registrationId(showcase, teamIndex);
        LocalDateTime registeredAt = LocalDate.now().plusDays(showcase.openDay() + 1 + teamIndex % 5).atTime(20, 15);
        String status = switch (team.state()) {
            case CONFIRMED -> "CONFIRMED";
            case PENDING_PAYMENT -> "PENDING_PAYMENT";
            case PENDING_MEMBERS -> "PENDING_MEMBERS";
        };
        int rows = entityManager.createNativeQuery("""
                        insert into tournament_registrations (registration_id, tournament_id, team_name, type, status,
                                                              registered_by, registered_at, confirmed_at, payment_deadline,
                                                              created_at, created_by, updated_at, updated_by)
                        select :id, :tournamentId, :teamName, 'TEAM', :status,
                               :captainId, :registeredAt, :confirmedAt, :deadline,
                               :registeredAt, 'demo-seed', :registeredAt, 'demo-seed'
                        where not exists (select 1 from tournament_registrations where registration_id = :id)
                        """)
                .setParameter("id", registrationId)
                .setParameter("tournamentId", tournamentId(showcase))
                .setParameter("teamName", team.name())
                .setParameter("status", status)
                .setParameter("captainId", userId(team.captainEmail()))
                .setParameter("registeredAt", registeredAt)
                .setParameter("confirmedAt", team.state() == TeamState.CONFIRMED ? registeredAt.plusHours(2) : null)
                .setParameter("deadline", team.state() == TeamState.PENDING_PAYMENT
                        ? LocalDateTime.now().plusHours(20) : null)
                .executeUpdate();

        List<Integer> shirts = showcase.shirts();
        for (int slot = 0; slot < shirts.size(); slot++) {
            boolean captain = slot == showcase.captainSlot();
            // Đội đang lập: hai người cuối còn chưa nhận lời nên đội chưa đủ người.
            boolean invited = team.state() == TeamState.PENDING_MEMBERS && slot >= shirts.size() - 2;
            rows += entityManager.createNativeQuery("""
                            insert into tournament_lineups (lineup_id, registration_id, player_id, player_name, lineup_role,
                                                            shirt_number, member_status, responded_at,
                                                            created_at, created_by, updated_at, updated_by)
                            select :id, :registrationId, :playerId, :playerName, :role,
                                   :shirt, :memberStatus, :respondedAt,
                                   now(), 'demo-seed', now(), 'demo-seed'
                            where not exists (select 1 from tournament_lineups where lineup_id = :id)
                            """)
                    .setParameter("id", id("lineup", registrationId + "|" + slot))
                    .setParameter("registrationId", registrationId)
                    .setParameter("playerId", captain ? userId(team.captainEmail())
                            : id("player", showcase.name() + "|" + team.name() + "|" + slot))
                    .setParameter("playerName", captain ? CAPTAIN_NAMES.get(team.captainEmail())
                            : playerName(showcase, teamIndex, slot))
                    .setParameter("role", captain ? "CAPTAIN" : slot >= showcase.onField() ? "SUBSTITUTE" : "PLAYER")
                    .setParameter("shirt", shirts.get(slot))
                    .setParameter("memberStatus", invited ? "INVITED" : "ACCEPTED")
                    .setParameter("respondedAt", invited ? null : registeredAt.plusMinutes(30L + slot))
                    .executeUpdate();
        }

        if (showcase.fee() > 0 && team.state() != TeamState.PENDING_MEMBERS) {
            boolean paid = team.state() == TeamState.CONFIRMED;
            rows += entityManager.createNativeQuery("""
                            insert into tournament_payments (tournament_payment_id, registration_id, amount, payment_id,
                                                             status, paid_at,
                                                             created_at, created_by, updated_at, updated_by)
                            select :id, :registrationId, :amount, :paymentId, :status, :paidAt,
                                   now(), 'demo-seed', now(), 'demo-seed'
                            where not exists (select 1 from tournament_payments where registration_id = :registrationId)
                            """)
                    .setParameter("id", id("tournament-payment", registrationId.toString()))
                    .setParameter("registrationId", registrationId)
                    .setParameter("amount", showcase.fee())
                    .setParameter("paymentId", paid ? id("payment", registrationId.toString()) : null)
                    .setParameter("status", paid ? "SUCCEEDED" : "PENDING")
                    .setParameter("paidAt", paid ? registeredAt.plusHours(2) : null)
                    .executeUpdate();
        }
        return rows;
    }

    // ---------------------------------------------------------------- lịch thi đấu

    /**
     * Loại trực tiếp: vòng 1 ghép 1-2, 3-4...; các vòng sau lấy đội thắng của cặp tương ứng vòng trước.
     * Trận chưa có tỷ số để trống (và vòng sau hiện "Chờ xác định").
     */
    private int seedElimination(Showcase showcase, List<UUID> teams) {
        int rounds = Integer.numberOfTrailingZeros(teams.size());
        List<UUID> entrants = new ArrayList<>(teams);
        int resultIndex = 0;
        // next_fixture_id có khóa ngoại: tính kết quả theo chiều thuận nhưng ghi từ chung kết ngược về vòng 1.
        List<IntSupplier> inserts = new ArrayList<>();
        for (int round = 1; round <= rounds; round++) {
            int matches = entrants.size() / 2;
            List<UUID> winners = new ArrayList<>();
            for (int match = 1; match <= matches; match++) {
                UUID home = entrants.get(match * 2 - 2);
                UUID away = entrants.get(match * 2 - 1);
                int[] score = home != null && away != null && resultIndex < showcase.results().size()
                        ? showcase.results().get(resultIndex++) : null;
                UUID winner = score == null ? null : score[0] > score[1] ? home : away;
                winners.add(winner);
                UUID next = round < rounds ? fixtureId(showcase, round + 1, (match + 1) / 2) : null;
                int r = round;
                int m = match;
                inserts.add(() -> insertFixture(showcase, r, roundName(r, rounds), m, home, away, score, winner,
                        next, showcase.roundDays().get(r - 1), m - 1));
            }
            entrants = winners;
        }
        int rows = 0;
        for (int index = inserts.size() - 1; index >= 0; index--) rows += inserts.get(index).getAsInt();
        return rows;
    }

    /** Vòng tròn theo phương pháp xoay vòng: mỗi lượt mọi đội đá một trận, không đội nào đá hai trận cùng ngày. */
    private int seedRoundRobin(Showcase showcase, List<UUID> teams) {
        List<UUID> circle = new ArrayList<>(teams);
        int size = circle.size();
        int resultIndex = 0;
        int rows = 0;
        for (int round = 1; round < size; round++) {
            for (int match = 1; match <= size / 2; match++) {
                UUID home = circle.get(match - 1);
                UUID away = circle.get(size - match);
                int[] score = resultIndex < showcase.results().size() ? showcase.results().get(resultIndex++) : null;
                UUID winner = score == null || score[0] == score[1] ? null : score[0] > score[1] ? home : away;
                rows += insertFixture(showcase, round, "Lượt " + round, match, home, away, score, winner, null,
                        showcase.roundDays().get(round - 1), match - 1);
            }
            circle.add(1, circle.remove(size - 1));
        }
        return rows;
    }

    /** Một trận + khung giờ sân của nó: các trận cùng ngày chia lần lượt theo sân rồi theo giờ. */
    private int insertFixture(Showcase showcase, int round, String roundName, int match, UUID home, UUID away,
                              int[] score, UUID winner, UUID nextFixtureId, int dayOffset, int slotOfDay) {
        List<String> courts = showcase.courts();
        String court = courts.get(slotOfDay % courts.size());
        LocalTime start = showcase.dailyStart().plusMinutes((long) (slotOfDay / courts.size()) * showcase.matchMinutes());
        LocalDate playDate = LocalDate.now().plusDays(dayOffset);
        UUID fixtureId = fixtureId(showcase, round, match);
        UUID reservationId = id("tournament-reservation", fixtureId.toString());

        int rows = entityManager.createNativeQuery("""
                        insert into tournament_venue_reservations (reservation_id, tournament_id, venue_id, court_id,
                                                                   play_date, start_time, end_time, status,
                                                                   created_at, created_by, updated_at, updated_by)
                        select :id, :tournamentId, :venueId, :courtId, :playDate, :startTime, :endTime, 'CONFIRMED',
                               now(), 'demo-seed', now(), 'demo-seed'
                        where not exists (select 1 from tournament_venue_reservations where reservation_id = :id)
                        """)
                .setParameter("id", reservationId)
                .setParameter("tournamentId", tournamentId(showcase))
                .setParameter("venueId", id("venue", showcase.venueName()))
                .setParameter("courtId", courtId(showcase, court))
                .setParameter("playDate", playDate)
                .setParameter("startTime", start)
                .setParameter("endTime", start.plusMinutes(showcase.matchMinutes()))
                .executeUpdate();

        LocalDateTime playedAt = playDate.atTime(start).plusMinutes(showcase.matchMinutes());
        rows += entityManager.createNativeQuery("""
                        insert into tournament_fixtures (fixture_id, tournament_id, round_name, round_number, match_number,
                                                         registration1_id, registration2_id, score1, score2,
                                                         winner_registration_id, next_fixture_id, reservation_id, status,
                                                         created_at, created_by, updated_at, updated_by)
                        select :id, :tournamentId, :roundName, :round, :match,
                               :home, :away, :score1, :score2,
                               :winner, :next, :reservationId, :status,
                               :createdAt, 'demo-seed', :updatedAt, 'demo-seed'
                        where not exists (select 1 from tournament_fixtures where fixture_id = :id)
                        """)
                .setParameter("id", fixtureId)
                .setParameter("tournamentId", tournamentId(showcase))
                .setParameter("roundName", roundName)
                .setParameter("round", round)
                .setParameter("match", match)
                .setParameter("home", home)
                .setParameter("away", away)
                .setParameter("score1", score == null ? null : score[0])
                .setParameter("score2", score == null ? null : score[1])
                .setParameter("winner", winner)
                .setParameter("next", nextFixtureId)
                .setParameter("reservationId", reservationId)
                .setParameter("status", score == null ? "SCHEDULED" : "COMPLETED")
                .setParameter("createdAt", LocalDateTime.now().plusDays(showcase.startDay() - 1L))
                .setParameter("updatedAt", score == null ? LocalDateTime.now() : playedAt)
                .executeUpdate();
        return rows;
    }

    // ---------------------------------------------------------------- tiện ích

    private static String roundName(int round, int totalRounds) {
        int fromFinal = totalRounds - round;
        if (fromFinal == 0) return "Chung kết";
        if (fromFinal == 1) return "Bán kết";
        if (fromFinal == 2) return "Tứ kết";
        return "Vòng " + round;
    }

    /** Tên cầu thủ xác định theo vị trí để chạy lại seed luôn ra cùng một tên. */
    private static String playerName(Showcase showcase, int teamIndex, int slot) {
        // String.hashCode gần như không đổi khi chỉ khác chữ số cuối; băm qua UUID để tên phân bố đều.
        long hash = id("player-name", showcase.name() + "|" + teamIndex + "|" + slot).getMostSignificantBits() & Long.MAX_VALUE;
        return FAMILY.get((int) (hash % FAMILY.size())) + " " + MIDDLE.get((int) ((hash / 97) % MIDDLE.size())) + " "
                + GIVEN.get((int) ((hash / 9973) % GIVEN.size()));
    }

    private static String jsonArray(List<String> values) {
        return values.stream().map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static UUID tournamentId(Showcase showcase) {
        return id("tournament", showcase.name());
    }

    private static UUID registrationId(Showcase showcase, int teamIndex) {
        return id("registration", showcase.name() + "|" + showcase.teams().get(teamIndex).name());
    }

    private static UUID fixtureId(Showcase showcase, int round, int match) {
        return id("fixture", showcase.name() + "|" + round + "|" + match);
    }

    private static UUID courtId(Showcase showcase, String courtName) {
        return id("court", showcase.venueName() + "|" + courtName);
    }

    private static UUID userId(String email) {
        return UUID.nameUUIDFromBytes(("goatsports:user:" + email).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID id(String kind, String key) {
        return UUID.nameUUIDFromBytes(("goatsports:" + kind + ":" + key).getBytes(StandardCharsets.UTF_8));
    }
}
