package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubPhotoRepository;
import iuh.fit.clubservice.domain.entity.ClubPhoto;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubPhotoEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ClubPhotoRepositoryImpl implements ClubPhotoRepository {
    private final JpaClubPhotoRepository jpaRepository;
    private final ClubPhotoEntityMapper mapper;

    @Override
    public List<ClubPhoto> saveAll(List<ClubPhoto> photos) {
        return jpaRepository.saveAll(photos.stream().map(mapper::toEntity).toList()).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public List<ClubPhoto> findByClubId(UUID clubId) {
        return jpaRepository.findByClubIdOrderByCreatedAtDesc(clubId).stream().map(mapper::toDomain).toList();
    }
}
