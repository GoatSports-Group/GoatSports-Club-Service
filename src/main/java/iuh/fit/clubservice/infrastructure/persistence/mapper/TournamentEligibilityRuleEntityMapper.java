package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentEligibilityRuleEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TournamentEligibilityRuleEntityMapper {
    TournamentEligibilityRule toDomain(TournamentEligibilityRuleEntity entity);

    TournamentEligibilityRuleEntity toEntity(TournamentEligibilityRule domain);
}
