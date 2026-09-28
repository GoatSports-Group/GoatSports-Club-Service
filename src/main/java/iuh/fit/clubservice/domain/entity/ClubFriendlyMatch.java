package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.SportType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một trận giao hữu CLB đấu CLB do AI ghép, nhìn từ phía một CLB (mỗi phiên ghép trận có hai dòng). */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClubFriendlyMatch extends BaseEntity {
    private UUID friendlyMatchId;
    private UUID sessionId;
    private UUID clubId;
    private UUID opponentClubId;
    private String opponentName;
    private SportType sportType;
    private String playFormat;
    private Integer clubScore;
    private Integer opponentScore;
    private String result;
    private Integer eloBefore;
    private Integer eloAfter;
    private LocalDateTime playedAt;
}
