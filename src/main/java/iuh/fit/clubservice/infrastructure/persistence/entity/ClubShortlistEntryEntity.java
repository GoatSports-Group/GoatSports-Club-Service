package iuh.fit.clubservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "club_scouting_shortlist",
        uniqueConstraints = @UniqueConstraint(name = "uk_club_scouting_shortlist_player", columnNames = {"club_id", "user_id"}))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ClubShortlistEntryEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "entry_id")
    private UUID entryId;
    @Column(name = "club_id", nullable = false)
    private UUID clubId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "note", length = 280)
    private String note;
    @Column(name = "added_by", nullable = false)
    private UUID addedBy;
}
