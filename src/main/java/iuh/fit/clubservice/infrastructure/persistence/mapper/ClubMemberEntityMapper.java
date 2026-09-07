package iuh.fit.clubservice.infrastructure.persistence.mapper;

import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubMemberEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClubMemberEntityMapper {
    ClubMember toDomain(ClubMemberEntity entity);
    ClubMemberEntity toEntity(ClubMember domain);
}
