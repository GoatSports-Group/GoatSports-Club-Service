package iuh.fit.clubservice.presentation.request;

import lombok.*;
import jakarta.validation.constraints.Size;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class JoinClubRequest {
    @Size(max = 500, message = "Lời giới thiệu không được vượt quá 500 ký tự")
    private String message;
}
