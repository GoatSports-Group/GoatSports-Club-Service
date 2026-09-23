package iuh.fit.clubservice.presentation.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveShortlistEntryRequest {
    @Size(max = 280, message = "Ghi chú tối đa 280 ký tự")
    private String note;
}
