package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.ClubActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface JpaClubActivityRepository extends JpaRepository<ClubActivityEntity, UUID> {
    List<ClubActivityEntity> findByClubIdOrderByStartAtDesc(UUID clubId);

    Page<ClubActivityEntity> findByClubIdAndStartAtGreaterThanEqualOrderByStartAtAsc(
            UUID clubId, LocalDateTime from, Pageable pageable);

    List<ClubActivityEntity> findByClubIdInAndStartAtGreaterThanEqualOrderByStartAtAsc(
            List<UUID> clubIds, LocalDateTime from);

    Page<ClubActivityEntity> findByClubIdInAndStartAtGreaterThanEqualOrderByStartAtAsc(
            List<UUID> clubIds, LocalDateTime from, Pageable pageable);
}
