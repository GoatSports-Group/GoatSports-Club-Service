package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentEntity;
import iuh.fit.clubservice.infrastructure.persistence.mapper.TournamentEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaTournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TournamentRepositoryImpl implements TournamentRepository {
    private final JpaTournamentRepository jpaTournamentRepository;
    private final TournamentEntityMapper tournamentEntityMapper;

    @Override
    public Tournament save(Tournament tournament) {
        return tournamentEntityMapper.toDomain(jpaTournamentRepository.save(tournamentEntityMapper.toEntity(tournament)));
    }

    @Override
    public Optional<Tournament> findById(UUID tournamentId) {
        return jpaTournamentRepository.findById(tournamentId).map(tournamentEntityMapper::toDomain);
    }

    @Override
    public Optional<Tournament> findByIdForUpdate(UUID tournamentId) {
        return jpaTournamentRepository.findByIdForUpdate(tournamentId).map(tournamentEntityMapper::toDomain);
    }

    @Override
    public Page<Tournament> searchTournaments(SportType sportType, TournamentStatus status, String keyword, Pageable pageable) {
        Specification<TournamentEntity> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.conjunction();

        if (sportType != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("sportType"), sportType));
        }

        if (status != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), status));
        }

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern));
        }

        return jpaTournamentRepository.findAll(specification, pageable).map(tournamentEntityMapper::toDomain);
    }

    @Override
    public void deleteById(UUID tournamentId) {
        jpaTournamentRepository.deleteById(tournamentId);
    }
}
