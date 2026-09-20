package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentEligibilityRuleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TournamentEligibilityRuleEntityMapper {
    TournamentEligibilityRule toDomain(TournamentEligibilityRuleEntity entity);

    @Mapping(target = "tournament", ignore = true)
    TournamentEligibilityRuleEntity toEntity(TournamentEligibilityRule domain);
}
