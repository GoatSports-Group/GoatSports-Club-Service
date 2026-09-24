package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.enums.MatchStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bảng xếp hạng vòng tròn, tính trực tiếp từ kết quả các trận. Không lưu thành bảng riêng: đó là dữ liệu dẫn xuất,
 * lưu thêm sẽ tạo nguồn sự thật thứ hai có thể lệch với kết quả trận.
 */
public final class StandingsCalculator {

    /** Thắng 3 điểm, hòa 1, thua 0. */
    public record Standing(UUID registrationId, int played, int won, int drawn, int lost,
                           int scoreFor, int scoreAgainst, int points, int rank) {
    }

    private StandingsCalculator() {
    }

    /**
     * Xếp hạng theo điểm → hiệu số → số bàn thắng → mã đăng ký (để thứ tự luôn ổn định).
     * Lưu ý lịch sử: từng có lỗi gọi .reversed() hai lần trên cả chuỗi comparator làm bảng đảo ngược hoàn toàn.
     */
    public static List<Standing> calculate(List<UUID> registrationIds, List<TournamentFixture> fixtures) {
        Map<UUID, Tally> table = new LinkedHashMap<>();
        registrationIds.forEach(id -> table.put(id, new Tally()));
        fixtures.stream()
                .filter(f -> f.getStatus() == MatchStatus.COMPLETED && f.getRegistration2Id() != null)
                .forEach(f -> record(table, f));

        Comparator<Map.Entry<UUID, Tally>> ranking =
                Comparator.<Map.Entry<UUID, Tally>>comparingInt(e -> e.getValue().points).reversed()
                        .thenComparing(Comparator.<Map.Entry<UUID, Tally>>comparingInt(
                                e -> e.getValue().scoreFor - e.getValue().scoreAgainst).reversed())
                        .thenComparing(Comparator.<Map.Entry<UUID, Tally>>comparingInt(
                                e -> e.getValue().scoreFor).reversed())
                        .thenComparing(e -> e.getKey().toString());
        List<Map.Entry<UUID, Tally>> sorted = table.entrySet().stream().sorted(ranking).toList();
        List<Standing> result = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            Tally t = sorted.get(index).getValue();
            result.add(new Standing(sorted.get(index).getKey(), t.played, t.won, t.drawn, t.lost,
                    t.scoreFor, t.scoreAgainst, t.points, index + 1));
        }
        return result;
    }

    private static void record(Map<UUID, Tally> table, TournamentFixture f) {
        Tally first = table.get(f.getRegistration1Id());
        Tally second = table.get(f.getRegistration2Id());
        // Đội đã rút/bị loại khỏi danh sách CONFIRMED thì bỏ qua trận của họ.
        if (first == null || second == null) return;
        first.add(f.getScore1(), f.getScore2());
        second.add(f.getScore2(), f.getScore1());
    }

    private static final class Tally {
        int played, won, drawn, lost, scoreFor, scoreAgainst, points;

        void add(int own, int opponent) {
            played++;
            scoreFor += own;
            scoreAgainst += opponent;
            if (own > opponent) { won++; points += 3; }
            else if (own == opponent) { drawn++; points++; }
            else lost++;
        }
    }
}
