package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import iuh.fit.clubservice.domain.enums.SportType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PlayerProfileProvider {
    Optional<PlayerProfileView> findProfile(UUID userId, SportType sportType);

    List<ScoutingCandidateView> findScoutingCandidates(ScoutingFilter filter) throws ServiceException;

    List<ScoutingCandidateView> lookupScoutingProfiles(
            SportType sportType, double latitude, double longitude, Collection<UUID> userIds) throws ServiceException;

    record ScoutingFilter(
            SportType sportType,
            double latitude,
            double longitude,
            double radiusKm,
            int limit,
            Set<SkillLevel> skillLevels,
            String position,
            DayOfWeek availableOn
    ) { }

    record ScoutingCandidateView(
            UUID userId,
            String username,
            String fullName,
            String avatarUrl,
            boolean profileVisible,
            SkillLevel skillLevel,
            Integer eloRating,
            Set<String> preferredPositions,
            String playStyle,
            String city,
            Double playRadiusKm,
            Integer winCount,
            Integer lossCount,
            Integer drawCount,
            Integer matchCount,
            Double winRate,
            List<AvailabilityView> availabilities,
            Double distanceKm
    ) { }

    record AvailabilityView(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) { }

    record PlayerProfileView(
            UUID userId,
            LocalDate dateOfBirth,
            String gender,
            SkillLevel skillLevel,
            Integer eloRating
    ) { }
}
