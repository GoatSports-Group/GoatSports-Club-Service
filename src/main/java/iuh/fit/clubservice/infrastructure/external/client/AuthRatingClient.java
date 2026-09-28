package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.port.external.SportRatingPort;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.infrastructure.config.AuthClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/** API nội bộ ELO / hồ sơ thể thao của auth-service (khác path với AuthClient nên tách client). */
@FeignClient(
        name = "GOATSPORTS-AUTH-SERVICE",
        contextId = "authRatingClient",
        url = "${auth.service.url}",
        path = "/internal",
        configuration = AuthClientConfig.class
)
public interface AuthRatingClient {
    @PostMapping("/sport-profiles/ensure")
    BaseResponse<Object> ensureProfile(@RequestBody EnsureRequest request);

    @PostMapping("/match-results/tournament")
    BaseResponse<List<SportRatingPort.RatedPlayer>> rateTournamentMatch(@RequestBody SportRatingPort.TournamentMatch match);

    record EnsureRequest(UUID userId, SportType sportType) { }
}
