package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.UpdateFixtureResultCommand;
import iuh.fit.clubservice.application.dto.response.TournamentFixtureResponseCommand;
import iuh.fit.clubservice.application.dto.response.TournamentStandingResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;

import java.util.List;
import java.util.UUID;

/** Lịch thi đấu, kết quả và bảng xếp hạng của giải. Chỉ ban tổ chức được xếp lịch và nhập tỷ số. */
public interface TournamentFixtureService {

    /** Chốt danh sách CONFIRMED, sinh bracket (loại trực tiếp) hoặc lịch vòng tròn và chuyển giải sang đang diễn ra. */
    List<TournamentFixtureResponseCommand> generateFixtures(UUID tournamentId, UUID actorUserId) throws ServiceException;

    List<TournamentFixtureResponseCommand> getFixtures(UUID tournamentId);

    TournamentFixtureResponseCommand updateFixtureResult(UpdateFixtureResultCommand command) throws ServiceException;

    List<TournamentStandingResponseCommand> getStandings(UUID tournamentId);
}
