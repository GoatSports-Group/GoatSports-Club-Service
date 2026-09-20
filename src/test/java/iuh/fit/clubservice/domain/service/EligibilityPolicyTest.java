package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.domain.enums.EligibilityRuleOperator;
import iuh.fit.clubservice.domain.enums.EligibilityRuleType;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityPolicyTest {

    private static TournamentEligibilityRule rule(EligibilityRuleType type,
                                                  EligibilityRuleOperator operator,
                                                  String expected) {
        return TournamentEligibilityRule.builder()
                .ruleId(UUID.randomUUID())
                .tournamentId(UUID.randomUUID())
                .ruleType(type)
                .operator(operator)
                .expectedValue(expected)
                .build();
    }

    private static EligibilityPolicy.Candidate candidate(Integer age, String gender, SkillLevel skill,
                                                         Integer elo, UUID clubId, int teamSize) {
        return new EligibilityPolicy.Candidate(age, gender, skill, elo, clubId, teamSize);
    }

    @Test
    @DisplayName("không có điều kiện thì ai cũng đăng ký được")
    void noRulesAllowsEveryone() {
        assertThat(EligibilityPolicy.firstViolation(List.of(), candidate(null, null, null, null, null, 0)))
                .isEmpty();
    }

    @Test
    @DisplayName("tuổi dưới ngưỡng bị từ chối, đủ tuổi thì qua")
    void ageBoundaryIsEnforced() {
        List<TournamentEligibilityRule> rules = List.of(
                rule(EligibilityRuleType.AGE, EligibilityRuleOperator.GREATER_THAN_OR_EQUAL, "18"));

        assertThat(EligibilityPolicy.firstViolation(rules, candidate(17, null, null, null, null, 0)))
                .isPresent();
        assertThat(EligibilityPolicy.firstViolation(rules, candidate(18, null, null, null, null, 0)))
                .isEmpty();
    }

    @Test
    @DisplayName("thiếu dữ liệu để xét thì từ chối, không âm thầm bỏ qua điều kiện")
    void missingDataFailsClosed() {
        List<TournamentEligibilityRule> rules = List.of(
                rule(EligibilityRuleType.AGE, EligibilityRuleOperator.GREATER_THAN_OR_EQUAL, "18"));

        Optional<String> violation =
                EligibilityPolicy.firstViolation(rules, candidate(null, null, null, null, null, 0));

        assertThat(violation).isPresent();
        assertThat(violation.get()).contains("chưa được xác định");
    }

    @Test
    @DisplayName("BETWEEN nhận cả hai biên")
    void betweenIsInclusive() {
        List<TournamentEligibilityRule> rules = List.of(
                rule(EligibilityRuleType.AGE, EligibilityRuleOperator.BETWEEN, "16,25"));

        assertThat(EligibilityPolicy.firstViolation(rules, candidate(16, null, null, null, null, 0))).isEmpty();
        assertThat(EligibilityPolicy.firstViolation(rules, candidate(25, null, null, null, null, 0))).isEmpty();
        assertThat(EligibilityPolicy.firstViolation(rules, candidate(15, null, null, null, null, 0))).isPresent();
        assertThat(EligibilityPolicy.firstViolation(rules, candidate(26, null, null, null, null, 0))).isPresent();
    }

    @Test
    @DisplayName("ELO trên ngưỡng bị loại khi giải giới hạn trần")
    void eloCeilingIsEnforced() {
        List<TournamentEligibilityRule> rules = List.of(
                rule(EligibilityRuleType.ELO_RATING, EligibilityRuleOperator.LESS_THAN_OR_EQUAL, "1500"));

        assertThat(EligibilityPolicy.firstViolation(rules, candidate(null, null, null, 1501, null, 0))).isPresent();
        assertThat(EligibilityPolicy.firstViolation(rules, candidate(null, null, null, 1500, null, 0))).isEmpty();
    }

    @Test
    @DisplayName("giới tính so khớp không phân biệt hoa thường và hỗ trợ IN")
    void genderMatching() {
        List<TournamentEligibilityRule> equalRule = List.of(
                rule(EligibilityRuleType.GENDER, EligibilityRuleOperator.EQUAL, "FEMALE"));
        assertThat(EligibilityPolicy.firstViolation(equalRule, candidate(null, "female", null, null, null, 0)))
                .isEmpty();
        assertThat(EligibilityPolicy.firstViolation(equalRule, candidate(null, "MALE", null, null, null, 0)))
                .isPresent();

        List<TournamentEligibilityRule> inRule = List.of(
                rule(EligibilityRuleType.GENDER, EligibilityRuleOperator.IN, "MALE, OTHER"));
        assertThat(EligibilityPolicy.firstViolation(inRule, candidate(null, "OTHER", null, null, null, 0)))
                .isEmpty();
    }

    @Test
    @DisplayName("trình độ so sánh theo thứ bậc chứ không so chuỗi")
    void skillLevelComparesByRank() {
        List<TournamentEligibilityRule> rules = List.of(
                rule(EligibilityRuleType.SKILL_LEVEL, EligibilityRuleOperator.GREATER_THAN_OR_EQUAL,
                        SkillLevel.INTERMEDIATE.name()));

        assertThat(EligibilityPolicy.firstViolation(
                rules, candidate(null, null, SkillLevel.BEGINNER, null, null, 0))).isPresent();
        assertThat(EligibilityPolicy.firstViolation(
                rules, candidate(null, null, SkillLevel.INTERMEDIATE, null, null, 0))).isEmpty();
    }

    @Test
    @DisplayName("đội hình thiếu người bị chặn")
    void teamSizeIsEnforced() {
        List<TournamentEligibilityRule> rules = List.of(
                rule(EligibilityRuleType.TEAM_SIZE, EligibilityRuleOperator.GREATER_THAN_OR_EQUAL, "5"));

        assertThat(EligibilityPolicy.firstViolation(rules, candidate(null, null, null, null, null, 4)))
                .isPresent();
        assertThat(EligibilityPolicy.firstViolation(rules, candidate(null, null, null, null, null, 5)))
                .isEmpty();
    }

    @Test
    @DisplayName("giải riêng cho một CLB chặn người ngoài")
    void clubMembershipIsEnforced() {
        UUID club = UUID.randomUUID();
        List<TournamentEligibilityRule> rules = List.of(
                rule(EligibilityRuleType.CLUB_MEMBERSHIP, EligibilityRuleOperator.EQUAL, club.toString()));

        assertThat(EligibilityPolicy.firstViolation(rules, candidate(null, null, null, null, club, 0)))
                .isEmpty();
        assertThat(EligibilityPolicy.firstViolation(
                rules, candidate(null, null, null, null, UUID.randomUUID(), 0))).isPresent();
        assertThat(EligibilityPolicy.firstViolation(rules, candidate(null, null, null, null, null, 0)))
                .isPresent();
    }

    @Test
    @DisplayName("trả về vi phạm đầu tiên khi có nhiều điều kiện")
    void reportsFirstViolationOnly() {
        List<TournamentEligibilityRule> rules = List.of(
                rule(EligibilityRuleType.AGE, EligibilityRuleOperator.GREATER_THAN_OR_EQUAL, "18"),
                rule(EligibilityRuleType.TEAM_SIZE, EligibilityRuleOperator.GREATER_THAN_OR_EQUAL, "5"));

        Optional<String> violation =
                EligibilityPolicy.firstViolation(rules, candidate(16, null, null, null, null, 2));

        assertThat(violation).isPresent();
        assertThat(violation.get()).contains("Tuổi");
    }
}
