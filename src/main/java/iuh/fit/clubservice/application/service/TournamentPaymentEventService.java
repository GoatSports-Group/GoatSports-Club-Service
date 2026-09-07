package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.exception.ServiceException;
import java.time.LocalDateTime;
import java.util.UUID;

public interface TournamentPaymentEventService {
    void apply(UUID paymentId, UUID payerId, UUID registrationId, Double amount, String currency,
               boolean succeeded, LocalDateTime paidAt) throws ServiceException;
}
