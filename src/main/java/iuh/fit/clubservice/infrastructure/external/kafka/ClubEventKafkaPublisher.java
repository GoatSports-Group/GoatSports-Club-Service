package iuh.fit.clubservice.infrastructure.external.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import iuh.fit.clubservice.application.port.external.ClubEventPublisher;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubActivity;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "club.events", name = "producer-enabled", havingValue = "true")
public class ClubEventKafkaPublisher implements ClubEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public ClubEventKafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${club.events.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void activityCreated(Club club, ClubActivity activity, List<UUID> receiverIds) {
        if (receiverIds == null) return;
        for (UUID receiverId : receiverIds) {
            if (receiverId == null) continue;
            emit("club.activity.created.v1", club, receiverId, activity.getCreatedByUserId(), payload -> {
                payload.put("activityId", activity.getActivityId().toString());
                payload.put("title", activity.getTitle());
                payload.put("startAt", String.valueOf(activity.getStartAt()));
            });
        }
    }

    @Override
    public void membershipRequested(Club club, ClubMember member, String requesterName, List<UUID> managerIds) {
        if (managerIds == null) return;
        for (UUID managerId : managerIds) {
            if (managerId == null || managerId.equals(member.getUserId())) continue;
            emit("club.membership.requested.v1", club, managerId, member.getUserId(), payload -> {
                payload.put("membershipId", member.getMembershipId().toString());
                payload.put("requesterId", member.getUserId().toString());
                payload.put("requesterName", requesterName == null ? "" : requesterName);
            });
        }
    }

    @Override
    public void membershipResponded(Club club, ClubMember member, boolean accepted) {
        String eventType = accepted
                ? "club.membership.approved.v1"
                : "club.membership.rejected.v1";
        emit(eventType, club, member.getUserId(), member.getRespondedBy(), payload ->
                payload.put("membershipId", member.getMembershipId().toString()));
    }

    @Override
    public void invitationSent(Club club, ClubInvitation invitation) {
        emit("club.invitation.sent.v1", club, invitation.getInviteeId(), invitation.getInvitedBy(), payload -> {
            payload.put("invitationId", invitation.getInvitationId().toString());
            payload.put("message", invitation.getMessage() == null ? "" : invitation.getMessage());
        });
    }

    @Override
    public void invitationAnswered(Club club, ClubInvitation invitation, boolean accepted) {
        String eventType = accepted
                ? "club.invitation.accepted.v1"
                : "club.invitation.declined.v1";
        emit(eventType, club, invitation.getInvitedBy(), invitation.getInviteeId(), payload -> {
            payload.put("invitationId", invitation.getInvitationId().toString());
            payload.put("inviteeId", invitation.getInviteeId().toString());
        });
    }

    private void emit(String eventType, Club club, UUID receiverId, UUID actorId,
                      Consumer<ObjectNode> extraPayload) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("clubId", club.getClubId().toString());
            payload.put("clubName", club.getName());
            payload.put("receiverId", receiverId.toString());
            extraPayload.accept(payload);

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", eventType);
            envelope.put("aggregateId", club.getClubId().toString());
            envelope.put("producer", "club-service");
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("correlationId", String.valueOf(MDC.get("correlationId")));
            envelope.put("actorId", String.valueOf(actorId));
            envelope.set("payload", payload);

            // The configured JsonSerializer must receive the JSON object itself.
            // Sending writeValueAsString(envelope) would serialize the JSON a second
            // time, so consumers would receive a quoted string instead of an object.
            kafkaTemplate.send(topic, club.getClubId().toString(), envelope)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Không phát được sự kiện {} của CLB {}: {}",
                                    eventType, club.getClubId(), error.getMessage());
                        }
                    });
        } catch (Exception exception) {
            // Thong bao tre hon mot nhip khong duoc lam hong nghiep vu goc.
            log.warn("Không phát được sự kiện {} của CLB {}: {}",
                    eventType, club.getClubId(), exception.getMessage());
        }
    }
}
