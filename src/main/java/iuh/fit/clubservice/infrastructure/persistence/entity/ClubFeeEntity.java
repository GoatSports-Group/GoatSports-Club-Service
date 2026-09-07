package iuh.fit.clubservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "club_fees")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClubFeeEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fee_id")
    private UUID feeId;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", insertable = false, updatable = false)
    private ClubEntity club;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "required", nullable = false)
    private Boolean required;

    @OneToMany(mappedBy = "fee", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ClubFeePaymentEntity> payments = new HashSet<>();
}
