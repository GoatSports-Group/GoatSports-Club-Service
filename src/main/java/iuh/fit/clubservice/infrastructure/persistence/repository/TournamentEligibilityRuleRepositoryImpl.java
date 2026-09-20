package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.TournamentEligibilityRuleRepository;
import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentEligibilityRuleEntity;
import iuh.fit.clubservice.infrastructure.persistence.mapper.TournamentEligibilityRuleEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaTournamentEligibilityRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TournamentEligibilityRuleRepositoryImpl implements TournamentEligibilityRuleRepository {
    private final JpaTournamentEligibilityRuleRepository jpaRepository;
    private final TournamentEligibilityRuleEntityMapper mapper;

    @Override
    public List<TournamentEligibilityRule> saveAll(List<TournamentEligibilityRule> rules) {
        List<TournamentEligibilityRuleEntity> entities = rules.stream().map(mapper::toEntity).toList();
        return jpaRepository.saveAll(entities).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<TournamentEligibilityRule> findByTournamentId(UUID tournamentId) {
        return jpaRepository.findByTournamentIdOrderByRuleTypeAsc(tournamentId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteByTournamentId(UUID tournamentId) {
        jpaRepository.deleteByTournamentId(tournamentId);
    }
}
