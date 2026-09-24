package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand;
import iuh.fit.clubservice.application.dto.response.TournamentRegistrationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.LineupRole;
import iuh.fit.clubservice.domain.enums.SportType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Nguoi choi tham du giai: dang ky ca nhan (mon danh don) hoac lap doi (mon dong doi / danh doi)
 * bang loi moi phai xac nhan, dong le phi trong 24 gio, rut hoac bi tu choi kem hoan phi.
 */
public interface TournamentRegistrationService {

    TournamentRegistrationResponseCommand register(RegisterTournamentTeamCommand command) throws ServiceException;

    List<TeamInvitation> getMyInvitations(UUID userId);

    TournamentRegistrationResponseCommand respondInvitation(UUID tournamentId, UUID registrationId, UUID userId,
                                                            boolean accept) throws ServiceException;

    TournamentRegistrationResponseCommand inviteMembers(UUID tournamentId, UUID registrationId, UUID captainId,
                                                        List<RegisterTournamentTeamCommand.LineupItem> invitees)
            throws ServiceException;

    /** Doi truong bo mot nguoi khoi doi, hoac chinh thanh vien tu roi doi (playerId == actorId). */
    TournamentRegistrationResponseCommand removeMember(UUID tournamentId, UUID registrationId, UUID actorId,
                                                       UUID playerId) throws ServiceException;

    FeeCheckout checkout(UUID tournamentId, UUID registrationId, UUID userId) throws ServiceException;

    /** Nguoi dang ky tu rut (CANCELLED) hoac ban to chuc tu choi kem ly do (REJECTED). Da dong phi thi hoan. */
    void cancel(UUID tournamentId, UUID registrationId, UUID actorId, String reason) throws ServiceException;

    List<TournamentRegistrationResponseCommand> getTeams(UUID tournamentId);

    /** Chat nhom giai = ban to chuc + moi nguoi da xac nhan trong cac dang ky CONFIRMED. */
    void syncConversation(Tournament tournament);

    void closeAllForCancelledTournament(Tournament tournament, UUID actorId);

    /** Truoc khi xep lich: dong dang ky chua du nguoi hoac chua dong phi, vi bracket chot tu danh sach CONFIRMED. */
    void closeUnfinishedBeforeFixtures(Tournament tournament, UUID actorId);

    /** Dang ky qua han dong phi, hoac doi chua du nguoi khi da het han dang ky. */
    List<UUID> findOverdueRegistrationIds(LocalDateTime now);

    void expire(UUID registrationId, LocalDateTime now);

    record TeamInvitation(
            UUID tournamentId,
            String tournamentName,
            SportType sportType,
            LocalDate startDate,
            LocalDate registrationCloseDate,
            UUID registrationId,
            String teamName,
            UUID captainId,
            UUID clubId,
            LineupRole lineupRole,
            Integer shirtNumber,
            int acceptedCount,
            int rosterMin,
            int rosterMax
    ) { }

    record FeeCheckout(UUID paymentId, String checkoutUrl, String qrCodeContent, Double amount,
                       LocalDateTime expiresAt) { }
}
