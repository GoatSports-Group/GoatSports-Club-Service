package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.domain.enums.SkillLevel;
import iuh.fit.clubservice.domain.enums.SportType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileProvider {
    Optional<PlayerProfileView> findProfile(UUID userId, SportType sportType);

    List<ScoutingCandidateView> findScoutingCandidates(
            SportType sportType, double latitude, double longitude, double radiusKm, int limit);

    record ScoutingCandidateView(
            UUID userId,
            String username,
            String fullName,
            String avatarUrl,
            SkillLevel skillLevel,
            Integer eloRating,
            Integer matchCount,
            Double winRate,
            Double playRadiusKm,
            Double distanceKm
    ) { }

    record PlayerProfileView(
            UUID userId,
            LocalDate dateOfBirth,
            String gender,
            SkillLevel skillLevel,
            Integer eloRating
    ) { }
}
