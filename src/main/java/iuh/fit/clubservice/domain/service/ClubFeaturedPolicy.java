package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.enums.SportType;

import java.util.Set;

/**
 * Cham diem "CLB noi bat" (trang Cau lac bo va /clubs/featured), thang 0-100 roi nhan he so ca nhan hoa.
 *
 * <ul>
 *   <li>Sinh hoat deu 40%: so buoi trong 30 ngay qua va 14 ngay toi, du diem tu 8 buoi.</li>
 *   <li>Thanh tich 25%: ti le thang lam muot (thang + 0.5 hoa + 2) / (so tran + 4), nen CLB thang 1/1 tran
 *       (0.6) khong vuot CLB thang 30/45 (~0.67).</li>
 *   <li>Dang phat trien 20%: thanh vien moi trong 30 ngay, du diem tu 5 nguoi.</li>
 *   <li>Quy mo 15%: log2(thanh vien + 1) / log2(31), du diem quanh 30 nguoi — CLB dong nguoi khong ap dao.</li>
 * </ul>
 * Ca nhan hoa la he so nhan, khong phai bo loc: cung mon x1.25, cung tinh/thanh x1.15.
 */
public final class ClubFeaturedPolicy {
    public static final int MIN_MEMBERS = 3;
    public static final int ACTIVE_WINDOW_DAYS = 60;
    public static final int RECENT_DAYS = 30;
    public static final int UPCOMING_DAYS = 14;

    static final double SAME_SPORT_BOOST = 1.25;
    static final double SAME_CITY_BOOST = 1.15;

    private ClubFeaturedPolicy() {
    }

    /** So lieu cua mot CLB trong cac cua so thoi gian tren. */
    public record Stats(SportType sportType, String city, long members, long newMembers, long recentActivities,
                        long windowActivities, int wins, int draws, int matches) {
    }

    public static boolean eligible(Stats stats) {
        return stats.members() >= MIN_MEMBERS && stats.windowActivities() > 0;
    }

    public static double baseScore(Stats stats) {
        double activity = Math.min(stats.recentActivities() / 8.0, 1.0);
        double performance = (stats.wins() + 0.5 * stats.draws() + 2.0) / (stats.matches() + 4.0);
        double growth = Math.min(stats.newMembers() / 5.0, 1.0);
        double size = Math.min(log2(stats.members() + 1.0) / log2(31.0), 1.0);
        return 100.0 * (0.40 * activity + 0.25 * performance + 0.20 * growth + 0.15 * size);
    }

    public static boolean sameSport(Stats stats, Set<SportType> viewerSports) {
        return stats.sportType() != null && viewerSports.contains(stats.sportType());
    }

    public static boolean sameCity(Stats stats, Set<String> viewerCities) {
        return stats.city() != null && viewerCities.contains(stats.city());
    }

    public static double score(Stats stats, Set<SportType> viewerSports, Set<String> viewerCities) {
        double score = baseScore(stats);
        if (sameSport(stats, viewerSports)) score *= SAME_SPORT_BOOST;
        if (sameCity(stats, viewerCities)) score *= SAME_CITY_BOOST;
        return score;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }
}
