package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.TournamentFormat;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import iuh.fit.clubservice.domain.enums.SportType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tournaments")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tournament_id")
    private UUID tournamentId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 3000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false)
    private SportType sportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private TournamentFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TournamentStatus status;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "entry_fee")
    private Double entryFee;

    @Column(name = "prize_pool")
    private Double prizePool;

    @Column(name = "registration_open_date")
    private LocalDate registrationOpenDate;

    @Column(name = "registration_close_date")
    private LocalDate registrationCloseDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "rules", columnDefinition = "jsonb")
    private List<String> rules;

    @JsonIgnore
    @OneToMany(mappedBy = "tournament", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TournamentRegistrationEntity> registrations = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "tournament", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TournamentFixtureEntity> fixtures = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "tournament", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TournamentVenueReservationEntity> reservations = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "tournament", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TournamentEligibilityRuleEntity> eligibilityRules = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "tournament", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TournamentStandingEntity> standings = new HashSet<>();
}
