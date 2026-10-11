package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.application.service.AdminCommunityService;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface JpaClubRepository extends JpaRepository<ClubEntity, UUID>, JpaSpecificationExecutor<ClubEntity> {

    List<ClubEntity> findByActiveTrueAndDisbandedAtIsNullAndPrivacy(ClubPrivacy privacy);

    long countByActiveTrueAndDisbandedAtIsNull();

    long countByDisbandedAtIsNotNull();

    @Query(value = """
            select new iuh.fit.clubservice.application.service.AdminCommunityService$ClubRow(
                c.clubId, c.name, c.sportType, c.city, c.logoUrl, c.ownerId, c.privacy, c.disbandedAt,
                c.eloRating, c.matchCount, c.createdAt,
                (select count(m) from ClubMemberEntity m where m.clubId = c.clubId and m.status = :activeMember))
            from ClubEntity c
            where ((:includeActive = true and c.disbandedAt is null) or (:includeDisbanded = true and c.disbandedAt is not null))
              and c.sportType in :sportTypes and lower(c.name) like :pattern
            order by c.createdAt desc, c.clubId
            """, countQuery = """
            select count(c) from ClubEntity c
            where ((:includeActive = true and c.disbandedAt is null) or (:includeDisbanded = true and c.disbandedAt is not null))
              and c.sportType in :sportTypes and lower(c.name) like :pattern
            """)
    Page<AdminCommunityService.ClubRow> searchForAdmin(@Param("includeActive") boolean includeActive,
                                                       @Param("includeDisbanded") boolean includeDisbanded,
                                                       @Param("sportTypes") Collection<SportType> sportTypes,
                                                       @Param("pattern") String pattern,
                                                       @Param("activeMember") ClubMemberStatus activeMember,
                                                       Pageable pageable);
}
