package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.TournamentLineup;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentLineupEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TournamentLineupEntityMapper {
    TournamentLineup toDomain(TournamentLineupEntity entity);
    TournamentLineupEntity toEntity(TournamentLineup domain);
}
