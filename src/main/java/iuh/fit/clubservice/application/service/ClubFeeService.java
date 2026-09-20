package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.dto.request.CreateClubFeeCommand;
import iuh.fit.clubservice.application.dto.response.ClubFeePaymentResponseCommand;
import iuh.fit.clubservice.application.dto.response.ClubFeeResponseCommand;
import iuh.fit.clubservice.application.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ClubFeeService {
    ClubFeeResponseCommand createFee(CreateClubFeeCommand command) throws ServiceException;

    List<ClubFeeResponseCommand> getClubFees(UUID clubId, UUID requesterId) throws ServiceException;

    List<ClubFeePaymentResponseCommand> getFeePayments(UUID clubId, UUID feeId, UUID actorUserId)
            throws ServiceException;

    ClubFeePaymentResponseCommand waiveFee(UUID clubId, UUID feeId, UUID membershipId, UUID actorUserId)
            throws ServiceException;

    ClubFeePaymentResponseCommand initiatePayment(UUID clubId, UUID feeId, UUID userId) throws ServiceException;

    void applyPayment(UUID paymentId, UUID payerId, UUID feePaymentId, Double amount, String currency,
                      boolean succeeded, LocalDateTime paidAt) throws ServiceException;
}
