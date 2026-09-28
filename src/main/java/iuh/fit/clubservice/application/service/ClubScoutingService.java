package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.ScoutingQueryCommand;
import iuh.fit.clubservice.application.dto.response.ScoutedPlayerResponseCommand;
import iuh.fit.clubservice.application.dto.response.SentInvitationResponseCommand;
import iuh.fit.clubservice.application.dto.response.ShortlistEntryResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;

import java.util.List;
import java.util.UUID;

public interface ClubScoutingService {
    List<ScoutedPlayerResponseCommand> findCandidates(UUID clubId, UUID actorUserId, ScoutingQueryCommand query)
            throws ServiceException;

    List<ShortlistEntryResponseCommand> getShortlist(UUID clubId, UUID actorUserId) throws ServiceException;

    ShortlistEntryResponseCommand saveShortlistEntry(UUID clubId, UUID actorUserId, UUID playerId, String note)
            throws ServiceException;

    void removeShortlistEntry(UUID clubId, UUID actorUserId, UUID playerId) throws ServiceException;

    List<SentInvitationResponseCommand> getSentInvitations(UUID clubId, UUID actorUserId) throws ServiceException;

    /**
     * Quan hệ của từng người với CLB (chưa liên quan, đã mời, đang xin vào, thành viên, bị cấm) — cho tab "Mời bạn bè":
     * người quản lý mời thẳng bạn bè của mình, không qua lọc khoảng cách / trình độ như danh sách Khám phá.
     */
    java.util.Map<UUID, iuh.fit.clubservice.application.dto.response.PlayerClubRelation> getRelations(
            UUID clubId, UUID actorUserId, java.util.Collection<UUID> userIds) throws ServiceException;
}
