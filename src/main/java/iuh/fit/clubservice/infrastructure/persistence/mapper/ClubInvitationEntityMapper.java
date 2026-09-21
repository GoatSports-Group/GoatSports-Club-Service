package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubInvitationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClubInvitationEntityMapper {
    ClubInvitation toDomain(ClubInvitationEntity entity);

    ClubInvitationEntity toEntity(ClubInvitation domain);
}
