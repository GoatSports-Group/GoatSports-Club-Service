package iuh.fit.clubservice.domain.entity;

import iuh.fit.clubservice.domain.enums.EligibilityRuleOperator;
import iuh.fit.clubservice.domain.enums.EligibilityRuleType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentEligibilityRule extends BaseEntity {
    private UUID ruleId;
    private UUID tournamentId;
    private EligibilityRuleType ruleType;
    private EligibilityRuleOperator operator;
    private String expectedValue;
}
