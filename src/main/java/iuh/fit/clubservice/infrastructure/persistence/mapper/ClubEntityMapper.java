package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClubEntityMapper {
    Club toDomain(ClubEntity entity);
    ClubEntity toEntity(Club domain);
}
