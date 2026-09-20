package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.infrastructure.config.SocialClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "GOATSPORTS-SOCIAL-SERVICE",
        url = "${social.service.url}",
        path = "/internal/conversations",
        configuration = SocialClientConfig.class
)
public interface SocialClient {

    @PutMapping("/context/{contextId}")
    BaseResponse<ConversationView> syncContextConversation(@PathVariable UUID contextId,
                                                           @RequestBody SyncConversationPayload payload);

    record SyncConversationPayload(String type, String name, String avatarUrl,
                                   UUID ownerId, List<UUID> memberIds) { }

    record ConversationView(UUID conversationId, UUID contextId, String name) { }
}
