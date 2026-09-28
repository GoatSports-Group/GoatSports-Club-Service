package iuh.fit.clubservice.presentation.controller.internal;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.service.ClubMatchmakingService;
import iuh.fit.clubservice.domain.enums.SportType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Ghép trận CLB đấu CLB: ai-service lấy hồ sơ CLB và ghi kết quả giao hữu. Gateway chặn /club-service/internal/**. */
@RestController
@RequestMapping("/internal/clubs")
@RequiredArgsConstructor
public class ClubMatchmakingInternalController {
    private final ClubMatchmakingService matchmakingService;

    @GetMapping("/{clubId}/matchmaking-profile")
    public ResponseEntity<ClubMatchmakingService.Profile> profile(@PathVariable UUID clubId) {
        return ResponseEntity.of(matchmakingService.getProfile(clubId));
    }

    @PostMapping("/friendly-results")
    public Map<UUID, Integer> friendlyResult(@Valid @RequestBody FriendlyResultRequest request) throws ServiceException {
        return matchmakingService.applyFriendlyResult(new ClubMatchmakingService.FriendlyResult(
                request.sessionId(), request.sportType(), request.playFormat(),
                new ClubMatchmakingService.Side(request.first().clubId(), request.first().score()),
                new ClubMatchmakingService.Side(request.second().clubId(), request.second().score())));
    }

    public record SideRequest(@NotNull UUID clubId, @NotNull @Min(0) @Max(999) Integer score) { }

    public record FriendlyResultRequest(
            @NotNull UUID sessionId,
            @NotNull SportType sportType,
            @Size(max = 40) String playFormat,
            @NotNull @Valid SideRequest first,
            @NotNull @Valid SideRequest second
    ) { }
}
