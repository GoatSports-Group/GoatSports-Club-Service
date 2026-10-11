package iuh.fit.clubservice.application.port.persistence;

import iuh.fit.clubservice.application.service.AdminCommunityService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.enums.SportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ClubRepository {
    Club save(Club club);

    Optional<Club> findById(UUID clubId);

    Page<Club> searchClubs(SportType sportType, String city, String keyword, Pageable pageable);

    void deleteById(UUID clubId);

    java.util.List<Club> findFeaturedCandidates();

    long countActive();

    long countDisbanded();

    Page<AdminCommunityService.ClubRow> searchForAdmin(
            boolean includeActive, boolean includeDisbanded, Collection<SportType> sportTypes,
            String keyword, Pageable pageable);
}
