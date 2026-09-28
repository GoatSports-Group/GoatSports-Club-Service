package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.ClubFriendlyMatch;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubFriendlyMatchEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClubFriendlyMatchEntityMapper {
    ClubFriendlyMatch toDomain(ClubFriendlyMatchEntity entity);
    ClubFriendlyMatchEntity toEntity(ClubFriendlyMatch domain);
}
