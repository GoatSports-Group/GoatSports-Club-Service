package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubMemberRepository {
    ClubMember save(ClubMember member);
    Optional<ClubMember> findById(UUID membershipId);
    List<ClubMember> findByClubId(UUID clubId);
    List<ClubMember> findByClubIdAndStatus(UUID clubId, ClubMemberStatus status);
    List<ClubMember> findByUserIdAndStatus(UUID userId, ClubMemberStatus status);
    Optional<ClubMember> findByClubIdAndUserId(UUID clubId, UUID userId);
    long countByClubIdAndStatus(UUID clubId, ClubMemberStatus status);
    void deleteById(UUID membershipId);
}
