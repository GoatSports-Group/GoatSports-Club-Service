package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubActivityRepository;
import iuh.fit.clubservice.domain.entity.ClubActivity;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubActivityEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ClubActivityRepositoryImpl implements ClubActivityRepository {
    private final JpaClubActivityRepository jpaClubActivityRepository;
    private final ClubActivityEntityMapper clubActivityEntityMapper;

    @Override
    public ClubActivity save(ClubActivity activity) {
        return clubActivityEntityMapper.toDomain(jpaClubActivityRepository.save(clubActivityEntityMapper.toEntity(activity)));
    }

    @Override
    public Optional<ClubActivity> findById(UUID activityId) {
        return jpaClubActivityRepository.findById(activityId).map(clubActivityEntityMapper::toDomain);
    }

    @Override
    public List<ClubActivity> findByClubId(UUID clubId) {
        return jpaClubActivityRepository.findByClubIdOrderByStartAtDesc(clubId).stream()
                .map(clubActivityEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ClubActivity> findUpcomingByClubId(UUID clubId, LocalDateTime from, Pageable pageable) {
        return jpaClubActivityRepository
                .findByClubIdAndStartAtGreaterThanEqualOrderByStartAtAsc(clubId, from, pageable)
                .map(clubActivityEntityMapper::toDomain);
    }

    @Override
    public List<ClubActivity> findUpcomingByClubIds(List<UUID> clubIds, LocalDateTime from) {
        if (clubIds == null || clubIds.isEmpty()) return List.of();
        return jpaClubActivityRepository
                .findByClubIdInAndStartAtGreaterThanEqualOrderByStartAtAsc(clubIds, from).stream()
                .map(clubActivityEntityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ClubActivity> findUpcomingByClubIds(List<UUID> clubIds, LocalDateTime from,
                                                    Pageable pageable) {
        if (clubIds == null || clubIds.isEmpty()) return new PageImpl<>(List.of(), pageable, 0);
        return jpaClubActivityRepository
                .findByClubIdInAndStartAtGreaterThanEqualOrderByStartAtAsc(clubIds, from, pageable)
                .map(clubActivityEntityMapper::toDomain);
    }

    @Override
    public void deleteById(UUID activityId) {
        jpaClubActivityRepository.deleteById(activityId);
    }

    @Override
    public Map<UUID, Long> countByClubIdsBetween(Collection<UUID> clubIds,
            LocalDateTime from, LocalDateTime to) {
        if (clubIds.isEmpty()) return Map.of();
        return toCountMap(jpaClubActivityRepository.countByClubIdsBetween(clubIds, from, to));
    }

    /** Hang (clubId, count) cua cac truy van group-by thanh map. */
    private static Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) counts.put((UUID) row[0], ((Number) row[1]).longValue());
        return counts;
    }
}
