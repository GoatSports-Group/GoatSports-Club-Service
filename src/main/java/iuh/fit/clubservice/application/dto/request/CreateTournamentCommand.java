package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.EligibilityRuleOperator;
import iuh.fit.clubservice.domain.enums.EligibilityRuleType;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import lombok.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTournamentCommand {
    private UUID organizerId;
    private String name;
    private String description;
    private SportType sportType;
    private TournamentFormat format;
    private Integer maxParticipants;
    private Double entryFee;
    private Double prizePool;
    private LocalDate registrationOpenDate;
    private LocalDate registrationCloseDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> rules;
    private List<EligibilityRuleItem> eligibilityRules;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EligibilityRuleItem {
        private EligibilityRuleType ruleType;
        private EligibilityRuleOperator operator;
        private String expectedValue;
    }

    public void validate() throws ServiceException {
        if (registrationOpenDate == null || registrationCloseDate == null
                || startDate == null || endDate == null
                || registrationCloseDate.isBefore(registrationOpenDate)
                || startDate.isBefore(registrationCloseDate)
                || endDate.isBefore(startDate)) {
            throw new ServiceException("Mốc thời gian giải đấu không hợp lệ.");
        }
        if (maxParticipants != null && maxParticipants < 2) {
            throw new ServiceException("Giải đấu cần ít nhất 2 người hoặc đội.");
        }
    }
}
