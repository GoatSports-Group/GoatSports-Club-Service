package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.ClubShortlistEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaClubShortlistRepository extends JpaRepository<ClubShortlistEntryEntity, UUID> {
    List<ClubShortlistEntryEntity> findByClubIdOrderByCreatedAtDesc(UUID clubId);

    Optional<ClubShortlistEntryEntity> findByClubIdAndUserId(UUID clubId, UUID userId);

    long countByClubId(UUID clubId);

    @org.springframework.transaction.annotation.Transactional
    void deleteByUserId(UUID userId);
}
