package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.response.FeaturedClubResponseCommand;
import iuh.fit.clubservice.application.port.persistence.ClubActivityRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.service.ClubFeaturedService;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.service.ClubFeaturedPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClubFeaturedServiceImpl implements ClubFeaturedService {
    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubActivityRepository activityRepository;
    private final ClubSupport support;

    @Override
    @Transactional(readOnly = true)
    public Page<FeaturedClubResponseCommand> getFeatured(UUID viewerId, Collection<SportType> sports,
            Collection<String> cities, SportType filterSport, String filterCity, Pageable pageable) {
        return getFeatured(viewerId, sports, cities, filterSport, filterCity, pageable, LocalDateTime.now());
    }

    // ponytail: cham diem toan bo CLB cong khai trong bo nho moi lan goi (vai bang group-by, khong N+1).
    // Du cho vai nghin CLB; vuot muc do thi tinh diem dinh ky vao mot cot va cache theo (mon, tinh).
    Page<FeaturedClubResponseCommand> getFeatured(UUID viewerId, Collection<SportType> sports,
            Collection<String> cities, SportType filterSport, String filterCity, Pageable pageable, LocalDateTime now) {
        Set<UUID> joined = viewerId == null ? Set.of() : viewerClubIds(viewerId);
        String onlyCity = filterCity == null || filterCity.isBlank() ? null : filterCity.trim();
        List<Club> candidates = clubRepository.findFeaturedCandidates().stream()
                .filter(club -> !joined.contains(club.getClubId()))
                .filter(club -> filterSport == null || filterSport == club.getSportType())
                .filter(club -> onlyCity == null || onlyCity.equals(club.getCity()))
                .toList();
        List<UUID> ids = candidates.stream().map(Club::getClubId).toList();

        LocalDateTime recentFrom = now.minusDays(ClubFeaturedPolicy.RECENT_DAYS);
        LocalDateTime upcomingTo = now.plusDays(ClubFeaturedPolicy.UPCOMING_DAYS);
        Map<UUID, Long> members = memberRepository.countActiveByClubIds(ids);
        Map<UUID, Long> newMembers = memberRepository.countJoinedSinceByClubIds(ids, recentFrom);
        Map<UUID, Long> recent = activityRepository.countByClubIdsBetween(ids, recentFrom, upcomingTo);
        Map<UUID, Long> window = activityRepository.countByClubIdsBetween(
                ids, now.minusDays(ClubFeaturedPolicy.ACTIVE_WINDOW_DAYS), upcomingTo);

        Set<SportType> viewerSports = sports == null ? Set.of() : Set.copyOf(sports);
        Set<String> viewerCities = cities == null ? Set.of() : cities.stream()
                .filter(Objects::nonNull).map(String::trim).filter(city -> !city.isEmpty())
                .collect(Collectors.toSet());

        List<Ranked> ranked = candidates.stream()
                .map(club -> new Ranked(club, new ClubFeaturedPolicy.Stats(
                        club.getSportType(), club.getCity(),
                        members.getOrDefault(club.getClubId(), 0L),
                        newMembers.getOrDefault(club.getClubId(), 0L),
                        recent.getOrDefault(club.getClubId(), 0L),
                        window.getOrDefault(club.getClubId(), 0L),
                        orZero(club.getWinCount()), orZero(club.getDrawCount()), orZero(club.getMatchCount()))))
                .filter(item -> ClubFeaturedPolicy.eligible(item.stats()))
                .map(item -> item.withScore(ClubFeaturedPolicy.score(item.stats(), viewerSports, viewerCities)))
                .sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparing(item -> item.club().getName(), Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        int from = (int) Math.min(pageable.getOffset(), ranked.size());
        int to = Math.min(from + pageable.getPageSize(), ranked.size());
        List<FeaturedClubResponseCommand> content = ranked.subList(from, to).stream()
                .map(item -> FeaturedClubResponseCommand.builder()
                        .club(support.toResponse(item.club()))
                        .score(Math.round(item.score() * 10) / 10.0)
                        .sameSport(ClubFeaturedPolicy.sameSport(item.stats(), viewerSports))
                        .sameCity(ClubFeaturedPolicy.sameCity(item.stats(), viewerCities))
                        .recentActivities(item.stats().recentActivities())
                        .newMembers(item.stats().newMembers())
                        .build())
                .toList();
        return new PageImpl<>(content, pageable, ranked.size());
    }

    /** CLB nguoi xem da tham gia hoac dang cho duyet: khong goi y lai. */
    private Set<UUID> viewerClubIds(UUID viewerId) {
        Set<UUID> ids = new HashSet<>();
        for (ClubMemberStatus status : List.of(ClubMemberStatus.ACTIVE, ClubMemberStatus.PENDING)) {
            memberRepository.findByUserIdAndStatus(viewerId, status).stream().map(ClubMember::getClubId).forEach(ids::add);
        }
        return ids;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record Ranked(Club club, ClubFeaturedPolicy.Stats stats, double score) {
        Ranked(Club club, ClubFeaturedPolicy.Stats stats) {
            this(club, stats, 0);
        }

        Ranked withScore(double value) {
            return new Ranked(club, stats, value);
        }
    }
}
