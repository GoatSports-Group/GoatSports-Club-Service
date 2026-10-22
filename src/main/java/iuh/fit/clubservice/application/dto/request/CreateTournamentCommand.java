package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.EligibilityRuleOperator;
import iuh.fit.clubservice.domain.enums.EligibilityRuleType;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.service.SportParticipationPolicy;
import lombok.*;
import java.time.Duration;
import java.time.LocalDate;
import iuh.fit.clubservice.domain.service.EligibilityPolicy;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
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
    private Boolean publish;
    private UUID venueId;
    private List<UUID> courtIds;
    private LocalTime dailyStartTime;
    private LocalTime dailyEndTime;
    private Integer matchDurationMinutes;
    private String playFormat;

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
        if (venueId == null || courtIds == null || courtIds.isEmpty()) {
            throw new ServiceException("Chọn cơ sở và ít nhất một sân cho giải.");
        }
        if (courtIds.stream().distinct().count() != courtIds.size()) {
            throw new ServiceException("Một sân bị chọn hai lần.");
        }
        if (dailyStartTime == null || dailyEndTime == null || !dailyEndTime.isAfter(dailyStartTime)) {
            throw new ServiceException("Giờ kết thúc thi đấu mỗi ngày phải sau giờ bắt đầu.");
        }
        if (matchDurationMinutes == null || matchDurationMinutes < 15 || matchDurationMinutes > 240) {
            throw new ServiceException("Mỗi trận kéo dài từ 15 đến 240 phút.");
        }
        if (Duration.between(dailyStartTime, dailyEndTime).toMinutes() < matchDurationMinutes) {
            throw new ServiceException("Khung giờ mỗi ngày phải đủ cho ít nhất một trận.");
        }
        SportParticipationPolicy.PlayFormat format = SportParticipationPolicy.find(playFormat)
                .orElseThrow(() -> new ServiceException("Hình thức thi đấu không hợp lệ."));
        if (format.sport() != sportType) {
            throw new ServiceException("Hình thức thi đấu không thuộc môn đã chọn.");
        }
        if (eligibilityRules != null) {
            for (EligibilityRuleItem item : eligibilityRules) {
                Optional<String> problem = EligibilityPolicy.invalidRule(
                        item.getRuleType(), item.getOperator(), item.getExpectedValue());
                if (problem.isPresent()) throw new ServiceException(problem.get());
            }
        }
    }
}
