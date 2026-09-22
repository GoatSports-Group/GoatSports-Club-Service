package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubPhotoRepository;
import iuh.fit.clubservice.domain.entity.ClubPhoto;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubPhotoEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
    public Page<ClubPhoto> findByClubId(UUID clubId, Pageable pageable) {
        return jpaRepository.findByClubId(clubId, pageable).map(mapper::toDomain);
    }

    @Override
    public Optional<ClubPhoto> findById(UUID photoId) {
        return jpaRepository.findById(photoId).map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID photoId) {
        jpaRepository.deleteById(photoId);
    }
}
