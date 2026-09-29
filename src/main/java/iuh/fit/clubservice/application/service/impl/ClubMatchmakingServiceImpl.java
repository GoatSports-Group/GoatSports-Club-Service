package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.ClubFriendlyMatchRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.service.ClubMatchmakingService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubFriendlyMatch;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.service.ClubEloPolicy;
import iuh.fit.clubservice.domain.service.ClubRecordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ClubMatchmakingServiceImpl implements ClubMatchmakingService {
    private final ClubSupport support;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubFriendlyMatchRepository friendlyMatchRepository;

    @Override
    public Optional<Profile> getProfile(UUID clubId) {
        return clubRepository.findById(clubId).filter(club -> !support.isDisbanded(club)).map(club -> {
            List<ClubMember> active = memberRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE);
            // Cùng quy tắc với ClubAccessPolicy.isManager: chủ CLB và thành viên ADMIN đang hoạt động.
            Set<UUID> managers = new LinkedHashSet<>();
            managers.add(club.getOwnerId());
            active.stream().filter(member -> member.getRole() == ClubRole.ADMIN)
                    .map(ClubMember::getUserId).forEach(managers::add);
            return new Profile(club.getClubId(), club.getName(), club.getLogoUrl(), club.getSportType(),
                    rating(club), List.copyOf(managers), active.size(), orZero(club.getMatchCount()),
                    club.getWinRate() == null ? 0D : club.getWinRate(),
                    Stream.concat(Stream.of(club.getOwnerId()), active.stream().map(ClubMember::getUserId))
                            .distinct().toList());
        });
    }

    @Override
    @Transactional(rollbackFor = ServiceException.class)
    public Map<UUID, Integer> applyFriendlyResult(FriendlyResult command) throws ServiceException {
        List<ClubFriendlyMatch> recorded = friendlyMatchRepository.findBySessionId(command.sessionId());
        if (!recorded.isEmpty()) {
            return recorded.stream().collect(Collectors.toMap(
                    ClubFriendlyMatch::getClubId, ClubFriendlyMatch::getEloAfter, (a, b) -> a, LinkedHashMap::new));
        }
        if (command.first().clubId().equals(command.second().clubId())) {
            throw new ServiceException("Hai bên của trận giao hữu phải là hai CLB khác nhau.");
        }
        Club first = support.requireClub(command.first().clubId());
        Club second = support.requireClub(command.second().clubId());
        int comparison = Integer.compare(command.first().score(), command.second().score());
        // Cả hai CLB tính từ ELO trước trận của nhau.
        int firstBefore = rating(first);
        int secondBefore = rating(second);
        int firstAfter = ClubEloPolicy.newRating(firstBefore, secondBefore, orZero(first.getMatchCount()), comparison);
        int secondAfter = ClubEloPolicy.newRating(secondBefore, firstBefore, orZero(second.getMatchCount()), -comparison);
        LocalDateTime playedAt = LocalDateTime.now();

        ClubRecordPolicy.apply(first, comparison, 1);
        ClubRecordPolicy.apply(second, -comparison, 1);
        first.setEloRating(firstAfter);
        second.setEloRating(secondAfter);
        clubRepository.save(first);
        clubRepository.save(second);
        friendlyMatchRepository.saveAll(List.of(
                record(command, first, second, command.first(), command.second(), comparison, firstBefore, firstAfter, playedAt),
                record(command, second, first, command.second(), command.first(), -comparison, secondBefore, secondAfter, playedAt)));

        Map<UUID, Integer> ratings = new LinkedHashMap<>();
        ratings.put(first.getClubId(), firstAfter);
        ratings.put(second.getClubId(), secondAfter);
        return ratings;
    }

    private static ClubFriendlyMatch record(FriendlyResult command, Club club, Club opponent, Side mine, Side theirs,
                                            int comparison, int before, int after, LocalDateTime playedAt) {
        return ClubFriendlyMatch.builder()
                .sessionId(command.sessionId()).clubId(club.getClubId())
                .opponentClubId(opponent.getClubId()).opponentName(opponent.getName())
                .sportType(command.sportType()).playFormat(command.playFormat())
                .clubScore(mine.score()).opponentScore(theirs.score())
                .result(comparison > 0 ? "WIN" : comparison < 0 ? "LOSS" : "DRAW")
                .eloBefore(before).eloAfter(after).playedAt(playedAt)
                .build();
    }

    private static int rating(Club club) {
        return club.getEloRating() == null ? ClubEloPolicy.INITIAL_RATING : club.getEloRating();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
