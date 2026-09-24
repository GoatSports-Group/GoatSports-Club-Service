package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.domain.entity.Tournament;

import java.util.Map;
import java.util.UUID;

/**
 * Su kien giai dau cho notification-service (cung topic voi su kien CLB). Moi su kien mot nguoi nhan
 * vi notifications.event_id la unique toan bang. Phat loi khong duoc lam hong nghiep vu goc.
 */
public interface TournamentEventPublisher {
    String TEAM_INVITED = "tournament.team.invited.v1";
    String TEAM_ANSWERED = "tournament.team.answered.v1";
    String PAYMENT_DUE = "tournament.registration.payment-due.v1";
    String CONFIRMED = "tournament.registration.confirmed.v1";
    String CLOSED = "tournament.registration.closed.v1";

    void publish(String eventType, Tournament tournament, UUID receiverId, UUID actorId, Map<String, String> payload);
}
