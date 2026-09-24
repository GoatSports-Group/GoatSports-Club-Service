package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.TournamentStatus;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Quy tắc vòng đời của giải và của suất đăng ký — một nguồn sự thật duy nhất cho mọi use case
 * (đăng ký, lập đội, lệ phí, job quá hạn, callback thanh toán).
 */
public final class TournamentLifecyclePolicy {

    /** Thời gian giữ suất để đóng lệ phí, tính từ lúc đăng ký đủ điều kiện thanh toán. */
    public static final Duration PAYMENT_WINDOW = Duration.ofHours(24);

    /**
     * Các trạng thái đang chiếm một suất của giải. Đội đang chờ đủ người (PENDING_MEMBERS) cũng giữ suất,
     * nếu không người đến sau sẽ lấp chỗ trong lúc đội còn đang mời đồng đội.
     */
    public static final Set<RegistrationStatus> HOLDING_SEAT = Collections.unmodifiableSet(EnumSet.of(
            RegistrationStatus.PENDING_MEMBERS, RegistrationStatus.PENDING_ELIGIBILITY,
            RegistrationStatus.PENDING_PAYMENT, RegistrationStatus.CONFIRMED));

    private TournamentLifecyclePolicy() {
    }

    public static boolean holdsSeat(RegistrationStatus status) {
        return HOLDING_SEAT.contains(status);
    }

    /**
     * Trạng thái hiển thị của giải. Mở/đóng đăng ký được suy ra từ ngày chứ không lưu thành cột riêng:
     * một state machine thứ hai chạy song song với ngày sẽ sớm lệch nhau. Các trạng thái do ban tổ chức
     * quyết định (nháp, hủy, đang diễn ra, kết thúc) thì giữ nguyên.
     * Cùng luật được dịch sang SQL trong {@code TournamentRepositoryImpl} để tìm kiếm phân trang đúng.
     */
    public static TournamentStatus effectiveStatus(Tournament tournament, LocalDate today) {
        TournamentStatus stored = tournament.getStatus();
        if (stored == TournamentStatus.DRAFT || stored == TournamentStatus.CANCELLED
                || stored == TournamentStatus.IN_PROGRESS || stored == TournamentStatus.COMPLETED) {
            return stored;
        }
        if (today.isBefore(tournament.getRegistrationOpenDate())) return TournamentStatus.PUBLISHED;
        if (!today.isAfter(tournament.getRegistrationCloseDate())) return TournamentStatus.REGISTRATION_OPEN;
        return TournamentStatus.REGISTRATION_CLOSED;
    }

    /** Đã xếp lịch (bracket chốt từ danh sách CONFIRMED) hoặc đã kết thúc. */
    public static boolean isStarted(Tournament tournament) {
        return tournament.getStatus() == TournamentStatus.IN_PROGRESS
                || tournament.getStatus() == TournamentStatus.COMPLETED;
    }

    /** Danh sách đội đã chốt: không nhận thêm lệ phí, tiền về muộn phải hoàn. */
    public static boolean isRosterLocked(Tournament tournament) {
        return isStarted(tournament) || tournament.getStatus() == TournamentStatus.CANCELLED;
    }

    /** Đội đang lập mà hết hạn đăng ký (hoặc giải đã chốt) thì không còn cơ hội đủ người. */
    public static boolean isFormingExpired(Tournament tournament, LocalDate today) {
        return today.isAfter(tournament.getRegistrationCloseDate()) || isRosterLocked(tournament);
    }
}
