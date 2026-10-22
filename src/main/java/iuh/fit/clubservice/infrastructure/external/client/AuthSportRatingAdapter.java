package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.SportRatingPort;
import iuh.fit.clubservice.domain.enums.SportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSportRatingAdapter implements SportRatingPort {
    private final AuthRatingClient client;

    @Override
    public void ensureProfile(UUID userId, SportType sportType) throws ServiceException {
        try {
            client.ensureProfile(new AuthRatingClient.EnsureRequest(userId, sportType));
        } catch (RuntimeException exception) {
            log.warn("Không tạo được hồ sơ {} cho {}: {}", sportType, userId, exception.getMessage());
            throw new ServiceException("Chưa tạo được hồ sơ thể thao.");
        }
    }

    @Override
    public List<RatedPlayer> rateTournamentMatch(TournamentMatch match) throws ServiceException {
        try {
            BaseResponse<List<RatedPlayer>> response = client.rateTournamentMatch(match);
            return response == null || response.getData() == null ? List.of() : response.getData();
        } catch (RuntimeException exception) {
            log.warn("Không tính được ELO cho trận {}: {}", match.fixtureId(), exception.getMessage());
            throw new ServiceException("Chưa cập nhật được ELO của trận đấu.");
        }
    }
}
