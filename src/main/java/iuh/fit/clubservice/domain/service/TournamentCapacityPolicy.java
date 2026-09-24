package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.enums.TournamentFormat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * "Du san" = so luot san (san x ngay x so tran vua khung gio moi ngay) khong it hon so tran toi da
 * cua giai khi du suat.
 * ponytail: dem tong luot, chua xep lich thuc; mot doi van co the phai da hai tran lien nhau.
 * Neu can chac chan tung vong vua mot ngay, chuyen sang mo phong xep lich theo vong.
 */
public final class TournamentCapacityPolicy {

    public record Capacity(int requiredMatches, int availableMatches, int matchesPerDay) {
        public boolean enough() { return availableMatches >= requiredMatches; }
    }

    private TournamentCapacityPolicy() { }

    /** Loai truc tiep n doi can n-1 tran; vong tron can n(n-1)/2 tran. */
    public static int requiredMatches(TournamentFormat format, int participants) {
        if (participants < 2) return 0;
        return format == TournamentFormat.ROUND_ROBIN ? participants * (participants - 1) / 2 : participants - 1;
    }

    public static Capacity capacity(TournamentFormat format, int participants, int courts,
                                    LocalDate startDate, LocalDate endDate,
                                    LocalTime dailyStart, LocalTime dailyEnd, int matchMinutes) {
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        long windowMinutes = Duration.between(dailyStart, dailyEnd).toMinutes();
        int perCourtPerDay = matchMinutes <= 0 ? 0 : (int) (windowMinutes / matchMinutes);
        int perDay = perCourtPerDay * courts;
        return new Capacity(requiredMatches(format, participants), (int) Math.min(Integer.MAX_VALUE, perDay * days), perDay);
    }

    /** Thong bao loi cho ban to chuc, rong neu du san. */
    public static Optional<String> shortage(Capacity capacity, int courts, int matchMinutes) {
        if (capacity.enough()) return Optional.empty();
        return Optional.of("Không đủ sân: giải cần tối đa " + capacity.requiredMatches() + " trận nhưng "
                + courts + " sân trong khung giờ đã chọn chỉ đủ " + capacity.availableMatches()
                + " trận (" + matchMinutes + " phút/trận). Hãy thêm sân, kéo dài khung giờ, thêm ngày thi đấu "
                + "hoặc giảm số suất.");
    }
}
