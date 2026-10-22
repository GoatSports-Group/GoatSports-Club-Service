package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.domain.enums.EligibilityRuleOperator;
import iuh.fit.clubservice.domain.enums.EligibilityRuleType;
import iuh.fit.clubservice.domain.enums.SkillLevel;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntPredicate;

public final class EligibilityPolicy {

    private EligibilityPolicy() { }

    public record Candidate(
            Integer age,
            String gender,
            SkillLevel skillLevel,
            Integer eloRating,
            UUID clubId,
            int teamSize
    ) { }

    /** Tra ve mo ta vi pham dau tien, rong neu du dieu kien. */
    public static Optional<String> firstViolation(List<TournamentEligibilityRule> rules, Candidate candidate) {
        for (TournamentEligibilityRule rule : rules) {
            Optional<String> violation = evaluate(rule, candidate);
            if (violation.isPresent()) return violation;
        }
        return Optional.empty();
    }

    private static Optional<String> evaluate(TournamentEligibilityRule rule, Candidate candidate) {
        return switch (rule.getRuleType()) {
            case AGE -> numeric(rule, candidate.age(), "Tuổi");
            case ELO_RATING -> numeric(rule, candidate.eloRating(), "Điểm ELO");
            case TEAM_SIZE -> numeric(rule, candidate.teamSize(), "Số thành viên đội hình");
            case SKILL_LEVEL -> skill(rule, candidate.skillLevel());
            case GENDER -> text(rule, candidate.gender(), "Giới tính");
            case CLUB_MEMBERSHIP -> text(rule,
                    candidate.clubId() == null ? null : candidate.clubId().toString(), "Câu lạc bộ");
        };
    }

    private static Optional<String> skill(TournamentEligibilityRule rule, SkillLevel level) {
        if (level == null) return Optional.of("Không xác định được trình độ của người đăng ký.");
        SkillLevel expected = parseSkill(rule.getExpectedValue());
        if (expected != null && isOrdinalComparison(rule.getOperator())) {
            return numeric(rule, level.ordinal(), "Trình độ", String.valueOf(expected.ordinal()));
        }
        return text(rule, level.name(), "Trình độ");
    }

    private static boolean isOrdinalComparison(EligibilityRuleOperator operator) {
        return operator == EligibilityRuleOperator.GREATER_THAN
                || operator == EligibilityRuleOperator.GREATER_THAN_OR_EQUAL
                || operator == EligibilityRuleOperator.LESS_THAN
                || operator == EligibilityRuleOperator.LESS_THAN_OR_EQUAL;
    }

    private static SkillLevel parseSkill(String value) {
        if (value == null) return null;
        try {
            return SkillLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Optional<String> numeric(TournamentEligibilityRule rule, Integer actual, String label) {
        return numeric(rule, actual, label, rule.getExpectedValue());
    }

    private static Optional<String> numeric(TournamentEligibilityRule rule, Integer actual,
                                            String label, String expectedRaw) {
        if (actual == null) {
            return Optional.of(label + " chưa được xác định nên không đủ căn cứ xét điều kiện tham dự.");
        }
        String expected = expectedRaw == null ? "" : expectedRaw.trim();
        boolean ok = switch (rule.getOperator()) {
            case EQUAL -> matchesNumber(expected, actual, true);
            case NOT_EQUAL -> matchesNumber(expected, actual, false);
            case GREATER_THAN -> compare(actual, expected, value -> actual > value);
            case GREATER_THAN_OR_EQUAL -> compare(actual, expected, value -> actual >= value);
            case LESS_THAN -> compare(actual, expected, value -> actual < value);
            case LESS_THAN_OR_EQUAL -> compare(actual, expected, value -> actual <= value);
            case IN -> Arrays.stream(expected.split(",")).map(String::trim)
                    .anyMatch(item -> matchesNumber(item, actual, true));
            case BETWEEN -> between(expected, actual);
        };
        return ok ? Optional.empty()
                : Optional.of(label + " không thỏa điều kiện tham dự (" + describe(rule) + ").");
    }

    private static boolean between(String expected, int actual) {
        String[] bounds = expected.split("[,-]");
        if (bounds.length != 2) return false;
        Integer low = toInt(bounds[0]);
        Integer high = toInt(bounds[1]);
        return low != null && high != null && actual >= low && actual <= high;
    }

    private static boolean matchesNumber(String expected, int actual, boolean shouldEqual) {
        Integer value = toInt(expected);
        if (value == null) return false;
        return shouldEqual == (actual == value);
    }

    private static boolean compare(int actual, String expected, IntPredicate predicate) {
        Integer value = toInt(expected);
        return value != null && predicate.test(value);
    }

    private static Integer toInt(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Optional<String> text(TournamentEligibilityRule rule, String actual, String label) {
        if (actual == null || actual.isBlank()) {
            return Optional.of(label + " chưa được xác định nên không đủ căn cứ xét điều kiện tham dự.");
        }
        String expected = rule.getExpectedValue() == null ? "" : rule.getExpectedValue().trim();
        boolean ok = switch (rule.getOperator()) {
            case EQUAL -> actual.equalsIgnoreCase(expected);
            case NOT_EQUAL -> !actual.equalsIgnoreCase(expected);
            case IN -> Arrays.stream(expected.split(",")).map(String::trim)
                    .anyMatch(item -> item.equalsIgnoreCase(actual));
            default -> false;
        };
        return ok ? Optional.empty()
                : Optional.of(label + " không thỏa điều kiện tham dự (" + describe(rule) + ").");
    }

    /**
     * Kiem tra luat luc ban to chuc luu giai. Luat khong doc duoc se bi danh gia la "khong dat"
     * (fail-closed) nen neu de lot, khong ai dang ky duoc giai.
     */
    public static Optional<String> invalidRule(EligibilityRuleType type, EligibilityRuleOperator operator, String raw) {
        if (type == null || operator == null || raw == null || raw.isBlank()) {
            return Optional.of("Điều kiện tham dự thiếu loại, toán tử hoặc giá trị.");
        }
        String value = raw.trim();
        return switch (type) {
            case AGE, ELO_RATING, TEAM_SIZE -> validNumber(operator, value) ? Optional.empty()
                    : Optional.of("Giá trị điều kiện số không hợp lệ: " + value + ".");
            case SKILL_LEVEL -> operator != EligibilityRuleOperator.BETWEEN
                    && Arrays.stream(value.split(",")).allMatch(item -> parseSkill(item) != null)
                    && (operator == EligibilityRuleOperator.IN || !value.contains(","))
                    ? Optional.empty() : Optional.of("Trình độ trong điều kiện tham dự không hợp lệ.");
            case GENDER -> isTextOperator(operator) && Arrays.stream(value.split(","))
                    .map(item -> item.trim().toUpperCase()).allMatch(item -> item.equals("MALE")
                            || item.equals("FEMALE") || item.equals("OTHER"))
                    ? Optional.empty() : Optional.of("Giới tính trong điều kiện tham dự không hợp lệ.");
            case CLUB_MEMBERSHIP -> isTextOperator(operator) && Arrays.stream(value.split(","))
                    .allMatch(EligibilityPolicy::isUuid)
                    ? Optional.empty() : Optional.of("Câu lạc bộ trong điều kiện tham dự không hợp lệ.");
        };
    }

    private static boolean validNumber(EligibilityRuleOperator operator, String value) {
        if (operator == EligibilityRuleOperator.IN) {
            return Arrays.stream(value.split(",")).allMatch(item -> toInt(item) != null);
        }
        if (operator == EligibilityRuleOperator.BETWEEN) {
            String[] bounds = value.split("[,-]");
            return bounds.length == 2 && toInt(bounds[0]) != null && toInt(bounds[1]) != null
                    && toInt(bounds[0]) <= toInt(bounds[1]);
        }
        return toInt(value) != null;
    }

    private static boolean isTextOperator(EligibilityRuleOperator operator) {
        return operator == EligibilityRuleOperator.EQUAL || operator == EligibilityRuleOperator.NOT_EQUAL
                || operator == EligibilityRuleOperator.IN;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String describe(TournamentEligibilityRule rule) {
        return rule.getRuleType() + " " + rule.getOperator() + " " + rule.getExpectedValue();
    }
}
