package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubMemberEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ClubMemberRepositoryImpl implements ClubMemberRepository {
    private final JpaClubMemberRepository jpaClubMemberRepository;
    private final ClubMemberEntityMapper clubMemberEntityMapper;

    @Override
    public ClubMember save(ClubMember member) {
        return clubMemberEntityMapper.toDomain(jpaClubMemberRepository.save(clubMemberEntityMapper.toEntity(member)));
    }

    @Override
    public Optional<ClubMember> findById(UUID membershipId) {
        return jpaClubMemberRepository.findById(membershipId).map(clubMemberEntityMapper::toDomain);
    }

    @Override
    public List<ClubMember> findByClubId(UUID clubId) {
        return jpaClubMemberRepository.findByClubId(clubId).stream().map(clubMemberEntityMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ClubMember> findByClubIdAndStatus(UUID clubId, ClubMemberStatus status) {
        return jpaClubMemberRepository.findByClubIdAndStatus(clubId, status).stream().map(clubMemberEntityMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Page<ClubMember> findByClubIdAndStatus(UUID clubId, ClubMemberStatus status, Pageable pageable) {
        return jpaClubMemberRepository.findByClubIdAndStatus(clubId, status, pageable)
                .map(clubMemberEntityMapper::toDomain);
    }

    @Override
    public List<ClubMember> findByUserIdAndStatus(UUID userId, ClubMemberStatus status) {
        return jpaClubMemberRepository.findByUserIdAndStatus(userId, status).stream().map(clubMemberEntityMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<ClubMember> findByClubIdAndUserId(UUID clubId, UUID userId) {
        return jpaClubMemberRepository.findByClubIdAndUserId(clubId, userId).map(clubMemberEntityMapper::toDomain);
    }

    @Override
    public long countByClubIdAndStatus(UUID clubId, ClubMemberStatus status) {
        return jpaClubMemberRepository.countByClubIdAndStatus(clubId, status);
    }

    @Override
    public void deleteById(UUID membershipId) {
        jpaClubMemberRepository.deleteById(membershipId);
    }

    @Override
    public java.util.Map<UUID, Long> countActiveByClubIds(java.util.Collection<UUID> clubIds) {
        if (clubIds.isEmpty()) return java.util.Map.of();
        return toCountMap(jpaClubMemberRepository.countByClubIdsAndStatus(clubIds, ClubMemberStatus.ACTIVE));
    }

    @Override
    public java.util.Map<UUID, Long> countJoinedSinceByClubIds(java.util.Collection<UUID> clubIds, java.time.LocalDateTime since) {
        if (clubIds.isEmpty()) return java.util.Map.of();
        return toCountMap(jpaClubMemberRepository.countJoinedSince(clubIds, ClubMemberStatus.ACTIVE, since));
    }

    /** Hang (clubId, count) cua cac truy van group-by thanh map. */
    private static java.util.Map<UUID, Long> toCountMap(List<Object[]> rows) {
        java.util.Map<UUID, Long> counts = new java.util.HashMap<>();
        for (Object[] row : rows) counts.put((UUID) row[0], ((Number) row[1]).longValue());
        return counts;
    }
}
