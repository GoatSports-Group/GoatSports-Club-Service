package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.TournamentVenueReservation;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentVenueReservationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TournamentVenueReservationEntityMapper {
    TournamentVenueReservation toDomain(TournamentVenueReservationEntity entity);

    TournamentVenueReservationEntity toEntity(TournamentVenueReservation domain);
}
