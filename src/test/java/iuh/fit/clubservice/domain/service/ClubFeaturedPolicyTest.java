package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.service.ClubFeaturedPolicy.Stats;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClubFeaturedPolicyTest {

    private static Stats stats(long members, long recent, int wins, int draws, int matches) {
        return new Stats(SportType.BADMINTON, "ho-chi-minh", members, 0, recent, recent, wins, draws, matches);
    }

    @Test
    void oneLuckyWinDoesNotBeatALongWinningRecord() {
        Stats lucky = stats(10, 4, 1, 0, 1);
        Stats proven = stats(10, 4, 30, 0, 45);

        assertThat(ClubFeaturedPolicy.baseScore(proven)).isGreaterThan(ClubFeaturedPolicy.baseScore(lucky));
    }

    @Test
    void regularActivityOutweighsSheerSize() {
        Stats bigButQuiet = new Stats(SportType.FOOTBALL, null, 40, 0, 1, 1, 0, 0, 0);
        Stats smallButActive = new Stats(SportType.FOOTBALL, null, 6, 2, 8, 8, 0, 0, 0);

        assertThat(ClubFeaturedPolicy.baseScore(smallButActive)).isGreaterThan(ClubFeaturedPolicy.baseScore(bigButQuiet));
    }

    @Test
    void needsMembersAndARecentSession() {
        assertThat(ClubFeaturedPolicy.eligible(stats(3, 1, 0, 0, 0))).isTrue();
        assertThat(ClubFeaturedPolicy.eligible(stats(2, 5, 0, 0, 0))).isFalse();
        assertThat(ClubFeaturedPolicy.eligible(new Stats(SportType.TENNIS, null, 20, 0, 0, 0, 5, 0, 6))).isFalse();
    }

    @Test
    void personalisationMultipliesInsteadOfFiltering() {
        Stats club = stats(8, 4, 3, 1, 6);
        double base = ClubFeaturedPolicy.baseScore(club);

        assertThat(ClubFeaturedPolicy.score(club, Set.of(), Set.of())).isEqualTo(base);
        assertThat(ClubFeaturedPolicy.score(club, Set.of(SportType.BADMINTON), Set.of())).isEqualTo(base * 1.25);
        assertThat(ClubFeaturedPolicy.score(club, Set.of(SportType.BADMINTON), Set.of("ho-chi-minh")))
                .isCloseTo(base * 1.25 * 1.15, Offset.offset(1e-9));
        assertThat(ClubFeaturedPolicy.score(club, Set.of(SportType.FOOTBALL), Set.of("ha-noi"))).isEqualTo(base);
    }

    @Test
    void scoreStaysWithinZeroToHundredBeforeBoosts() {
        Stats maxed = new Stats(SportType.FOOTBALL, null, 1000, 50, 50, 50, 100, 0, 100);
        Stats empty = new Stats(SportType.FOOTBALL, null, 0, 0, 0, 0, 0, 0, 0);

        assertThat(ClubFeaturedPolicy.baseScore(maxed)).isLessThanOrEqualTo(100.0);
        assertThat(ClubFeaturedPolicy.baseScore(empty)).isGreaterThanOrEqualTo(0.0);
    }
}
