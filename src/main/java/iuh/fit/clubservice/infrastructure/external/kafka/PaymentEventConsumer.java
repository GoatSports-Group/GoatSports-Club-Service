package iuh.fit.clubservice.infrastructure.external.kafka;

import com.fasterxml.jackson.databind.*;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.service.TournamentPaymentEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.events", name = "consumer-enabled", havingValue = "true")
public class PaymentEventConsumer {
    private final ObjectMapper objectMapper;
    private final TournamentPaymentEventService service;

    @KafkaListener(topics = "${payment.events.topic}", groupId = "${payment.events.club-group-id}")
    public void consume(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            if (!eventType.equals("payment.succeeded.v1") && !eventType.equals("payment.failed.v1")) return;
            JsonNode payload = envelope.path("payload");
            if (!"TOURNAMENT_REGISTRATION".equals(payload.path("referenceType").asText())
                    || !"TOURNAMENT_FEE".equals(payload.path("purpose").asText())) return;
            String paidAt = payload.path("paidAt").asText(null);
            service.apply(uuid(payload, "paymentId"), uuid(payload, "payerId"), uuid(payload, "referenceId"),
                    payload.path("amount").asDouble(), payload.path("currency").asText(),
                    eventType.equals("payment.succeeded.v1"), paidAt == null ? null : LocalDateTime.parse(paidAt));
        } catch (ServiceException exception) { throw new IllegalStateException(exception.getMessage(), exception); }
        catch (Exception exception) { throw new IllegalArgumentException("Payment event không đúng contract.", exception); }
    }
    private UUID uuid(JsonNode node, String field) { return UUID.fromString(node.path(field).asText()); }
}
