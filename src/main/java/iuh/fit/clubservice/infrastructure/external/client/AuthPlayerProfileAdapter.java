package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.domain.enums.SportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthPlayerProfileAdapter implements PlayerProfileProvider {
    private final AuthClient authClient;

    @Override
    public List<ScoutingCandidateView> findScoutingCandidates(
            SportType sportType, double latitude, double longitude, double radiusKm, int limit) {
        try {
            BaseResponse<List<AuthClient.ScoutingCandidateView>> response =
                    authClient.findScoutingCandidates(sportType, latitude, longitude, radiusKm, limit);
            List<AuthClient.ScoutingCandidateView> candidates = response == null ? null : response.getData();
            if (candidates == null) return List.of();
            return candidates.stream()
                    .map(candidate -> new ScoutingCandidateView(
                            candidate.userId(), candidate.username(), candidate.fullName(), candidate.avatarUrl(),
                            candidate.skillLevel(), candidate.eloRating(), candidate.matchCount(),
                            candidate.winRate(), candidate.playRadiusKm(), candidate.distanceKm()))
                    .toList();
        } catch (Exception exception) {
            log.warn("Khong lay duoc ung vien scouting: {}", exception.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<PlayerProfileView> findProfile(UUID userId, SportType sportType) {
        try {
            AuthClient.UserDetailView user = unwrap(authClient.getUserById(userId));
            if (user == null) return Optional.empty();
            AuthClient.SportProfileView profile = pickProfile(userId, sportType);
            return Optional.of(new PlayerProfileView(
                    userId,
                    user.dateOfBirth(),
                    user.gender(),
                    profile == null ? null : profile.skillLevel(),
                    profile == null ? null : profile.eloRating()
            ));
        } catch (RuntimeException exception) {
            // Khong doan du lieu khi auth-service loi; goi tang tren se fail-closed neu rule can.
            log.warn("Không đọc được hồ sơ người chơi {}: {}", userId, exception.getMessage());
            return Optional.empty();
        }
    }

    private AuthClient.SportProfileView pickProfile(UUID userId, SportType sportType) {
        List<AuthClient.SportProfileView> profiles = unwrap(authClient.getSportProfiles(userId));
        if (profiles == null || profiles.isEmpty()) return null;
        return profiles.stream()
                .filter(item -> sportType == null || item.sportType() == sportType)
                .findFirst()
                .orElse(null);
    }

    private <T> T unwrap(iuh.fit.clubservice.application.dto.response.base.BaseResponse<T> response) {
        return response == null ? null : response.getData();
    }
}
