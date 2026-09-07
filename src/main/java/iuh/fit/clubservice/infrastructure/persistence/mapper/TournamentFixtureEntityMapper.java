package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.TournamentFixture;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentFixtureEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TournamentFixtureEntityMapper {
    TournamentFixture toDomain(TournamentFixtureEntity entity);
    TournamentFixtureEntity toEntity(TournamentFixture domain);
}
