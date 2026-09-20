package iuh.fit.clubservice.infrastructure.external.client;

import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.Tournament;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocialClubConversationSync implements ClubConversationSync {
    private final SocialClient socialClient;

    @Override
    public Optional<UUID> sync(Club club, List<UUID> activeMemberIds) {
        return send(club.getClubId(), "CLUB", club.getName(), club.getLogoUrl(),
                club.getOwnerId(), activeMemberIds, "CLB");
    }

    @Override
    public Optional<UUID> syncTournament(Tournament tournament, List<UUID> participantIds) {
        // Giai dau khong co anh dai dien rieng nen de null; social-service giu nguyen anh cu.
        return send(tournament.getTournamentId(), "TOURNAMENT", tournament.getName(), null,
                tournament.getOrganizerId(), participantIds, "giải đấu");
    }

    private Optional<UUID> send(UUID contextId, String type, String name, String avatarUrl,
                                UUID ownerId, List<UUID> memberIds, String label) {
        try {
            var response = socialClient.syncContextConversation(contextId,
                    new SocialClient.SyncConversationPayload(type, name, avatarUrl, ownerId, memberIds));
            SocialClient.ConversationView view = response == null ? null : response.getData();
            return view == null ? Optional.empty() : Optional.ofNullable(view.conversationId());
        } catch (RuntimeException exception) {
            // Chat tre hon mot nhip khong duoc lam hong viec tao CLB, duyet thanh vien hay dang ky giai.
            // Moi lan danh sach doi deu dong bo lai toan bo nen lan loi nay tu khoi o lan sau.
            log.warn("Không đồng bộ được chat nhóm cho {} {}: {}", label, contextId, exception.getMessage());
            return Optional.empty();
        }
    }
}
