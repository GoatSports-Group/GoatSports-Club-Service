package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentRegistrationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TournamentRegistrationEntityMapper {
    TournamentRegistration toDomain(TournamentRegistrationEntity entity);
    TournamentRegistrationEntity toEntity(TournamentRegistration domain);
}
