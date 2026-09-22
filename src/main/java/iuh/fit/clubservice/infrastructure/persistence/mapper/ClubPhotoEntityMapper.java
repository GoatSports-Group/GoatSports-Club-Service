package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.ClubPhoto;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubPhotoEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClubPhotoEntityMapper {
    ClubPhoto toDomain(ClubPhotoEntity entity);
    ClubPhotoEntity toEntity(ClubPhoto domain);
}
