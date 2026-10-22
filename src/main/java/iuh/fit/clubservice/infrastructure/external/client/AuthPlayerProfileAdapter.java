package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.domain.enums.SportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthPlayerProfileAdapter implements PlayerProfileProvider {
    private final AuthClient authClient;

    @Override
    public List<ScoutingCandidateView> findScoutingCandidates(ScoutingFilter filter) throws ServiceException {
        return call(() -> authClient.findScoutingCandidates(
                filter.sportType(), filter.latitude(), filter.longitude(), filter.radiusKm(), filter.limit(),
                filter.skillLevels() == null || filter.skillLevels().isEmpty() ? null : filter.skillLevels(),
                filter.position() == null || filter.position().isBlank() ? null : filter.position().trim(),
                filter.availableOn()));
    }

    @Override
    public List<ScoutingCandidateView> lookupScoutingProfiles(
            SportType sportType, double latitude, double longitude, Collection<UUID> userIds) throws ServiceException {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return call(() -> authClient.lookupScoutingProfiles(
                new AuthClient.ScoutingLookup(sportType, latitude, longitude, List.copyOf(userIds))));
    }

    private List<ScoutingCandidateView> call(Supplier<BaseResponse<List<ScoutingCandidateView>>> request)
            throws ServiceException {
        try {
            List<ScoutingCandidateView> players = unwrap(request.get());
            return players == null ? List.of() : players;
        } catch (RuntimeException exception) {
            log.warn("Khong lay duoc ho so scouting: {}", exception.getMessage());
            throw new ServiceException("Chưa tải được hồ sơ người chơi. Vui lòng thử lại sau.");
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

    @Override
    public Optional<String> findFullName(UUID userId) {
        try {
            AuthClient.UserDetailView user = unwrap(authClient.getUserById(userId));
            return user == null || user.fullName() == null || user.fullName().isBlank()
                    ? Optional.empty() : Optional.of(user.fullName().trim());
        } catch (RuntimeException exception) {
            log.warn("Không đọc được tên người chơi {}: {}", userId, exception.getMessage());
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

    private <T> T unwrap(BaseResponse<T> response) {
        return response == null ? null : response.getData();
    }
}
