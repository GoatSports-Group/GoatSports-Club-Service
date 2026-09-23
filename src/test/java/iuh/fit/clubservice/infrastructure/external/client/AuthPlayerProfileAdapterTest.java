package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider.ScoutingFilter;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import iuh.fit.clubservice.domain.enums.SportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private final ScoutingFilter anyFilter = new ScoutingFilter(
            SportType.BADMINTON, 12.239, 109.196, 25, 24, Set.of(), "  ", null);

    private PlayerProfileProvider.ScoutingCandidateView candidate(UUID userId) {
        return new PlayerProfileProvider.ScoutingCandidateView(
                userId, "player", "Nguoi choi", null, true, SkillLevel.INTERMEDIATE, 1200, Set.of(),
                null, "Khánh Hòa", 15.0, 6, 5, 1, 12, 0.5, List.of(), 3.2);
    }

    private static <T> BaseResponse<T> envelope(T data) {
        BaseResponse<T> response = new BaseResponse<>();
        response.setData(data);
        return response;
    }

    @Test
    @DisplayName("Mo boc BaseResponse; bo loc rong thi khong gui len auth-service")
    void unwrapsTheEnvelopeAndOmitsEmptyFilters() throws ServiceException {
        UUID userId = UUID.randomUUID();
        when(authClient.findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any(), any()))
                .thenReturn(envelope(List.of(candidate(userId))));

        List<PlayerProfileProvider.ScoutingCandidateView> result = adapter.findScoutingCandidates(anyFilter);

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.userId()).isEqualTo(userId);
            assertThat(view.distanceKm()).isEqualTo(3.2);
        });
        verify(authClient).findScoutingCandidates(eq(SportType.BADMINTON), anyDouble(), anyDouble(), anyDouble(),
                eq(24), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("Body rong hoac khong co data thi tra danh sach rong")
    void emptyEnvelopeYieldsNoCandidates() throws ServiceException {
        when(authClient.findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any(), any()))
                .thenReturn(new BaseResponse<>());

        assertThat(adapter.findScoutingCandidates(anyFilter)).isEmpty();
    }

    @Test
    @DisplayName("auth-service hong thi bao loi ro rang, khong gia vo la khong co nguoi choi")
    void authFailureIsReportedNotHidden() {
        when(authClient.findScoutingCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any(), any()))
                .thenThrow(new IllegalStateException("auth-service khong phan hoi"));

        assertThatThrownBy(() -> adapter.findScoutingCandidates(anyFilter))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("thử lại");
    }

    @Test
    @DisplayName("Lookup rong thi khong goi auth-service")
    void emptyLookupSkipsTheCall() throws ServiceException {
        assertThat(adapter.lookupScoutingProfiles(SportType.BADMINTON, 12.2, 109.1, List.of())).isEmpty();
        verify(authClient, never()).lookupScoutingProfiles(any());
    }
}
