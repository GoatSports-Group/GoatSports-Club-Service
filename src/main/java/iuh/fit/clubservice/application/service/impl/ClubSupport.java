package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.response.ClubMemberResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.service.ClubAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Phần dùng chung của các use case câu lạc bộ: tra cứu, kiểm tra quyền quản lý, đồng bộ chat nhóm và dựng response.
 * Quy tắc "ai là người quản lý" chỉ nằm ở {@link ClubAccessPolicy} — không chép lại ở đây.
 */
@Component
@RequiredArgsConstructor
class ClubSupport {
    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final TournamentRegistrationRepository tournamentRegistrationRepository;
    private final ClubConversationSync conversationSync;

    Club requireClub(UUID clubId) throws ServiceException {
        return clubRepository.findById(clubId).orElseThrow(() -> new ServiceException("Không tìm thấy câu lạc bộ."));
    }

    /**
     * Mọi thao tác GHI phải đi qua hàm này: CLB giải tán chỉ là cờ trạng thái (không xóa), nên nếu một đường ghi
     * dùng {@link #requireClub} thì CLB đã giải tán vẫn tiếp tục hoạt động được. Đọc thì dùng requireClub để còn
     * hiển thị được trang "đã giải tán".
     */
    Club requireActiveClub(UUID clubId) throws ServiceException {
        Club club = requireClub(clubId);
        if (isDisbanded(club)) throw new ServiceException("Câu lạc bộ đã giải tán.");
        return club;
    }

    boolean isDisbanded(Club club) {
        return club.getDisbandedAt() != null || Boolean.FALSE.equals(club.getActive());
    }

    boolean isActiveClub(UUID clubId) {
        return clubRepository.findById(clubId).map(club -> !isDisbanded(club)).orElse(false);
    }

    void assertManager(Club club, UUID userId) throws ServiceException {
        if (!isManager(club, userId)) throw new ServiceException("Bạn không có quyền quản lý câu lạc bộ.");
    }

    boolean isManager(Club club, UUID userId) {
        return ClubAccessPolicy.isManager(club,
                memberRepository.findByClubIdAndUserId(club.getClubId(), userId).orElse(null), userId);
    }

    List<UUID> activeMemberIds(UUID clubId) {
        return memberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream().map(ClubMember::getUserId).toList();
    }

    /**
     * Đẩy toàn bộ danh sách thành viên đang hoạt động sang social-service. Đồng bộ cả danh sách thay vì
     * thêm/bớt từng người nên một lần gọi lỗi sẽ tự khỏi ở lần đổi thành viên kế tiếp.
     */
    Club syncConversation(Club club) {
        conversationSync.sync(club, activeMemberIds(club.getClubId())).ifPresent(conversationId -> {
            if (!conversationId.equals(club.getConversationId())) {
                club.setConversationId(conversationId);
                clubRepository.save(club);
            }
        });
        return club;
    }

    ClubResponseCommand toResponse(Club c) {
        return ClubResponseCommand.builder()
                .clubId(c.getClubId()).ownerId(c.getOwnerId()).name(c.getName()).description(c.getDescription())
                .logoUrl(c.getLogoUrl()).bannerUrl(c.getBannerUrl()).city(c.getCity()).location(c.getLocation()).tags(c.getTags())
                .sportType(c.getSportType()).privacy(c.getPrivacy())
                .approvalMode(c.getApprovalMode()).conversationId(c.getConversationId()).active(c.getActive())
                .disbandedAt(c.getDisbandedAt())
                .winCount(c.getWinCount()).lossCount(c.getLossCount()).drawCount(c.getDrawCount())
                .matchCount(c.getMatchCount()).winRate(c.getWinRate()).eloRating(c.getEloRating())
                .memberCount(memberRepository.countByClubIdAndStatus(c.getClubId(), ClubMemberStatus.ACTIVE))
                .tournamentCount(tournamentRegistrationRepository.countDistinctTournamentIdByClubId(c.getClubId()))
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt()).build();
    }

    static ClubMemberResponseCommand toResponse(ClubMember m) {
        return ClubMemberResponseCommand.builder().membershipId(m.getMembershipId()).clubId(m.getClubId())
                .userId(m.getUserId()).role(m.getRole()).status(m.getStatus())
                .introMessage(m.getIntroMessage()).requestedAt(m.getRequestedAt()).joinedAt(m.getJoinedAt())
                .respondedBy(m.getRespondedBy()).respondedAt(m.getRespondedAt()).build();
    }

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
