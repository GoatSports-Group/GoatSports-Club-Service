package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubPhoto;

import java.util.List;
import java.util.UUID;

public interface ClubPhotoRepository {
    List<ClubPhoto> saveAll(List<ClubPhoto> photos);

    List<ClubPhoto> findByClubId(UUID clubId);
}
