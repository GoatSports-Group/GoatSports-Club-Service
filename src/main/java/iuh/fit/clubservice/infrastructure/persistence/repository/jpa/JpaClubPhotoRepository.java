package iuh.fit.clubservice.infrastructure.persistence.repository.jpa;

import iuh.fit.clubservice.infrastructure.persistence.entity.ClubPhotoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface JpaClubPhotoRepository extends JpaRepository<ClubPhotoEntity, UUID> {
    Page<ClubPhotoEntity> findByClubId(UUID clubId, Pageable pageable);
}
