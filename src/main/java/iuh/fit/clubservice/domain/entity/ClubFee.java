package iuh.fit.clubservice.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClubFee extends BaseEntity {
    private UUID feeId;
    private UUID clubId;
    private String name;
    private Double amount;
    private LocalDate dueDate;
    private Boolean required;
}
