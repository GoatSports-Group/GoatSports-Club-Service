package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.domain.enums.SkillLevel;

import java.time.DayOfWeek;
import java.util.Set;

public record ScoutingQueryCommand(
        Double radiusKm,
        Integer limit,
        Set<SkillLevel> skillLevels,
        String position,
        DayOfWeek availableOn
) {
    public static ScoutingQueryCommand defaults() {
        return new ScoutingQueryCommand(null, null, null, null, null);
    }
}
