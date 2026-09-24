package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand;
import iuh.fit.clubservice.application.dto.response.TournamentRegistrationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.entity.Tournament;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Vòng đời một suất tham dự: đăng ký cá nhân hoặc tạo đội, đóng lệ phí trong 24 giờ,
 * rút / bị từ chối kèm hoàn phí, và hết hạn tự động. Việc mời và xác nhận thành viên nằm ở
 * {@link TournamentTeamService}.
 */
public interface TournamentRegistrationService {

    TournamentRegistrationResponseCommand register(RegisterTournamentTeamCommand command) throws ServiceException;

    FeeCheckout checkout(UUID tournamentId, UUID registrationId, UUID userId) throws ServiceException;

    /** Người đăng ký tự rút (CANCELLED) hoặc ban tổ chức từ chối kèm lý do (REJECTED). Đã đóng phí thì hoàn. */
    void cancel(UUID tournamentId, UUID registrationId, UUID actorId, String reason) throws ServiceException;

    List<TournamentRegistrationResponseCommand> getTeams(UUID tournamentId);

    /** Chat nhóm của giải = ban tổ chức + mọi người đã xác nhận trong các đăng ký CONFIRMED. */
    void syncConversation(Tournament tournament);

    void closeAllForCancelledTournament(Tournament tournament, UUID actorId);

    /** Trước khi xếp lịch: đóng đăng ký chưa đủ người hoặc chưa đóng phí, vì bracket chốt từ danh sách CONFIRMED. */
    void closeUnfinishedBeforeFixtures(Tournament tournament, UUID actorId);

    /** Đăng ký quá hạn đóng phí, hoặc đội chưa đủ người khi đã hết hạn đăng ký. */
    List<UUID> findOverdueRegistrationIds(LocalDateTime now);

    void expire(UUID registrationId, LocalDateTime now);

    record FeeCheckout(UUID paymentId, String checkoutUrl, String qrCodeContent, Double amount,
                       LocalDateTime expiresAt) { }
}
