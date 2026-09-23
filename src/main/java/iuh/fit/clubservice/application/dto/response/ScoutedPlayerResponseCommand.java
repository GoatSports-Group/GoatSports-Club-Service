package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class ScoutedPlayerResponseCommand {
    private UUID userId;
    private String username;
    private String fullName;
    private String avatarUrl;
    private boolean profileVisible;
    private SkillLevel skillLevel;
    private Integer eloRating;
    private Set<String> preferredPositions;
    private String playStyle;
    private String city;
    private Integer matchCount;
    private Integer winCount;
    private Integer lossCount;
    private Integer drawCount;
    private Double winRate;
    private List<PlayerProfileProvider.AvailabilityView> availabilities;
    private Double distanceKm;
    private Integer fitScore;
    private boolean withinPlayerRadius;
    private boolean shortlisted;
}
