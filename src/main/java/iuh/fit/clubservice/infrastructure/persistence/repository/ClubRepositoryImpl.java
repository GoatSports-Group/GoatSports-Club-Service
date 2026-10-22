package iuh.fit.clubservice.infrastructure.persistence.repository;

import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.service.AdminCommunityService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubEntity;
import iuh.fit.clubservice.infrastructure.persistence.mapper.ClubEntityMapper;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
    public Page<Club> searchClubs(SportType sportType, String city, String keyword, Pageable pageable) {
        Specification<ClubEntity> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.isTrue(root.get("active"));

        if (sportType != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("sportType"), sportType));
        }

        if (city != null && !city.isBlank()) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("city"), city.trim()));
        }

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern)
            ));
        }

        return jpaClubRepository.findAll(specification, pageable).map(clubEntityMapper::toDomain);
    }

    @Override
    public void deleteById(UUID clubId) {
        jpaClubRepository.deleteById(clubId);
    }

    @Override
    public long countActive() {
        return jpaClubRepository.countByActiveTrueAndDisbandedAtIsNull();
    }

    @Override
    public Page<AdminCommunityService.ClubRow> searchForAdmin(boolean includeActive, boolean includeDisbanded,
                                                              Collection<SportType> sportTypes, String keyword,
                                                              Pageable pageable) {
        String pattern = "%" + (keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return jpaClubRepository.searchForAdmin(includeActive, includeDisbanded, sportTypes, pattern,
                ClubMemberStatus.ACTIVE, pageable);
    }

    @Override
    public long countDisbanded() {
        return jpaClubRepository.countByDisbandedAtIsNotNull();
    }

    @Override
    public List<Club> findFeaturedCandidates() {
        return jpaClubRepository.findByActiveTrueAndDisbandedAtIsNullAndPrivacy(ClubPrivacy.PUBLIC).stream()
                .map(clubEntityMapper::toDomain)
                .toList();
    }
}
