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
            String referenceType = payload.path("referenceType").asText();
            boolean succeeded = eventType.equals("payment.succeeded.v1");
            LocalDateTime paidAtValue = dateTime(payload.path("paidAt"));

            if ("TOURNAMENT_REGISTRATION".equals(referenceType)
                    && "TOURNAMENT_FEE".equals(payload.path("purpose").asText())) {
                service.apply(uuid(payload, "paymentId"), uuid(payload, "payerId"), uuid(payload, "referenceId"),
                        payload.path("amount").asDouble(), payload.path("currency").asText(),
                        succeeded, paidAtValue);
            }
        } catch (ServiceException exception) { throw new IllegalStateException(exception.getMessage(), exception); }
        catch (Exception exception) { throw new IllegalArgumentException("Payment event không đúng contract.", exception); }
    }
    private UUID uuid(JsonNode node, String field) { return UUID.fromString(node.path(field).asText()); }

    /**
     * payment-service gửi LocalDateTime qua JsonSerializer mặc định của Spring Kafka, nên có thể là mảng
     * [năm, tháng, ngày, giờ, phút, giây, nano] thay vì chuỗi ISO. Đọc cả hai dạng; thiếu thì trả null.
     */
    static LocalDateTime dateTime(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isArray()) {
            int[] part = new int[7];
            for (int i = 0; i < Math.min(7, node.size()); i++) part[i] = node.get(i).asInt();
            return LocalDateTime.of(part[0], part[1], part[2], part[3], part[4], part[5], part[6]);
        }
        String text = node.asText();
        return text.isBlank() ? null : LocalDateTime.parse(text.replace(' ', 'T'));
    }
}
