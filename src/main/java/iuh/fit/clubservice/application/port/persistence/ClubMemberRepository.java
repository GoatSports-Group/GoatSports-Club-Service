package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubMemberRepository {
    ClubMember save(ClubMember member);

    Optional<ClubMember> findById(UUID membershipId);

    List<ClubMember> findByClubId(UUID clubId);

    List<ClubMember> findByClubIdAndStatus(UUID clubId, ClubMemberStatus status);

    Page<ClubMember> findByClubIdAndStatus(UUID clubId, ClubMemberStatus status, Pageable pageable);

    List<ClubMember> findByUserIdAndStatus(UUID userId, ClubMemberStatus status);

    Optional<ClubMember> findByClubIdAndUserId(UUID clubId, UUID userId);

    long countByClubIdAndStatus(UUID clubId, ClubMemberStatus status);

    /** So thanh vien ACTIVE theo CLB (CLB khong co ai thi vang mat khoi map). */
    java.util.Map<UUID, Long> countActiveByClubIds(java.util.Collection<UUID> clubIds);

    /** So thanh vien ACTIVE vao CLB tu thoi diem `since`. */
    java.util.Map<UUID, Long> countJoinedSinceByClubIds(java.util.Collection<UUID> clubIds, java.time.LocalDateTime since);

    void deleteById(UUID membershipId);
}
