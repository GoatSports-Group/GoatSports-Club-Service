package iuh.fit.clubservice.application.port.external;

import iuh.fit.clubservice.application.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.UUID;

/** Thu va hoan le phi giai dau qua payment-service. So tien luon do club-service tinh tu giai. */
public interface TournamentFeePayment {

    record Checkout(UUID paymentId, String status, String checkoutUrl, String qrCodeContent, LocalDateTime expiresAt) { }

    Checkout checkout(UUID registrationId, UUID payerId, double amount, String description, LocalDateTime deadline)
            throws ServiceException;

    /**
     * Yeu cau hoan toan bo le phi da thu (idempotent theo registration). Tra false neu payment-service loi:
     * dang ky van bi huy/tu choi, chi la trang thai REFUND_REQUESTED khong duoc dat va can xu ly tay.
     */
    boolean refund(UUID paymentId, UUID registrationId, UUID requestedBy, double amount, String reason);

    /** Ghi lệ phí đã thu vào doanh thu của người tổ chức (để họ rút về ngân hàng). Idempotent; lỗi thì ném ra. */
    void assignPayee(UUID paymentId, UUID organizerId);
}
