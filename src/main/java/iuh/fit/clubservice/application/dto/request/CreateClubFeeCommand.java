package iuh.fit.clubservice.application.dto.request;

import iuh.fit.clubservice.application.exception.ServiceException;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClubFeeCommand {
    private UUID clubId;
    private UUID actorUserId;
    private String name;
    private Double amount;
    private LocalDate dueDate;
    private Boolean required;

    public void validate() throws ServiceException {
        if (name == null || name.isBlank()) throw new ServiceException("Tên khoản phí không được để trống.");
        if (amount == null || amount < 0D) throw new ServiceException("Số tiền khoản phí không hợp lệ.");
        if (dueDate == null) throw new ServiceException("Hạn đóng phí không được để trống.");
    }
}
