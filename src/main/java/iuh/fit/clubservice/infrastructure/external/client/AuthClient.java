package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.infrastructure.config.AuthClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@FeignClient(
        name = "GOATSPORTS-AUTH-SERVICE",
        url = "${auth.service.url}",
        path = "/internal/users",
        configuration = AuthClientConfig.class
)
public interface AuthClient {

    @GetMapping("/{userId}")
    BaseResponse<UserDetailView> getUserById(@PathVariable UUID userId);

    @GetMapping("/{userId}/sport-profiles")
    BaseResponse<List<SportProfileView>> getSportProfiles(@PathVariable UUID userId);

    @GetMapping("/scouting")
    BaseResponse<List<PlayerProfileProvider.ScoutingCandidateView>> findScoutingCandidates(
            @RequestParam SportType sportType,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double radiusKm,
            @RequestParam int limit,
            @RequestParam(required = false) Set<SkillLevel> skillLevels,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) DayOfWeek availableOn);

    @PostMapping("/scouting/lookup")
    BaseResponse<List<PlayerProfileProvider.ScoutingCandidateView>> lookupScoutingProfiles(
            @RequestBody ScoutingLookup request);

    record ScoutingLookup(SportType sportType, double latitude, double longitude, List<UUID> userIds) { }

    record UserDetailView(UUID userId, String fullName, LocalDate dateOfBirth, String gender) { }

    record SportProfileView(SportType sportType, SkillLevel skillLevel, Integer eloRating) { }
}
