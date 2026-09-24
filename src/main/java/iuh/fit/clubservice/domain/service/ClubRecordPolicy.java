package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.entity.Club;

/** Thành tích thắng/hòa/thua của CLB, cộng dồn từ các trận giải đấu mà đội của CLB tham gia. */
public final class ClubRecordPolicy {

    private ClubRecordPolicy() {
    }

    /**
     * Cộng (sign = 1) hoặc trừ (sign = -1) một kết quả vào thành tích. Sửa tỷ số thì trừ kết quả cũ trước rồi mới
     * cộng kết quả mới — nếu không thành tích sẽ cộng dồn sai. matchCount và winRate luôn suy ra từ ba con số gốc.
     *
     * @param comparison dương = CLB thắng, 0 = hòa, âm = thua
     */
    public static void apply(Club club, int comparison, int sign) {
        club.setWinCount(Math.max(0, orZero(club.getWinCount()) + (comparison > 0 ? sign : 0)));
        club.setLossCount(Math.max(0, orZero(club.getLossCount()) + (comparison < 0 ? sign : 0)));
        club.setDrawCount(Math.max(0, orZero(club.getDrawCount()) + (comparison == 0 ? sign : 0)));
        int matches = club.getWinCount() + club.getLossCount() + club.getDrawCount();
        club.setMatchCount(matches);
        club.setWinRate(matches == 0 ? 0D : (double) club.getWinCount() / matches);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
