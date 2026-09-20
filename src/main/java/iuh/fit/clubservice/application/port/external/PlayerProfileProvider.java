package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.domain.enums.SkillLevel;
import iuh.fit.clubservice.domain.enums.SportType;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileProvider {
    Optional<PlayerProfileView> findProfile(UUID userId, SportType sportType);

    record PlayerProfileView(
            UUID userId,
            LocalDate dateOfBirth,
            String gender,
            SkillLevel skillLevel,
            Integer eloRating
    ) { }
}
