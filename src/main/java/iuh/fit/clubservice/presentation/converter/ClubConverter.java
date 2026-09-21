package iuh.fit.clubservice.presentation.converter;

import iuh.fit.clubservice.application.dto.request.*;
import iuh.fit.clubservice.presentation.request.*;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ClubConverter {
    public CreateClubCommand toCommand(UUID ownerId, CreateClubRequest request) {
        return CreateClubCommand.builder().ownerId(ownerId).name(request.getName())
                .description(request.getDescription()).logoUrl(request.getLogoUrl())
                .city(request.getCity()).location(request.getLocation()).sportType(request.getSportType())
                .privacy(request.getPrivacy()).approvalMode(request.getApprovalMode()).build();
    }
    public JoinClubCommand toCommand(UUID clubId, UUID userId, JoinClubRequest request) {
        return JoinClubCommand.builder().clubId(clubId).userId(userId)
                .introMessage(request == null ? null : request.getMessage()).build();
    }
    public RespondClubMemberCommand toCommand(UUID membershipId, UUID actorId, RespondClubMemberRequest request) {
        return RespondClubMemberCommand.builder().membershipId(membershipId).actorUserId(actorId)
                .accepted(request.isAccepted()).build();
    }
    public CreateClubActivityCommand toCommand(UUID clubId, UUID creatorId, CreateClubActivityRequest request) {
        return CreateClubActivityCommand.builder().clubId(clubId).creatorId(creatorId).title(request.getTitle())
                .description(request.getDescription()).venueId(request.getVenueId())
                .startAt(request.getStartAt()).endAt(request.getEndAt()).build();
    }
}
