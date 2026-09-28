package iuh.fit.clubservice.application.dto.response;

import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/** Một giải câu lạc bộ đã tham gia (đội đại diện CLB đã được xác nhận) kèm thành tích của đội CLB ở giải đó. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubTournamentResponseCommand {
    private UUID tournamentId;
    private String name;
    private SportType sportType;
    private TournamentFormat format;
    private TournamentStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID registrationId;
    private String teamName;
    private int teamCount;
    private int played;
    private int won;
    private int drawn;
    private int lost;
    /** Hạng của đội CLB (null khi giải chưa có trận nào có kết quả). */
    private Integer rank;
    private boolean champion;
}
