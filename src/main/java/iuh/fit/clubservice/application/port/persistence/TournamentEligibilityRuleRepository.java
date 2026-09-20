package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;

import java.util.List;
import java.util.UUID;

public interface TournamentEligibilityRuleRepository {
    List<TournamentEligibilityRule> saveAll(List<TournamentEligibilityRule> rules);

    List<TournamentEligibilityRule> findByTournamentId(UUID tournamentId);

    void deleteByTournamentId(UUID tournamentId);
}
