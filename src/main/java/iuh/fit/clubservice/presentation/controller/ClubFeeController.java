package iuh.fit.clubservice.presentation.controller;

import iuh.fit.clubservice.application.dto.request.CreateClubFeeCommand;
import iuh.fit.clubservice.application.dto.response.ClubFeePaymentResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubFeeResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.security.CurrentUserProvider;
import iuh.fit.clubservice.application.service.ClubFeeService;
import iuh.fit.clubservice.presentation.annotation.ApiMessage;
import iuh.fit.clubservice.presentation.request.CreateClubFeeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clubs/{clubId}/fees")
@RequiredArgsConstructor
public class ClubFeeController {
    private final ClubFeeService clubFeeService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping @ApiMessage("Lấy danh sách khoản phí thành công")
    public List<ClubFeeResponseCommand> fees(@PathVariable UUID clubId) throws ServiceException {
        return clubFeeService.getClubFees(clubId, requireUser());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiMessage("Tạo khoản phí thành công")
    public ClubFeeResponseCommand create(@PathVariable UUID clubId,
                                         @Valid @RequestBody CreateClubFeeRequest request) throws ServiceException {
        return clubFeeService.createFee(CreateClubFeeCommand.builder()
                .clubId(clubId).actorUserId(requireUser()).name(request.getName())
                .amount(request.getAmount()).dueDate(request.getDueDate()).required(request.getRequired())
                .build());
    }

    @GetMapping("/{feeId}/payments")
    @ApiMessage("Lấy lịch sử đóng phí thành công")
    public List<ClubFeePaymentResponseCommand> payments(@PathVariable UUID clubId,
                                                        @PathVariable UUID feeId) throws ServiceException {
        return clubFeeService.getFeePayments(clubId, feeId, requireUser());
    }

    @PostMapping("/{feeId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiMessage("Khởi tạo đóng phí thành công")
    public ClubFeePaymentResponseCommand initiate(@PathVariable UUID clubId,
                                                  @PathVariable UUID feeId) throws ServiceException {
        return clubFeeService.initiatePayment(clubId, feeId, requireUser());
    }

    @PatchMapping("/{feeId}/members/{membershipId}/waive")
    @ApiMessage("Miễn khoản phí thành công")
    public ClubFeePaymentResponseCommand waive(@PathVariable UUID clubId, @PathVariable UUID feeId,
                                               @PathVariable UUID membershipId) throws ServiceException {
        return clubFeeService.waiveFee(clubId, feeId, membershipId, requireUser());
    }

    private UUID requireUser() throws ServiceException {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ServiceException("Bạn cần đăng nhập để thực hiện thao tác này."));
    }
}
