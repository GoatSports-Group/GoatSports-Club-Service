package iuh.fit.clubservice.presentation.request;

import iuh.fit.clubservice.domain.enums.ClubRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeClubMemberRoleRequest {
    @NotNull(message = "Vai trò không được để trống")
    private ClubRole role;
}
