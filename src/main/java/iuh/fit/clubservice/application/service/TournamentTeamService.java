package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.RegisterTournamentTeamCommand;
import iuh.fit.clubservice.application.dto.response.TournamentRegistrationResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.domain.enums.LineupRole;
import iuh.fit.clubservice.domain.enums.SportType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lập đội theo cơ chế mời + xác nhận: đội trưởng mời, từng người tự nhận lời hoặc từ chối.
 * Đội chỉ chuyển sang đóng lệ phí khi đủ số người tối thiểu của hình thức thi đấu.
 */
public interface TournamentTeamService {

    List<TeamInvitation> getMyInvitations(UUID userId);

    TournamentRegistrationResponseCommand respondInvitation(UUID tournamentId, UUID registrationId, UUID userId,
                                                            boolean accept) throws ServiceException;

    TournamentRegistrationResponseCommand inviteMembers(UUID tournamentId, UUID registrationId, UUID captainId,
                                                        List<RegisterTournamentTeamCommand.LineupItem> invitees)
            throws ServiceException;

    /** Đội trưởng bỏ một người khỏi đội, hoặc chính thành viên tự rời đội (playerId == actorId). */
    TournamentRegistrationResponseCommand removeMember(UUID tournamentId, UUID registrationId, UUID actorId,
                                                       UUID playerId) throws ServiceException;

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
}
