package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.ClubActivity;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubActivityEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClubActivityEntityMapper {
    ClubActivity toDomain(ClubActivityEntity entity);
    ClubActivityEntity toEntity(ClubActivity domain);
}
