package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubPhoto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClubPhotoRepository {
    List<ClubPhoto> saveAll(List<ClubPhoto> photos);

    Page<ClubPhoto> findByClubId(UUID clubId, Pageable pageable);

    Optional<ClubPhoto> findById(UUID photoId);

    void deleteById(UUID photoId);
}
