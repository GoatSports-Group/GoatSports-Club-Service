package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.request.ScoutingQueryCommand;
import iuh.fit.clubservice.application.dto.response.PlayerClubRelation;
import iuh.fit.clubservice.application.dto.response.ScoutedPlayerResponseCommand;
import iuh.fit.clubservice.application.dto.response.SentInvitationResponseCommand;
import iuh.fit.clubservice.application.dto.response.ShortlistEntryResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider.ScoutingCandidateView;
import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.ClubShortlistRepository;
import iuh.fit.clubservice.application.service.ClubScoutingService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.entity.ClubShortlistEntry;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.domain.service.ClubAccessPolicy;
import iuh.fit.clubservice.domain.service.ProvinceAnchor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClubScoutingServiceImpl implements ClubScoutingService {
    private static final double DEFAULT_RADIUS_KM = 25;
    private static final double PROXIMITY_SCALE_KM = 25;
    private static final double MAX_RADIUS_KM = 200;
    private static final int DEFAULT_LIMIT = 24;
    private static final int MAX_LIMIT = 50;
    private static final double ACTIVE_MATCH_TARGET = 20;
    private static final int MAX_SHORTLIST = 100;
    private static final int MAX_NOTE_LENGTH = 280;
    private static final int SENT_INVITATION_HISTORY = 100;

    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubInvitationRepository invitationRepository;
    private final ClubShortlistRepository shortlistRepository;
    private final PlayerProfileProvider playerProfileProvider;

    @Override
    @Transactional(readOnly = true)
    public List<ScoutedPlayerResponseCommand> findCandidates(
            UUID clubId, UUID actorUserId, ScoutingQueryCommand query) throws ServiceException {
        Club club = requireManagedClub(clubId, actorUserId);
        ProvinceAnchor.Coordinates anchor = requireAnchor(club);
        ScoutingQueryCommand filters = query == null ? ScoutingQueryCommand.defaults() : query;

        double radius = clamp(filters.radiusKm() == null ? DEFAULT_RADIUS_KM : filters.radiusKm(), 1, MAX_RADIUS_KM);
        int size = (int) clamp(filters.limit() == null ? DEFAULT_LIMIT : filters.limit(), 1, MAX_LIMIT);
        Map<UUID, PlayerClubRelation> relations = relationsOf(club);
        Set<UUID> shortlisted = shortlistRepository.findByClubId(clubId).stream()
                .map(ClubShortlistEntry::getUserId).collect(Collectors.toSet());

        int poolSize = Math.min(size + relations.size(), MAX_LIMIT * 4);
        PlayerProfileProvider.ScoutingFilter filter = new PlayerProfileProvider.ScoutingFilter(
                club.getSportType(), anchor.latitude(), anchor.longitude(), radius, poolSize,
                filters.skillLevels(), filters.position(), filters.availableOn());
        return playerProfileProvider.findScoutingCandidates(filter).stream()
                .filter(candidate -> candidate.profileVisible() && !relations.containsKey(candidate.userId()))
                .map(candidate -> toResponse(candidate, shortlisted.contains(candidate.userId())))
                .sorted(Comparator.comparing(ScoutedPlayerResponseCommand::getFitScore).reversed()
                        .thenComparing(ScoutedPlayerResponseCommand::getDistanceKm,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(size)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShortlistEntryResponseCommand> getShortlist(UUID clubId, UUID actorUserId) throws ServiceException {
        Club club = requireManagedClub(clubId, actorUserId);
        List<ClubShortlistEntry> entries = shortlistRepository.findByClubId(clubId);
        if (entries.isEmpty()) return List.of();

        Map<UUID, ScoutedPlayerResponseCommand> players = lookup(club, entries.stream()
                .map(ClubShortlistEntry::getUserId).toList(), true);
        Map<UUID, PlayerClubRelation> relations = relationsOf(club);
        return entries.stream()
                .map(entry -> toResponse(entry, players.get(entry.getUserId()),
                        relations.getOrDefault(entry.getUserId(), PlayerClubRelation.NONE)))
                .toList();
    }

    @Override
    @Transactional
    public ShortlistEntryResponseCommand saveShortlistEntry(
            UUID clubId, UUID actorUserId, UUID playerId, String note) throws ServiceException {
        Club club = requireManagedClub(clubId, actorUserId);
        if (playerId == null) throw new ServiceException("Thiếu người chơi cần theo dõi.");
        String normalizedNote = note == null || note.isBlank() ? null : note.trim();
        if (normalizedNote != null && normalizedNote.length() > MAX_NOTE_LENGTH) {
            throw new ServiceException("Ghi chú tối đa " + MAX_NOTE_LENGTH + " ký tự.");
        }

        ClubShortlistEntry entry = shortlistRepository.findByClubIdAndUserId(clubId, playerId).orElse(null);
        ScoutedPlayerResponseCommand player = lookup(club, List.of(playerId), true).get(playerId);
        if (entry == null) {
            // A player who hid their profile can stay on an existing list, but can't be newly added.
            if (player == null) throw new ServiceException("Không tìm thấy người chơi.");
            if (!player.isProfileVisible()) {
                throw new ServiceException("Người chơi này không cho phép câu lạc bộ tìm thấy hồ sơ.");
            }
            if (shortlistRepository.countByClubId(clubId) >= MAX_SHORTLIST) {
                throw new ServiceException("Danh sách theo dõi đã đủ " + MAX_SHORTLIST + " người. Hãy bỏ bớt trước khi thêm.");
            }
            entry = ClubShortlistEntry.builder().clubId(clubId).userId(playerId).addedBy(actorUserId).build();
        }
        entry.setNote(normalizedNote);
        ClubShortlistEntry saved = shortlistRepository.save(entry);
        return toResponse(saved, player, relationsOf(club).getOrDefault(playerId, PlayerClubRelation.NONE));
    }

    @Override
    @Transactional
    public void removeShortlistEntry(UUID clubId, UUID actorUserId, UUID playerId) throws ServiceException {
        requireManagedClub(clubId, actorUserId);
        shortlistRepository.findByClubIdAndUserId(clubId, playerId).ifPresent(shortlistRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SentInvitationResponseCommand> getSentInvitations(UUID clubId, UUID actorUserId)
            throws ServiceException {
        Club club = requireManagedClub(clubId, actorUserId);
        List<ClubInvitation> invitations = invitationRepository.findRecentByClubId(clubId, SENT_INVITATION_HISTORY);
        if (invitations.isEmpty()) return List.of();

        Map<UUID, ScoutedPlayerResponseCommand> players = lookup(club, invitations.stream()
                .map(ClubInvitation::getInviteeId).distinct().toList(), false);
        Map<UUID, PlayerClubRelation> relations = relationsOf(club);
        return invitations.stream()
                .map(invitation -> SentInvitationResponseCommand.builder()
                        .invitationId(invitation.getInvitationId())
                        .status(invitation.getStatus())
                        .message(invitation.getMessage())
                        .invitedBy(invitation.getInvitedBy())
                        .createdAt(invitation.getCreatedAt())
                        .respondedAt(invitation.getRespondedAt())
                        .player(players.get(invitation.getInviteeId()))
                        .relation(relations.getOrDefault(invitation.getInviteeId(), PlayerClubRelation.NONE))
                        .build())
                .toList();
    }

    /** Tối đa số người hỏi một lần: đủ cho danh sách bạn bè, chặn request quá lớn. */
    static final int MAX_RELATION_LOOKUP = 500;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PlayerClubRelation> getRelations(UUID clubId, UUID actorUserId, Collection<UUID> userIds)
            throws ServiceException {
        Club club = requireManagedClub(clubId, actorUserId);
        if (userIds == null || userIds.isEmpty()) return Map.of();
        if (userIds.size() > MAX_RELATION_LOOKUP) {
            throw new ServiceException("Mỗi lần chỉ tra tối đa " + MAX_RELATION_LOOKUP + " người.");
        }
        Map<UUID, PlayerClubRelation> relations = relationsOf(club);
        Map<UUID, PlayerClubRelation> result = new LinkedHashMap<>();
        for (UUID userId : userIds) {
            if (userId != null) result.put(userId, relations.getOrDefault(userId, PlayerClubRelation.NONE));
        }
        return result;
    }

    private Club requireManagedClub(UUID clubId, UUID actorUserId) throws ServiceException {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy câu lạc bộ."));
        if (club.getDisbandedAt() != null || Boolean.FALSE.equals(club.getActive())) {
            throw new ServiceException("Câu lạc bộ đã giải tán.");
        }
        ClubMember membership = memberRepository.findByClubIdAndUserId(clubId, actorUserId).orElse(null);
        if (!ClubAccessPolicy.isManager(club, membership, actorUserId)) {
            throw new ServiceException("Chỉ chủ hoặc quản lý câu lạc bộ mới có thể tìm người chơi.");
        }
        return club;
    }

    private ProvinceAnchor.Coordinates requireAnchor(Club club) throws ServiceException {
        ProvinceAnchor.Coordinates anchor = anchorOf(club);
        if (anchor == null) {
            throw new ServiceException("Câu lạc bộ chưa có vị trí nên chưa tìm được người chơi quanh khu vực.");
        }
        return anchor;
    }

    /** CLB tu dat toa do thi uu tien; chua co thi lay tam tinh/thanh de CLB cu van dung duoc ngay. */
    private ProvinceAnchor.Coordinates anchorOf(Club club) {
        if (club.getLatitude() != null && club.getLongitude() != null) {
            return new ProvinceAnchor.Coordinates(club.getLatitude(), club.getLongitude());
        }
        return ProvinceAnchor.of(club.getCity()).orElse(null);
    }

    /**
     * Players the club can't invite right now: the owner, members, pending requests, bans and pending
     * invitations. Nguoi da roi hoac bi go van moi lai duoc, nen khong nam trong day.
     */
    private Map<UUID, PlayerClubRelation> relationsOf(Club club) {
        Map<UUID, PlayerClubRelation> relations = new HashMap<>();
        relations.put(club.getOwnerId(), PlayerClubRelation.MEMBER);
        for (ClubMember member : memberRepository.findByClubId(club.getClubId())) {
            PlayerClubRelation relation = switch (member.getStatus()) {
                case ACTIVE -> PlayerClubRelation.MEMBER;
                case PENDING -> PlayerClubRelation.REQUESTED;
                case BANNED -> PlayerClubRelation.BANNED;
                default -> null;
            };
            if (relation != null) relations.put(member.getUserId(), relation);
        }
        for (ClubInvitation invitation
                : invitationRepository.findByClubIdAndStatus(club.getClubId(), ClubInvitationStatus.PENDING)) {
            relations.putIfAbsent(invitation.getInviteeId(), PlayerClubRelation.INVITED);
        }
        return relations;
    }

    private Map<UUID, ScoutedPlayerResponseCommand> lookup(Club club, List<UUID> userIds, boolean markShortlisted)
            throws ServiceException {
        ProvinceAnchor.Coordinates anchor = anchorOf(club);
        double latitude = anchor == null ? 0 : anchor.latitude();
        double longitude = anchor == null ? 0 : anchor.longitude();
        return playerProfileProvider.lookupScoutingProfiles(club.getSportType(), latitude, longitude, userIds).stream()
                .map(view -> anchor == null || !view.profileVisible()
                        ? identityOf(view, markShortlisted)
                        : toResponse(view, markShortlisted))
                .collect(Collectors.toMap(ScoutedPlayerResponseCommand::getUserId, Function.identity(), (a, b) -> a));
    }

    private ScoutedPlayerResponseCommand identityOf(ScoutingCandidateView view, boolean shortlisted) {
        return ScoutedPlayerResponseCommand.builder()
                .userId(view.userId())
                .username(view.username())
                .fullName(view.fullName())
                .avatarUrl(view.avatarUrl())
                .profileVisible(view.profileVisible())
                .shortlisted(shortlisted)
                .build();
    }

    private ScoutedPlayerResponseCommand toResponse(ScoutingCandidateView candidate, boolean shortlisted) {
        Double distance = candidate.distanceKm();
        boolean withinPlayerRadius = distance != null
                && candidate.playRadiusKm() != null && candidate.playRadiusKm() >= distance;

        double proximity = distance == null ? 0 : 1 - Math.min(distance / PROXIMITY_SCALE_KM, 1);
        double activity = Math.min(orZero(candidate.matchCount()) / ACTIVE_MATCH_TARGET, 1);
        double reach = withinPlayerRadius ? 1 : 0;
        int fitScore = (int) Math.round(100 * (0.55 * proximity + 0.25 * activity + 0.20 * reach));

        return ScoutedPlayerResponseCommand.builder()
                .userId(candidate.userId())
                .username(candidate.username())
                .fullName(candidate.fullName())
                .avatarUrl(candidate.avatarUrl())
                .profileVisible(true)
                .skillLevel(candidate.skillLevel())
                .eloRating(candidate.eloRating())
                .preferredPositions(candidate.preferredPositions() == null ? Set.of() : candidate.preferredPositions())
                .playStyle(candidate.playStyle())
                .city(candidate.city())
                .matchCount(candidate.matchCount())
                .winCount(candidate.winCount())
                .lossCount(candidate.lossCount())
                .drawCount(candidate.drawCount())
                .winRate(candidate.winRate())
                .availabilities(candidate.availabilities() == null ? List.of() : candidate.availabilities())
                .distanceKm(distance)
                .fitScore(fitScore)
                .withinPlayerRadius(withinPlayerRadius)
                .shortlisted(shortlisted)
                .build();
    }

    private ShortlistEntryResponseCommand toResponse(
            ClubShortlistEntry entry, ScoutedPlayerResponseCommand player, PlayerClubRelation relation) {
        return ShortlistEntryResponseCommand.builder()
                .userId(entry.getUserId())
                .note(entry.getNote())
                .addedBy(entry.getAddedBy())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .relation(relation)
                .player(player)
                .build();
    }

    private double orZero(Integer value) { return value == null ? 0 : value; }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
