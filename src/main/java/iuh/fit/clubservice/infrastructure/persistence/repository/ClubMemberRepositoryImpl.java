package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubMemberEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubMemberRepository;
import lombok.RequiredArgsConstructor;
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
}
