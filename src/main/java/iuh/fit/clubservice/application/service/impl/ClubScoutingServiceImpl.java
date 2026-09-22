package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.response.ScoutedPlayerResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.PlayerProfileProvider;
import iuh.fit.clubservice.application.port.persistence.ClubInvitationRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.service.ClubScoutingService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubInvitation;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubInvitationStatus;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.service.ClubAccessPolicy;
import iuh.fit.clubservice.domain.service.ProvinceAnchor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClubScoutingServiceImpl implements ClubScoutingService {
    private static final double DEFAULT_RADIUS_KM = 25;
    private static final double MAX_RADIUS_KM = 200;
    private static final int DEFAULT_LIMIT = 24;
    private static final int MAX_LIMIT = 50;
    private static final double ACTIVE_MATCH_TARGET = 20;

    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubInvitationRepository invitationRepository;
    private final PlayerProfileProvider playerProfileProvider;

    @Override
    @Transactional(readOnly = true)
    public List<ScoutedPlayerResponseCommand> findCandidates(
            UUID clubId, UUID actorUserId, Double radiusKm, Integer limit) throws ServiceException {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ServiceException("Không tìm thấy câu lạc bộ."));
        if (club.getDisbandedAt() != null || Boolean.FALSE.equals(club.getActive())) {
            throw new ServiceException("Câu lạc bộ đã giải tán.");
        }
        ClubMember membership = memberRepository.findByClubIdAndUserId(clubId, actorUserId).orElse(null);
        if (!ClubAccessPolicy.isManager(club, membership, actorUserId)) {
            throw new ServiceException("Chỉ chủ hoặc quản lý câu lạc bộ mới có thể tìm người chơi.");
        }

        ProvinceAnchor.Coordinates anchor = anchorOf(club);
        if (anchor == null) {
            throw new ServiceException("Câu lạc bộ chưa có vị trí nên chưa tìm được người chơi quanh khu vực.");
        }

        double radius = clamp(radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm, 1, MAX_RADIUS_KM);
        int size = (int) clamp(limit == null ? DEFAULT_LIMIT : limit, 1, MAX_LIMIT);
        Set<UUID> excluded = excludedUserIds(clubId, club.getOwnerId());

        // Lay du ra roi moi loai, vi loai bo dien ra sau khi auth-service da cat theo limit.
        return playerProfileProvider.findScoutingCandidates(
                        club.getSportType(), anchor.latitude(), anchor.longitude(), radius, size + excluded.size())
                .stream()
                .filter(candidate -> !excluded.contains(candidate.userId()))
                .map(candidate -> toResponse(candidate, radius))
                .sorted(Comparator.comparingInt(ScoutedPlayerResponseCommand::getFitScore).reversed()
                        .thenComparing(ScoutedPlayerResponseCommand::getDistanceKm))
                .limit(size)
                .toList();
    }

    /** CLB tu dat toa do thi uu tien; chua co thi lay tam tinh/thanh de CLB cu van dung duoc ngay. */
    private ProvinceAnchor.Coordinates anchorOf(Club club) {
        if (club.getLatitude() != null && club.getLongitude() != null) {
            return new ProvinceAnchor.Coordinates(club.getLatitude(), club.getLongitude());
        }
        return ProvinceAnchor.of(club.getCity()).orElse(null);
    }

    /**
     * Khong moi lai nguoi da o trong CLB, dang cho duyet, dang bi cam, hoac da co loi moi treo.
     * Nguoi da roi hoac bi go van moi lai duoc.
     */
    private Set<UUID> excludedUserIds(UUID clubId, UUID ownerId) {
        Set<UUID> excluded = new HashSet<>();
        excluded.add(ownerId);
        for (ClubMember member : memberRepository.findByClubId(clubId)) {
            if (member.getStatus() == ClubMemberStatus.ACTIVE
                    || member.getStatus() == ClubMemberStatus.PENDING
                    || member.getStatus() == ClubMemberStatus.BANNED) {
                excluded.add(member.getUserId());
            }
        }
        for (ClubInvitation invitation
                : invitationRepository.findByClubIdAndStatus(clubId, ClubInvitationStatus.PENDING)) {
            excluded.add(invitation.getInviteeId());
        }
        return excluded;
    }

    private ScoutedPlayerResponseCommand toResponse(
            PlayerProfileProvider.ScoutingCandidateView candidate, double radius) {
        double distance = candidate.distanceKm() == null ? radius : candidate.distanceKm();
        boolean withinPlayerRadius = candidate.playRadiusKm() != null && candidate.playRadiusKm() >= distance;

        double proximity = 1 - Math.min(distance / radius, 1);
        double activity = Math.min(orZero(candidate.matchCount()) / ACTIVE_MATCH_TARGET, 1);
        double reach = withinPlayerRadius ? 1 : 0;
        int fitScore = (int) Math.round(100 * (0.55 * proximity + 0.25 * activity + 0.20 * reach));

        return ScoutedPlayerResponseCommand.builder()
                .userId(candidate.userId())
                .username(candidate.username())
                .fullName(candidate.fullName())
                .avatarUrl(candidate.avatarUrl())
                .skillLevel(candidate.skillLevel())
                .eloRating(candidate.eloRating())
                .matchCount(candidate.matchCount())
                .winRate(candidate.winRate())
                .distanceKm(distance)
                .fitScore(fitScore)
                .withinPlayerRadius(withinPlayerRadius)
                .build();
    }

    private double orZero(Integer value) { return value == null ? 0 : value; }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
