package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubActivityRepository;
import iuh.fit.clubservice.domain.entity.ClubActivity;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubActivityEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    public void deleteById(UUID activityId) {
        jpaClubActivityRepository.deleteById(activityId);
    }
}
