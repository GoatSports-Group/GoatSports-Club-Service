package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.infrastructure.config.AuthClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
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
    BaseResponse<List<ScoutingCandidateView>> findScoutingCandidates(
            @RequestParam SportType sportType,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double radiusKm,
            @RequestParam int limit);

    record UserDetailView(UUID userId, String fullName, LocalDate dateOfBirth, String gender) { }

    record ScoutingCandidateView(UUID userId, String username, String fullName, String avatarUrl,
                                 SkillLevel skillLevel, Integer eloRating, Integer matchCount,
                                 Double winRate, Double playRadiusKm, Double distanceKm) { }

    record SportProfileView(SportType sportType, SkillLevel skillLevel, Integer eloRating) { }
}
