package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubShortlistRepository;
import iuh.fit.clubservice.domain.entity.ClubShortlistEntry;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubShortlistEntryEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubShortlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ClubShortlistRepositoryImpl implements ClubShortlistRepository {
    private final JpaClubShortlistRepository jpaRepository;
    private final ClubShortlistEntryEntityMapper mapper;

    @Override
    public ClubShortlistEntry save(ClubShortlistEntry entry) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(entry)));
    }

    @Override
    public List<ClubShortlistEntry> findByClubId(UUID clubId) {
        return jpaRepository.findByClubIdOrderByCreatedAtDesc(clubId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<ClubShortlistEntry> findByClubIdAndUserId(UUID clubId, UUID userId) {
        return jpaRepository.findByClubIdAndUserId(clubId, userId).map(mapper::toDomain);
    }

    @Override
    public long countByClubId(UUID clubId) {
        return jpaRepository.countByClubId(clubId);
    }

    @Override
    public void delete(ClubShortlistEntry entry) {
        jpaRepository.deleteById(entry.getEntryId());
    }

    @Override
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
