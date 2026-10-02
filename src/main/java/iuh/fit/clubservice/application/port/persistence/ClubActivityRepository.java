package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubActivityRepository {
    ClubActivity save(ClubActivity activity);

    Optional<ClubActivity> findById(UUID activityId);

    List<ClubActivity> findByClubId(UUID clubId);

    Page<ClubActivity> findUpcomingByClubId(UUID clubId, java.time.LocalDateTime from, Pageable pageable);

    List<ClubActivity> findUpcomingByClubIds(List<UUID> clubIds, java.time.LocalDateTime from);

    Page<ClubActivity> findUpcomingByClubIds(List<UUID> clubIds, java.time.LocalDateTime from, Pageable pageable);

    /** So buoi sinh hoat co startAt trong [from, to] theo CLB. */
    java.util.Map<UUID, Long> countByClubIdsBetween(java.util.Collection<UUID> clubIds,
            java.time.LocalDateTime from, java.time.LocalDateTime to);

    void deleteById(UUID activityId);
}
