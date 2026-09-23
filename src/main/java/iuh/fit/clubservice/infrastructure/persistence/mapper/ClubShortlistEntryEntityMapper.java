package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.ClubShortlistEntry;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubShortlistEntryEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClubShortlistEntryEntityMapper {
    ClubShortlistEntry toDomain(ClubShortlistEntryEntity entity);

    ClubShortlistEntryEntity toEntity(ClubShortlistEntry domain);
}
