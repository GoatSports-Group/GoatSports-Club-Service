package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.domain.enums.TournamentFormat;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Nhà vô địch của một giải đã kết thúc: thắng trận chung kết (loại trực tiếp) hoặc hạng 1 bảng (vòng tròn). */
public final class ChampionPolicy {

    private ChampionPolicy() {
    }

    public static Optional<UUID> champion(TournamentFormat format, List<UUID> confirmedIds, List<TournamentFixture> fixtures) {
        if (fixtures == null || fixtures.isEmpty()) return Optional.empty();
        if (format == TournamentFormat.ROUND_ROBIN) {
            return StandingsCalculator.calculate(confirmedIds, fixtures).stream()
                    .filter(row -> row.rank() == 1)
                    .map(StandingsCalculator.Standing::registrationId)
                    .findFirst();
        }
        return fixtures.stream()
                .max(Comparator.comparingInt(fixture -> fixture.getRoundNumber() == null ? 0 : fixture.getRoundNumber()))
                .map(TournamentFixture::getWinnerRegistrationId);
    }
}
