package iuh.fit.clubservice.infrastructure.persistence;

import iuh.fit.clubservice.application.service.AdminCommunityService.ClubRow;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubMemberStatus;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.domain.enums.ClubRole;
import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.service.TournamentLifecyclePolicy;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubEntity;
import iuh.fit.clubservice.infrastructure.persistence.entity.ClubMemberEntity;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentEntity;
import iuh.fit.clubservice.infrastructure.persistence.entity.TournamentRegistrationEntity;
import iuh.fit.clubservice.infrastructure.persistence.mapper.TournamentEntityMapperImpl;
import iuh.fit.clubservice.infrastructure.persistence.mapper.TournamentRegistrationEntityMapperImpl;
import iuh.fit.clubservice.infrastructure.persistence.repository.TournamentRegistrationRepositoryImpl;
import iuh.fit.clubservice.infrastructure.persistence.repository.TournamentRepositoryImpl;
import iuh.fit.clubservice.infrastructure.persistence.repository.jpa.JpaClubRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Truy vấn danh sách CLB / giải đấu của admin chạy trên H2 (chế độ PostgreSQL). */
@DataJpaTest(properties = {
        // TournamentEntity khai báo cột jsonb (PostgreSQL); H2 chỉ có JSON nên tạo domain cùng tên.
        "spring.datasource.url=jdbc:h2:mem:club_admin_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.liquibase.enabled=false",
        // @EnableFeignClients ở class main vẫn được nạp: client cần URL dù test không gọi tới.
        "auth.service.url=http://localhost:9", "social.service.url=http://localhost:9",
        "venue.service.url=http://localhost:9", "payment.service.url=http://localhost:9",
        "storage.service.url=http://localhost:9"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TournamentRepositoryImpl.class, TournamentEntityMapperImpl.class,
        TournamentRegistrationRepositoryImpl.class, TournamentRegistrationEntityMapperImpl.class})
class AdminCommunityQueriesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Autowired private TestEntityManager em;
    @Autowired private JpaClubRepository clubs;
    @Autowired private TournamentRepositoryImpl tournaments;
    @Autowired private TournamentRegistrationRepositoryImpl registrations;

    @Test
    void clubRowsCountOnlyActiveMembersAndFilterByStateSportAndName() {
        UUID active = club("GOAT Cầu Lông", SportType.BADMINTON, false);
        club("Bóng Đá Quận 7", SportType.FOOTBALL, false);
        club("GOAT Cũ", SportType.BADMINTON, true);
        member(active, ClubMemberStatus.ACTIVE);
        member(active, ClubMemberStatus.ACTIVE);
        member(active, ClubMemberStatus.PENDING);
        member(active, ClubMemberStatus.LEFT);
        em.flush();
        em.clear();

        Page<ClubRow> goat = clubs.searchForAdmin(true, true, EnumSet.of(SportType.BADMINTON), "%goat%",
                ClubMemberStatus.ACTIVE, PageRequest.of(0, 10));
        assertThat(goat.getTotalElements()).isEqualTo(2);
        ClubRow activeRow = goat.getContent().stream().filter(row -> row.clubId().equals(active)).findFirst().orElseThrow();
        assertThat(activeRow.memberCount()).isEqualTo(2);
        assertThat(activeRow.disbanded()).isFalse();

        Page<ClubRow> disbanded = clubs.searchForAdmin(false, true, EnumSet.allOf(SportType.class), "%",
                ClubMemberStatus.ACTIVE, PageRequest.of(0, 10));
        assertThat(disbanded.getContent()).singleElement()
                .satisfies(row -> assertThat(row.disbanded()).isTrue());
    }

    @Test
    void tournamentSearchHidesDraftsAndSeatCountsComeFromOneGroupedQuery() {
        UUID open = tournament("Giải mở đăng ký", TournamentStatus.PUBLISHED, TODAY.minusDays(1), TODAY.plusDays(3));
        UUID cancelled = tournament("Giải đã hủy", TournamentStatus.CANCELLED, TODAY.minusDays(9), TODAY.minusDays(2));
        tournament("Bản nháp", TournamentStatus.DRAFT, TODAY.minusDays(1), TODAY.plusDays(3));
        registration(open, RegistrationStatus.CONFIRMED);
        registration(open, RegistrationStatus.PENDING_PAYMENT);
        registration(open, RegistrationStatus.CANCELLED);
        registration(cancelled, RegistrationStatus.CONFIRMED);
        em.flush();
        em.clear();

        Page<Tournament> all = tournaments.searchForAdmin(null, null, null, TODAY, PageRequest.of(0, 10));
        assertThat(all.getContent()).extracting(Tournament::getTournamentId).containsExactlyInAnyOrder(open, cancelled);

        Page<Tournament> openNow = tournaments.searchForAdmin(TournamentStatus.REGISTRATION_OPEN, null, null, TODAY,
                PageRequest.of(0, 10));
        assertThat(openNow.getContent()).extracting(Tournament::getTournamentId).containsExactly(open);

        Map<UUID, Long> seats = registrations.countByTournamentIdsAndStatuses(List.of(open, cancelled),
                TournamentLifecyclePolicy.HOLDING_SEAT);
        assertThat(seats).containsEntry(open, 2L).containsEntry(cancelled, 1L);
    }

    private UUID club(String name, SportType sport, boolean disbanded) {
        return em.persist(ClubEntity.builder()
                .name(name).sportType(sport).ownerId(UUID.randomUUID())
                .approvalMode(ClubApprovalMode.AUTO).privacy(ClubPrivacy.PUBLIC)
                .active(!disbanded).disbandedAt(disbanded ? TODAY.atStartOfDay() : null)
                .winCount(0).lossCount(0).drawCount(0).matchCount(0).winRate(0.0).eloRating(1000)
                .build()).getClubId();
    }

    private void member(UUID clubId, ClubMemberStatus status) {
        em.persist(ClubMemberEntity.builder()
                .clubId(clubId).userId(UUID.randomUUID()).role(ClubRole.MEMBER).status(status).build());
    }

    private UUID tournament(String name, TournamentStatus status, LocalDate opens, LocalDate closes) {
        return em.persist(TournamentEntity.builder()
                .name(name).sportType(SportType.BADMINTON).format(TournamentFormat.SINGLE_ELIMINATION)
                .status(status).organizerId(UUID.randomUUID()).maxParticipants(8)
                .registrationOpenDate(opens).registrationCloseDate(closes)
                .startDate(closes.plusDays(1)).endDate(closes.plusDays(2))
                .build()).getTournamentId();
    }

    private void registration(UUID tournamentId, RegistrationStatus status) {
        em.persist(TournamentRegistrationEntity.builder()
                .tournamentId(tournamentId).playerId(UUID.randomUUID()).type(RegistrationType.INDIVIDUAL)
                .status(status).registeredBy(UUID.randomUUID()).build());
    }
}
