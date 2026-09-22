package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.ClubPhotoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaClubPhotoRepository extends JpaRepository<ClubPhotoEntity, UUID> {
    List<ClubPhotoEntity> findByClubIdOrderByCreatedAtDesc(UUID clubId);
}
