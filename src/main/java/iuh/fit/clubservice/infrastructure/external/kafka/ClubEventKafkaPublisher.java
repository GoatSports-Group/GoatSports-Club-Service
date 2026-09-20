package iuh.fit.clubservice.infrastructure.external.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import iuh.fit.clubservice.application.port.external.ClubEventPublisher;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubActivity;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "club.events", name = "producer-enabled", havingValue = "true")
public class ClubEventKafkaPublisher implements ClubEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public ClubEventKafkaPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${club.events.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void activityCreated(Club club, ClubActivity activity, List<UUID> receiverIds) {
        if (receiverIds == null || receiverIds.isEmpty()) return;
        for (UUID receiverId : receiverIds) {
            if (receiverId != null) publish(club, activity, receiverId);
        }
    }

    private void publish(Club club, ClubActivity activity, UUID receiverId) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("clubId", club.getClubId().toString());
            payload.put("clubName", club.getName());
            payload.put("activityId", activity.getActivityId().toString());
            payload.put("title", activity.getTitle());
            payload.put("receiverId", receiverId.toString());
            payload.put("startAt", String.valueOf(activity.getStartAt()));

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", "club.activity.created.v1");
            envelope.put("aggregateId", club.getClubId().toString());
            envelope.put("producer", "club-service");
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("correlationId", String.valueOf(MDC.get("correlationId")));
            envelope.put("actorId", String.valueOf(activity.getCreatedByUserId()));
            envelope.set("payload", payload);

            kafkaTemplate.send(topic, club.getClubId().toString(),
                    objectMapper.writeValueAsString(envelope));
        } catch (Exception exception) {
            // Thong bao tre hon mot nhip khong duoc lam hong viec tao hoat dong.
            log.warn("Không phát được sự kiện hoạt động CLB {}: {}",
                    club.getClubId(), exception.getMessage());
        }
    }
}
