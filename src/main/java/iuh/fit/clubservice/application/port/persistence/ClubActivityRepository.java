package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubActivity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubActivityRepository {
    ClubActivity save(ClubActivity activity);
    Optional<ClubActivity> findById(UUID activityId);
    List<ClubActivity> findByClubId(UUID clubId);
    void deleteById(UUID activityId);
}
