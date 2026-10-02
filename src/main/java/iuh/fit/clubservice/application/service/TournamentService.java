package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.CreateTournamentCommand;
import iuh.fit.clubservice.application.dto.request.ReserveTournamentVenueCommand;
import iuh.fit.clubservice.application.dto.request.UpdateFixtureResultCommand;
import iuh.fit.clubservice.application.dto.response.TournamentFixtureResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentStandingResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentVenueReservationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.entity.TournamentEligibilityRule;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.enums.SportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Giải đấu của chủ sân: tạo (kèm kiểm tra đủ sân và giữ sân bên venue-service), sửa bản nháp, công bố, hủy, kết thúc.
 * Lịch/kết quả ở {@link TournamentFixtureService}, xếp sân ở {@link TournamentScheduleService},
 * suất đăng ký ở {@link TournamentRegistrationService}.
 */
public interface TournamentService {
    Page<TournamentResponseCommand> searchTournaments(SportType sportType, TournamentStatus status, String keyword, Pageable pageable);

    /** Giai nguoi choi tham gia (ke ca dang ky bi tu choi / da huy), loc nhu trang kham pha. */
    Page<TournamentResponseCommand> getParticipatingTournaments(UUID userId, TournamentStatus status, SportType sportType,
                                                                String keyword, Pageable pageable);

    /** Danh sách giải của chủ sân có bộ lọc (trang quản lý giải). */
    Page<TournamentResponseCommand> getOrganizedTournaments(UUID userId, TournamentStatus status, SportType sportType,
                                                            UUID venueId, String keyword, Pageable pageable);

    /** Số giải theo từng trạng thái hiệu lực + tổng suất đang giữ, trên toàn bộ giải của chủ sân. */
    OrganizerSummary getOrganizerSummary(UUID userId);

    record OrganizerSummary(java.util.Map<TournamentStatus, Long> byStatus, long total, long seatsHeld) { }

    TournamentResponseCommand getTournamentDetails(UUID tournamentId, UUID viewerId) throws ServiceException;

    TournamentResponseCommand createTournament(CreateTournamentCommand command) throws ServiceException;

    TournamentResponseCommand updateTournament(UUID tournamentId, CreateTournamentCommand command) throws ServiceException;





    List<TournamentEligibilityRule> getEligibilityRules(UUID tournamentId);

    TournamentResponseCommand changeStatus(UUID tournamentId, TournamentStatus target, UUID actorUserId)
            throws ServiceException;



}
