package iuh.fit.clubservice.infrastructure.external.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import iuh.fit.clubservice.application.port.external.TournamentEventPublisher;
import iuh.fit.clubservice.domain.entity.Tournament;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "club.events", name = "producer-enabled", havingValue = "true")
public class TournamentEventKafkaPublisher implements TournamentEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public TournamentEventKafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper,
                                         @Value("${club.events.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(String eventType, Tournament tournament, UUID receiverId, UUID actorId,
                        Map<String, String> extra) {
        if (receiverId == null) return;
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("tournamentId", tournament.getTournamentId().toString());
            payload.put("tournamentName", tournament.getName());
            payload.put("receiverId", receiverId.toString());
            extra.forEach(payload::put);

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", eventType);
            envelope.put("aggregateId", tournament.getTournamentId().toString());
            envelope.put("producer", "club-service");
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("correlationId", String.valueOf(MDC.get("correlationId")));
            envelope.put("actorId", String.valueOf(actorId));
            envelope.set("payload", payload);

            kafkaTemplate.send(topic, tournament.getTournamentId().toString(), envelope)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Không phát được sự kiện {} của giải {}: {}",
                                    eventType, tournament.getTournamentId(), error.getMessage());
                        }
                    });
        } catch (Exception exception) {
            log.warn("Không phát được sự kiện {} của giải {}: {}",
                    eventType, tournament.getTournamentId(), exception.getMessage());
        }
    }
}
