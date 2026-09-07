package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaClubMemberRepository extends JpaRepository<ClubMemberEntity, UUID> {
    List<ClubMemberEntity> findByClubId(UUID clubId);
    List<ClubMemberEntity> findByClubIdAndStatus(UUID clubId, ClubMemberStatus status);
    List<ClubMemberEntity> findByUserIdAndStatus(UUID userId, ClubMemberStatus status);
    Optional<ClubMemberEntity> findByClubIdAndUserId(UUID clubId, UUID userId);
    long countByClubIdAndStatus(UUID clubId, ClubMemberStatus status);
}
