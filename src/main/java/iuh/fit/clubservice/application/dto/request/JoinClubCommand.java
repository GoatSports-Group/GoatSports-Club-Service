package iuh.fit.clubservice.application.dto.request;

import lombok.*;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class JoinClubCommand {
    private UUID clubId;
    private UUID userId;
    private String introMessage;
}
