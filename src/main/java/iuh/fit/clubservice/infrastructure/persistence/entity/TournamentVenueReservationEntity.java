package iuh.fit.clubservice.infrastructure.persistence.entity;

import iuh.fit.clubservice.domain.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "tournament_venue_reservations")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentVenueReservationEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", insertable = false, updatable = false)
    private TournamentEntity tournament;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "court_id", nullable = false)
    private UUID courtId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "play_date", nullable = false)
    private LocalDate playDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @OneToOne(mappedBy = "reservation", fetch = FetchType.LAZY)
    private TournamentFixtureEntity fixture;
}
