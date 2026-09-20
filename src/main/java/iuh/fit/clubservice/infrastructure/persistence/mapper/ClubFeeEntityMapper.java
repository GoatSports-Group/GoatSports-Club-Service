package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.ClubFee;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubFeeEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClubFeeEntityMapper {
    ClubFee toDomain(ClubFeeEntity entity);

    ClubFeeEntity toEntity(ClubFee domain);
}
