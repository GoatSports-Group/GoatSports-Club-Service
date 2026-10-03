package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.infrastructure.config.SocialClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "GOATSPORTS-SOCIAL-SERVICE",
        contextId = "socialHighlightClient",
        url = "${social.service.url}",
        path = "/internal/posts",
        configuration = SocialClientConfig.class
)
public interface SocialHighlightClient {

    @PostMapping("/highlights")
    BaseResponse<PostView> publish(@RequestBody HighlightPayload payload);

    record HighlightPayload(UUID authorId, String sport, String content, String kind, UUID refId, String title,
                            String headline, String subtitle, String result, List<Object> sides,
                            List<StatPayload> stats, UUID clubId, String clubName, List<UUID> tagged) { }

    record StatPayload(String label, String value) { }

    record PostView(UUID postId) { }
}
