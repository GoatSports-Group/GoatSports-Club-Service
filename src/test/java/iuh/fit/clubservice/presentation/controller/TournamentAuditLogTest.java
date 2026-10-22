package iuh.fit.clubservice.presentation.controller;

import iuh.fit.clubservice.application.port.security.CurrentUserProvider;
import iuh.fit.clubservice.application.service.TournamentFixtureService;
import iuh.fit.clubservice.application.service.TournamentRegistrationService;
import iuh.fit.clubservice.application.service.TournamentRevenueService;
import iuh.fit.clubservice.application.service.TournamentScheduleService;
import iuh.fit.clubservice.application.service.TournamentService;
import iuh.fit.clubservice.application.service.TournamentTeamService;
import iuh.fit.clubservice.presentation.converter.TournamentConverter;
import iuh.fit.clubservice.presentation.request.UpdateFixtureResultRequest;
import iuh.fit.shared.annotation.AuditLog;
import iuh.fit.shared.aspect.AuditLogAspect;
import iuh.fit.shared.dto.ApiLogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sửa tỷ số giải phải vào nhật ký kiểm toán, ghi đúng người sửa (không phải người trong dữ liệu trả về). */
class TournamentAuditLogTest {

    private final UUID caller = UUID.randomUUID();
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void scoreCorrectionIsAuditedWithTheScoreAndTheCaller() throws Exception {
        CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
        when(currentUser.getCurrentUserId()).thenReturn(Optional.of(caller));
        TournamentController controller = new TournamentController(mock(TournamentService.class),
                mock(TournamentRevenueService.class), mock(TournamentFixtureService.class),
                mock(TournamentScheduleService.class), mock(TournamentRegistrationService.class),
                mock(TournamentTeamService.class), mock(TournamentConverter.class), currentUser);
        UUID tournamentId = UUID.randomUUID();
        UUID fixtureId = UUID.randomUUID();

        proxied(controller).result(tournamentId, fixtureId,
                UpdateFixtureResultRequest.builder().score1(3).score2(1).build());

        ApiLogEvent event = sentEvent();
        assertThat(event.getAction()).isEqualTo("RECORD_TOURNAMENT_RESULT");
        assertThat(event.getUserId()).isEqualTo(caller.toString());
        assertThat(event.getDescription()).contains("3-1").contains(fixtureId.toString());
    }

    @Test
    void theAffectedMemberInTheResultIsNotLoggedAsTheActor() {
        proxied(new MemberAction()).changeRole(UUID.randomUUID());

        assertThat(sentEvent().getUserId()).isEqualTo(caller.toString());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxied(T target) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/tournaments");
        request.addHeader("X-User-Id", caller.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        when(kafka.send(anyString(), any())).thenReturn(new CompletableFuture<>());

        AuditLogAspect aspect = new AuditLogAspect(kafka);
        ReflectionTestUtils.setField(aspect, "userLogTopic", "user-log");
        ReflectionTestUtils.setField(aspect, "applicationName", "GOATSPORTS-CLUB-SERVICE");
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return (T) factory.getProxy();
    }

    private ApiLogEvent sentEvent() {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(anyString(), event.capture());
        return (ApiLogEvent) event.getValue();
    }

    public static class MemberAction {
        public record Member(String userId) { }

        @AuditLog(action = "CHANGE_CLUB_MEMBER_ROLE", description = "Đổi vai trò thành viên #{#membershipId}")
        public Member changeRole(UUID membershipId) {
            return new Member("victim-" + membershipId);
        }
    }
}
