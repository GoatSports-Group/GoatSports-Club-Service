package iuh.fit.clubservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubRecentMatchResponseCommand {
    private UUID matchId;
    private UUID tournamentId;
    private String tournamentName;
    private String opponentName;
    private LocalDateTime playedAt;
    private Integer clubScore;
    private Integer opponentScore;
    private String result;
    /** Trận giao hữu CLB đấu CLB do AI ghép (không thuộc giải nào, tournamentId = null). */
    private boolean friendly;
    /** ELO của CLB thay đổi bao nhiêu sau trận giao hữu; null với trận giải đấu. */
    private Integer eloChange;
}
