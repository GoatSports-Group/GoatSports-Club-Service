package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.RegistrationStatus;
import iuh.fit.clubservice.domain.enums.RegistrationType;
import iuh.fit.clubservice.domain.enums.SkillLevel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tournament_registrations")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentRegistrationEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "registration_id")
    private UUID registrationId;

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", insertable = false, updatable = false)
    private TournamentEntity tournament;

    @Column(name = "player_id")
    private UUID playerId;

    @Column(name = "club_id")
    private UUID clubId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", insertable = false, updatable = false)
    private ClubEntity club;

    @Column(name = "team_name")
    private String teamName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private RegistrationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RegistrationStatus status;

    @Column(name = "registered_by", nullable = false)
    private UUID registeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level")
    private SkillLevel skillLevel;

    @Column(name = "status_reason", length = 1000)
    private String statusReason;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @OneToMany(mappedBy = "registration", fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.Set<TournamentLineupEntity> lineups = new java.util.HashSet<>();

    @OneToOne(mappedBy = "registration", fetch = FetchType.LAZY)
    private TournamentPaymentEntity payment;

    @OneToOne(mappedBy = "registration", fetch = FetchType.LAZY)
    private TournamentStandingEntity standing;
}
