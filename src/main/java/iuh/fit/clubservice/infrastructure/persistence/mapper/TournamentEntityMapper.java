package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TournamentEntityMapper {
    Tournament toDomain(TournamentEntity entity);
    TournamentEntity toEntity(Tournament domain);
}
