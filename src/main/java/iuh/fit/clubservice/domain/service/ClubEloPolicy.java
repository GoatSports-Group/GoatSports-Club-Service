package iuh.fit.clubservice.domain.service;

/**
 * ELO của CLB trong các trận giao hữu ghép bằng AI (chỉ môn đồng đội). Cùng công thức với ELO người chơi
 * bên auth-service: K = 40 cho 10 trận đầu để CLB mới nhanh chóng về đúng mức, sau đó K = 24.
 */
public final class ClubEloPolicy {
    public static final int INITIAL_RATING = 1200;

    private ClubEloPolicy() {
    }

    /** @param comparison dương = CLB thắng, 0 = hòa, âm = thua */
    public static int newRating(int rating, int opponentRating, int matchCount, int comparison) {
        double expected = 1.0 / (1.0 + Math.pow(10, (opponentRating - rating) / 400.0));
        double actual = comparison > 0 ? 1.0 : comparison == 0 ? 0.5 : 0.0;
        int k = matchCount < 10 ? 40 : 24;
        return Math.max(0, (int) Math.round(rating + k * (actual - expected)));
    }
}
