package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import java.util.Collection;
import java.time.LocalDateTime;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubMemberEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaClubMemberRepository extends JpaRepository<ClubMemberEntity, UUID> {
    List<ClubMemberEntity> findByClubId(UUID clubId);

    List<ClubMemberEntity> findByClubIdAndStatus(UUID clubId, ClubMemberStatus status);

    Page<ClubMemberEntity> findByClubIdAndStatus(UUID clubId, ClubMemberStatus status, Pageable pageable);

    List<ClubMemberEntity> findByUserIdAndStatus(UUID userId, ClubMemberStatus status);

    Optional<ClubMemberEntity> findByClubIdAndUserId(UUID clubId, UUID userId);

    long countByClubIdAndStatus(UUID clubId, ClubMemberStatus status);

    @Query("select m.clubId, count(m) from ClubMemberEntity m "
            + "where m.clubId in :clubIds and m.status = :status group by m.clubId")
    List<Object[]> countByClubIdsAndStatus(@Param("clubIds") Collection<UUID> clubIds,
            @Param("status") ClubMemberStatus status);

    @Query("select m.clubId, count(m) from ClubMemberEntity m "
            + "where m.clubId in :clubIds and m.status = :status and m.joinedAt >= :since group by m.clubId")
    List<Object[]> countJoinedSince(@Param("clubIds") Collection<UUID> clubIds,
            @Param("status") ClubMemberStatus status, @Param("since") LocalDateTime since);
}
