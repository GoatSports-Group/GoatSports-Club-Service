package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.ClubActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaClubActivityRepository extends JpaRepository<ClubActivityEntity, UUID> {
    List<ClubActivityEntity> findByClubIdOrderByStartAtDesc(UUID clubId);
}
