package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.ClubFeePayment;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubFeePaymentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClubFeePaymentEntityMapper {
    ClubFeePayment toDomain(ClubFeePaymentEntity entity);

    @Mapping(target = "fee", ignore = true)
    @Mapping(target = "membership", ignore = true)
    ClubFeePaymentEntity toEntity(ClubFeePayment domain);
}
