package iuh.fit.clubservice.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class InviteClubMemberRequest {
    @NotNull(message = "Thiếu người được mời")
    private UUID inviteeId;
    @Size(max = 500, message = "Lời nhắn tối đa 500 ký tự")
    private String message;
}
