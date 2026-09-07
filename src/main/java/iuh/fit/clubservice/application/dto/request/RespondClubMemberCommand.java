package iuh.fit.clubservice.application.dto.request;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespondClubMemberCommand {
    private UUID membershipId;
    private UUID actorUserId;
    private boolean accepted;
}
