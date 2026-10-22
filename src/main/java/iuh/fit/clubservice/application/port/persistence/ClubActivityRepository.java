package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.domain.entity.ClubActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ClubActivityRepository {
    ClubActivity save(ClubActivity activity);

    Optional<ClubActivity> findById(UUID activityId);

    List<ClubActivity> findByClubId(UUID clubId);

    Page<ClubActivity> findUpcomingByClubId(UUID clubId, LocalDateTime from, Pageable pageable);

    List<ClubActivity> findUpcomingByClubIds(List<UUID> clubIds, LocalDateTime from);

    Page<ClubActivity> findUpcomingByClubIds(List<UUID> clubIds, LocalDateTime from, Pageable pageable);

    /** So buoi sinh hoat co startAt trong [from, to] theo CLB. */
    Map<UUID, Long> countByClubIdsBetween(Collection<UUID> clubIds,
            LocalDateTime from, LocalDateTime to);

    void deleteById(UUID activityId);
}
