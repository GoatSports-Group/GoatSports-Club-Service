package iuh.fit.clubservice.infrastructure.external.client;

import feign.FeignException;
import iuh.fit.clubservice.application.dto.response.base.BaseResponse;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.CommunityHighlightPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocialHighlightAdapter implements CommunityHighlightPublisher {
    private final SocialHighlightClient client;

    @Override
    public UUID publishTournament(TournamentHighlight highlight) throws ServiceException {
        try {
            BaseResponse<SocialHighlightClient.PostView> response = client.publish(new SocialHighlightClient.HighlightPayload(highlight.authorId(),
                    highlight.sport() == null ? null : highlight.sport().name(), highlight.content(), "TOURNAMENT",
                    highlight.tournamentId(), highlight.title(), highlight.headline(), highlight.subtitle(),
                    highlight.result(), List.of(),
                    highlight.stats().stream().map(stat -> new SocialHighlightClient.StatPayload(stat.label(), stat.value()))
                            .toList(),
                    highlight.clubId(), highlight.clubName(), highlight.tagged()));
            SocialHighlightClient.PostView view = response == null ? null : response.getData();
            if (view == null || view.postId() == null) throw new ServiceException("Cộng đồng không trả về bài viết.");
            return view.postId();
        } catch (FeignException exception) {
            if (exception.status() >= 400 && exception.status() < 500) {
                throw new ServiceException(RemoteErrors.message(exception, "Không đăng được lên Cộng đồng."));
            }
            log.warn("Không đăng được thành tích giải {} lên Cộng đồng: {}", highlight.tournamentId(), exception.getMessage());
            throw new ServiceException("Cộng đồng đang bận, vui lòng thử lại sau ít phút.");
        } catch (RuntimeException exception) {
            log.warn("Không đăng được thành tích giải {} lên Cộng đồng: {}", highlight.tournamentId(), exception.getMessage());
            throw new ServiceException("Cộng đồng đang bận, vui lòng thử lại sau ít phút.");
        }
    }
}
