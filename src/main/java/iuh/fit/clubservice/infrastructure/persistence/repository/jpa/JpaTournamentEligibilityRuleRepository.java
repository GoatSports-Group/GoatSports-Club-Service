package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentEligibilityRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaTournamentEligibilityRuleRepository
        extends JpaRepository<TournamentEligibilityRuleEntity, UUID> {
    List<TournamentEligibilityRuleEntity> findByTournamentIdOrderByRuleTypeAsc(UUID tournamentId);

    void deleteByTournamentId(UUID tournamentId);
}
