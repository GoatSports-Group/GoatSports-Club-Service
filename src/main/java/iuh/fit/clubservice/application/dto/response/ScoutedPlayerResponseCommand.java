package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.SkillLevel;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ScoutedPlayerResponseCommand {
    private UUID userId;
    private String username;
    private String fullName;
    private String avatarUrl;
    private SkillLevel skillLevel;
    private Integer eloRating;
    private Integer matchCount;
    private Double winRate;
    private Double distanceKm;
    private int fitScore;
    private boolean withinPlayerRadius;
}
