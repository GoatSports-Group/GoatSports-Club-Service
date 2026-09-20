package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.ClubFee;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubFeeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClubFeeEntityMapper {
    ClubFee toDomain(ClubFeeEntity entity);

    @Mapping(target = "club", ignore = true)
    @Mapping(target = "payments", ignore = true)
    ClubFeeEntity toEntity(ClubFee domain);
}
