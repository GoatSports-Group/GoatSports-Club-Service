package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.TournamentFixture;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentFixtureRepository {
    TournamentFixture save(TournamentFixture fixture);
    List<TournamentFixture> saveAll(List<TournamentFixture> fixtures);
    Optional<TournamentFixture> findById(UUID fixtureId);
    List<TournamentFixture> findByTournamentId(UUID tournamentId);
    /** Trận đã có tỉ số nhưng chưa tính được ELO. */
    List<TournamentFixture> findRatingPending(int limit);

    /** Đánh dấu đã tính ELO, chỉ khi tỉ số vẫn là tỉ số đã gửi đi (chủ sân có thể vừa sửa lại). */
    boolean markRatingSynced(UUID fixtureId, Integer score1, Integer score2);

    void deleteByTournamentId(UUID tournamentId);
}
