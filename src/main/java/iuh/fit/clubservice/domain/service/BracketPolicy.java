package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.enums.MatchStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Quy tắc thuần của sơ đồ thi đấu: xếp hạt giống, đặt tên vòng, đưa đội thắng lên trận kế tiếp. */
public final class BracketPolicy {

    private BracketPolicy() {
    }

    /**
     * Thứ tự hạt giống xác định: sắp theo mã đăng ký rồi xáo bằng seed lấy từ tournamentId.
     * Chạy lại cùng một giải luôn ra đúng cùng một bracket — trước đây shuffle không seed nên mỗi lần một khác.
     */
    public static List<UUID> seed(List<UUID> registrationIds, UUID tournamentId) {
        List<UUID> ordered = new ArrayList<>(registrationIds);
        ordered.sort(Comparator.comparing(UUID::toString));
        Collections.shuffle(ordered, new Random(tournamentId.getMostSignificantBits()));
        return ordered;
    }

    /** Lũy thừa của 2 nhỏ nhất chứa đủ số đội; chỗ trống là lượt miễn thi đấu (bye). */
    public static int bracketSize(int teams) {
        int size = 1;
        while (size < teams) size *= 2;
        return size;
    }

    public static String roundName(int round, int totalRounds) {
        int fromFinal = totalRounds - round;
        if (fromFinal == 0) return "Chung kết";
        if (fromFinal == 1) return "Bán kết";
        if (fromFinal == 2) return "Tứ kết";
        return "Vòng " + round;
    }

    /** Trận lẻ của vòng trước đổ vào nhánh trên (đội 1), trận chẵn vào nhánh dưới (đội 2) của trận kế tiếp. */
    public static void placeWinner(TournamentFixture target, int sourceMatchNumber, UUID registrationId) {
        if ((sourceMatchNumber - 1) % 2 == 0) target.setRegistration1Id(registrationId);
        else target.setRegistration2Id(registrationId);
    }

    /** Trận thực sự đã đá (có đủ hai đội và đã có kết quả) — khác với trận bye được tự động xử thắng. */
    public static boolean hasPlayedResult(TournamentFixture fixture) {
        return fixture.getStatus() == MatchStatus.COMPLETED
                && fixture.getRegistration1Id() != null && fixture.getRegistration2Id() != null;
    }
}
