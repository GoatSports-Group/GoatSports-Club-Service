package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.ClubFeePayment;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubFeePaymentEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClubFeePaymentEntityMapper {
    ClubFeePayment toDomain(ClubFeePaymentEntity entity);

    ClubFeePaymentEntity toEntity(ClubFeePayment domain);
}
