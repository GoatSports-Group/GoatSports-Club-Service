package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import iuh.fit.clubservice.domain.enums.SportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * auth-service boc moi response trong BaseResponse qua ResponseCustom (ResponseBodyAdvice,
 * supports() luon tra true). Khai bao Feign tra ve List tran se hong luc giai ma, va loi do
 * chi lo ra khi chay that. Cac test o day giu dung hop dong boc/mo boc do.
 */
@ExtendWith(MockitoExtension.class)
class AuthPlayerProfileAdapterTest {
    @Mock private AuthClient authClient;
    @InjectMocks private AuthPlayerProfileAdapter adapter;

    private AuthClient.ScoutingCandidateView candidate(UUID userId) {
        return new AuthClient.ScoutingCandidateView(
                userId, "player", "Nguoi choi", null, SkillLevel.INTERMEDIATE,
                1200, 12, 0.5, 15.0, 3.2);
    }

    @Test
    @DisplayName("Mo boc BaseResponse de lay danh sach ung vien")
    void unwrapsTheBaseResponseEnvelope() {
        UUID userId = UUID.randomUUID();
        BaseResponse<List<AuthClient.ScoutingCandidateView>> response = new BaseResponse<>();
        response.setData(List.of(candidate(userId)));
        when(authClient.findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(response);

        List<PlayerProfileProvider.ScoutingCandidateView> result =
                adapter.findScoutingCandidates(SportType.BADMINTON, 12.239, 109.196, 25, 24);

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.userId()).isEqualTo(userId);
            assertThat(view.distanceKm()).isEqualTo(3.2);
            assertThat(view.playRadiusKm()).isEqualTo(15.0);
        });
    }

    @Test
    @DisplayName("Body rong hoac khong co data thi tra danh sach rong")
    void emptyEnvelopeYieldsNoCandidates() {
        when(authClient.findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(new BaseResponse<>());

        assertThat(adapter.findScoutingCandidates(SportType.BADMINTON, 12.239, 109.196, 25, 24)).isEmpty();
    }

    @Test
    @DisplayName("auth-service hong thi tra danh sach rong, khong nem loi lam vo trang CLB")
    void authFailureDegradesToEmptyList() {
        when(authClient.findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new IllegalStateException("auth-service khong phan hoi"));

        assertThat(adapter.findScoutingCandidates(SportType.BADMINTON, 12.239, 109.196, 25, 24)).isEmpty();
    }
}
