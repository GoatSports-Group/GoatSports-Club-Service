package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.EligibilityRuleOperator;
import iuh.fit.clubservice.domain.enums.EligibilityRuleType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "tournament_eligibility_rules")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentEligibilityRuleEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", insertable = false, updatable = false)
    private TournamentEntity tournament;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private EligibilityRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false)
    private EligibilityRuleOperator operator;

    @Column(name = "expected_value", nullable = false, length = 1000)
    private String expectedValue;
}
