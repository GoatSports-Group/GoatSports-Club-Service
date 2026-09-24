package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TournamentLifecyclePolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    private Tournament tournament(TournamentStatus status, LocalDate open, LocalDate close) {
        return Tournament.builder().status(status).registrationOpenDate(open).registrationCloseDate(close).build();
    }

    @Test
    @DisplayName("mở/đóng đăng ký suy từ ngày; trạng thái do ban tổ chức quyết định thì giữ nguyên")
    void effectiveStatusFollowsDates() {
        assertThat(TournamentLifecyclePolicy.effectiveStatus(
                tournament(TournamentStatus.PUBLISHED, TODAY.plusDays(1), TODAY.plusDays(5)), TODAY))
                .isEqualTo(TournamentStatus.PUBLISHED);
        assertThat(TournamentLifecyclePolicy.effectiveStatus(
                tournament(TournamentStatus.PUBLISHED, TODAY, TODAY), TODAY))
                .isEqualTo(TournamentStatus.REGISTRATION_OPEN);
        assertThat(TournamentLifecyclePolicy.effectiveStatus(
                tournament(TournamentStatus.REGISTRATION_OPEN, TODAY.minusDays(5), TODAY.minusDays(1)), TODAY))
                .isEqualTo(TournamentStatus.REGISTRATION_CLOSED);
        assertThat(TournamentLifecyclePolicy.effectiveStatus(
                tournament(TournamentStatus.CANCELLED, TODAY.minusDays(1), TODAY.plusDays(1)), TODAY))
                .isEqualTo(TournamentStatus.CANCELLED);
    }

    @Test
    @DisplayName("đội đang lập hết cơ hội khi quá hạn đăng ký hoặc danh sách đã chốt")
    void formingExpiresAfterCloseOrLock() {
        Tournament open = tournament(TournamentStatus.PUBLISHED, TODAY.minusDays(1), TODAY);
        assertThat(TournamentLifecyclePolicy.isFormingExpired(open, TODAY)).isFalse();
        assertThat(TournamentLifecyclePolicy.isFormingExpired(open, TODAY.plusDays(1))).isTrue();
        open.setStatus(TournamentStatus.IN_PROGRESS);
        assertThat(TournamentLifecyclePolicy.isFormingExpired(open, TODAY)).isTrue();
        assertThat(TournamentLifecyclePolicy.isRosterLocked(open)).isTrue();
    }

    @Test
    @DisplayName("đội đang chờ đủ người vẫn giữ suất; đã rút hoặc bị từ chối thì không")
    void seatHolding() {
        assertThat(TournamentLifecyclePolicy.holdsSeat(RegistrationStatus.PENDING_MEMBERS)).isTrue();
        assertThat(TournamentLifecyclePolicy.holdsSeat(RegistrationStatus.CONFIRMED)).isTrue();
        assertThat(TournamentLifecyclePolicy.holdsSeat(RegistrationStatus.CANCELLED)).isFalse();
        assertThat(TournamentLifecyclePolicy.holdsSeat(RegistrationStatus.REJECTED)).isFalse();
    }
}
