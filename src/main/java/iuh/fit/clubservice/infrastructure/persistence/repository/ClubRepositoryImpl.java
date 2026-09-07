package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ClubRepositoryImpl implements ClubRepository {
    private final JpaClubRepository jpaClubRepository;
    private final ClubEntityMapper clubEntityMapper;

    @Override
    public Club save(Club club) {
        return clubEntityMapper.toDomain(jpaClubRepository.save(clubEntityMapper.toEntity(club)));
    }

    @Override
    public Optional<Club> findById(UUID clubId) {
        return jpaClubRepository.findById(clubId).map(clubEntityMapper::toDomain);
    }

    @Override
    public Page<Club> searchClubs(SportType sportType, String keyword, Pageable pageable) {
        return jpaClubRepository.searchClubs(sportType, keyword, pageable).map(clubEntityMapper::toDomain);
    }

    @Override
    public void deleteById(UUID clubId) {
        jpaClubRepository.deleteById(clubId);
    }
}
