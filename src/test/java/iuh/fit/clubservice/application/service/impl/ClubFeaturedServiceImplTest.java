package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.dto.response.FeaturedClubResponseCommand;
import iuh.fit.clubservice.application.port.external.ClubConversationSync;
import iuh.fit.clubservice.application.port.persistence.ClubActivityRepository;
import iuh.fit.clubservice.application.port.persistence.ClubMemberRepository;
import iuh.fit.clubservice.application.port.persistence.ClubRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.domain.entity.Club;
import iuh.fit.clubservice.domain.entity.ClubMember;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.SportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClubFeaturedServiceImplTest {
    @Mock private ClubRepository clubRepository;
    @Mock private ClubMemberRepository memberRepository;
    @Mock private ClubActivityRepository activityRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private ClubConversationSync conversationSync;

    private ClubFeaturedServiceImpl service;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 2, 12, 0);
    private final UUID viewer = UUID.randomUUID();
    private final Club badminton = club("Cầu Lông Phú Nhuận", SportType.BADMINTON, "ho-chi-minh", 9, 2, 14);
    private final Club football = club("Bóng Đá Sài Gòn", SportType.FOOTBALL, "ho-chi-minh", 12, 3, 18);
    private final Club quiet = club("Ngủ Đông", SportType.FOOTBALL, "ha-noi", 20, 0, 25);
    private final Club joined = club("CLB Của Tôi", SportType.BADMINTON, "ho-chi-minh", 30, 0, 30);

    @BeforeEach
    void setUp() {
        service = new ClubFeaturedServiceImpl(clubRepository, memberRepository, activityRepository,
                new ClubSupport(clubRepository, memberRepository, registrationRepository, conversationSync));
        when(clubRepository.findFeaturedCandidates()).thenReturn(List.of(badminton, football, quiet, joined));
        when(memberRepository.countActiveByClubIds(any())).thenReturn(Map.of(
                badminton.getClubId(), 11L, football.getClubId(), 8L, quiet.getClubId(), 25L, joined.getClubId(), 40L));
        when(memberRepository.countJoinedSinceByClubIds(any(), any())).thenReturn(Map.of(badminton.getClubId(), 3L));
        // Cua so 30 ngay va cua so 60 ngay: "Ngủ Đông" khong co buoi nao -> bi loai.
        when(activityRepository.countByClubIdsBetween(any(), any(), any())).thenReturn(Map.of(
                badminton.getClubId(), 3L, football.getClubId(), 3L, joined.getClubId(), 8L));
        when(memberRepository.findByUserIdAndStatus(eq(viewer), eq(ClubMemberStatus.ACTIVE)))
                .thenReturn(List.of(ClubMember.builder().clubId(joined.getClubId()).build()));
        when(memberRepository.findByUserIdAndStatus(eq(viewer), eq(ClubMemberStatus.PENDING))).thenReturn(List.of());
    }

    @Test
    void guestsSeeEligibleClubsRankedByScore() {
        Page<FeaturedClubResponseCommand> page = service.getFeatured(null, List.of(), List.of(), null, null, PageRequest.of(0, 10), now);

        assertThat(page.getContent()).extracting(item -> item.getClub().getName())
                .containsExactly("CLB Của Tôi", "Cầu Lông Phú Nhuận", "Bóng Đá Sài Gòn");
        assertThat(page.getContent().get(1).getRecentActivities()).isEqualTo(3);
        assertThat(page.getContent().get(1).getNewMembers()).isEqualTo(3);
    }

    @Test
    void signedInViewerDoesNotSeeTheirOwnClubsAndSameSportIsBoostedNotFiltered() {
        double guestFootball = service.getFeatured(null, List.of(), List.of(), null, null, PageRequest.of(0, 10), now)
                .getContent().stream().filter(item -> item.getClub().getName().equals("Bóng Đá Sài Gòn"))
                .findFirst().orElseThrow().getScore();

        Page<FeaturedClubResponseCommand> page = service.getFeatured(
                viewer, List.of(SportType.FOOTBALL), List.of("ho-chi-minh"), null, null, PageRequest.of(0, 10), now);

        // CLB nguoi xem da tham gia bi loai; CLB khac mon van con (he so nhan, khong phai bo loc).
        assertThat(page.getContent()).extracting(item -> item.getClub().getName())
                .containsExactlyInAnyOrder("Bóng Đá Sài Gòn", "Cầu Lông Phú Nhuận");
        FeaturedClubResponseCommand football = page.getContent().stream()
                .filter(item -> item.getClub().getName().equals("Bóng Đá Sài Gòn")).findFirst().orElseThrow();
        assertThat(football.isSameSport()).isTrue();
        assertThat(football.isSameCity()).isTrue();
        assertThat(football.getScore()).isCloseTo(guestFootball * 1.25 * 1.15, org.assertj.core.data.Offset.offset(0.2));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void pagesSliceTheRankedList() {
        Page<FeaturedClubResponseCommand> second = service.getFeatured(null, null, null, null, null, PageRequest.of(1, 2), now);

        assertThat(second.getContent()).extracting(item -> item.getClub().getName()).containsExactly("Bóng Đá Sài Gòn");
        assertThat(second.getTotalElements()).isEqualTo(3);
    }

    private static Club club(String name, SportType sport, String city, int wins, int draws, int matches) {
        return Club.builder().clubId(UUID.randomUUID()).name(name).sportType(sport).city(city).active(true)
                .winCount(wins).drawCount(draws).lossCount(matches - wins - draws).matchCount(matches).build();
    }

    @Test
    void featuredPageFiltersBySportAndCityBeforeRanking() {
        Page<FeaturedClubResponseCommand> page = service.getFeatured(
                null, null, null, SportType.FOOTBALL, "ho-chi-minh", PageRequest.of(0, 10), now);

        assertThat(page.getContent()).extracting(item -> item.getClub().getName()).containsExactly("Bóng Đá Sài Gòn");
    }
}
