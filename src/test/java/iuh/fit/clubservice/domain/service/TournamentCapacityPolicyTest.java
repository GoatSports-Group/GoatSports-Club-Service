package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class TournamentCapacityPolicyTest {

    @Test
    void countsMatchesPerFormat() {
        assertThat(TournamentCapacityPolicy.requiredMatches(TournamentFormat.SINGLE_ELIMINATION, 16)).isEqualTo(15);
        assertThat(TournamentCapacityPolicy.requiredMatches(TournamentFormat.ROUND_ROBIN, 6)).isEqualTo(15);
        assertThat(TournamentCapacityPolicy.requiredMatches(TournamentFormat.ROUND_ROBIN, 1)).isZero();
    }

    @Test
    void courtsTimesDaysTimesMatchesThatFitTheWindow() {
        // 2 san x 3 ngay x floor(150 phut / 45) = 2 x 3 x 3 = 18 luot.
        var capacity = TournamentCapacityPolicy.capacity(TournamentFormat.ROUND_ROBIN, 6, 2,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), LocalTime.of(17, 0), LocalTime.of(19, 30), 45);

        assertThat(capacity.availableMatches()).isEqualTo(18);
        assertThat(capacity.matchesPerDay()).isEqualTo(6);
        assertThat(capacity.enough()).isTrue();
        assertThat(TournamentCapacityPolicy.shortage(capacity, 2, 45)).isEmpty();
    }

    @Test
    void shortageExplainsTheGap() {
        var capacity = TournamentCapacityPolicy.capacity(TournamentFormat.ROUND_ROBIN, 8, 1,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), LocalTime.of(18, 0), LocalTime.of(20, 0), 60);

        assertThat(capacity.enough()).isFalse();
        assertThat(TournamentCapacityPolicy.shortage(capacity, 1, 60)).get().asString()
                .contains("28 trận").contains("2 trận");
    }

    @Test
    void participationFollowsSport() {
        assertThat(SportParticipationPolicy.formatsOf(SportType.BADMINTON)).extracting("code")
                .containsExactly("BADMINTON_SINGLES", "BADMINTON_DOUBLES");
        assertThat(SportParticipationPolicy.resolve(SportType.FOOTBALL, "BADMINTON_SINGLES").sport())
                .isEqualTo(SportType.FOOTBALL);
        assertThat(SportParticipationPolicy.find("VOLLEYBALL_6").orElseThrow().rosterMin()).isEqualTo(6);
    }
}
