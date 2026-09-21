package iuh.fit.clubservice.infrastructure.external.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClubEventKafkaPublisherTest {

    @Test
    void membershipRequestPublishesJsonObjectInsteadOfDoubleEncodedString() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(eq("club-events-topic"), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        ClubEventKafkaPublisher publisher = new ClubEventKafkaPublisher(
                kafkaTemplate,
                new ObjectMapper(),
                "club-events-topic"
        );
        UUID clubId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Club club = Club.builder().clubId(clubId).ownerId(ownerId).name("Weekend Club").build();
        ClubMember member = ClubMember.builder()
                .membershipId(UUID.randomUUID())
                .clubId(clubId)
                .userId(requesterId)
                .build();

        publisher.membershipRequested(club, member, "GOAT Player", List.of(ownerId));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("club-events-topic"), eq(clubId.toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(JsonNode.class);
        JsonNode envelope = (JsonNode) payload.getValue();
        assertThat(envelope.path("eventType").asText()).isEqualTo("club.membership.requested.v1");
        assertThat(envelope.path("payload").path("receiverId").asText()).isEqualTo(ownerId.toString());
    }
}
