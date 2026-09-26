package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.port.persistence.TournamentPaymentRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentPayment;
import iuh.fit.clubservice.domain.entity.TournamentRegistration;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentRevenueServiceImplTest {
    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentPaymentRepository paymentRepository;
    @InjectMocks private TournamentRevenueServiceImpl service;

    @Test
    void addsCollectedFeesAndSubtractsPrizeOnCompletionDay() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID tournamentId = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 9, 26);
        Tournament tournament = Tournament.builder().tournamentId(tournamentId).organizerId(owner).name("Giải cầu lông đôi")
                .status(TournamentStatus.COMPLETED).prizePool(8_000D).completedAt(day.atTime(21, 0)).endDate(day).build();
        UUID paid = UUID.randomUUID();
        UUID refunded = UUID.randomUUID();
        when(tournamentRepository.findByOrganizerId(eq(owner), any())).thenReturn(new PageImpl<>(List.of(tournament)));
        when(registrationRepository.findByTournamentId(tournamentId)).thenReturn(List.of(
                TournamentRegistration.builder().registrationId(paid).build(),
                TournamentRegistration.builder().registrationId(refunded).build()));
        when(paymentRepository.findByRegistrationId(paid)).thenReturn(Optional.of(TournamentPayment.builder()
                .amount(5_000D).status(FeePaymentStatus.SUCCEEDED).paidAt(LocalDateTime.of(day, java.time.LocalTime.NOON)).build()));
        when(paymentRepository.findByRegistrationId(refunded)).thenReturn(Optional.of(TournamentPayment.builder()
                .amount(5_000D).status(FeePaymentStatus.REFUND_REQUESTED).paidAt(LocalDateTime.of(day, java.time.LocalTime.NOON)).build()));

        var revenue = service.getOwnerRevenue(owner, null, day.minusDays(3), day);

        assertThat(revenue.feeIncome()).isEqualTo(5_000D);
        assertThat(revenue.prizeExpense()).isEqualTo(8_000D);
        assertThat(revenue.net()).isEqualTo(-3_000D);
        assertThat(revenue.entries()).extracting("kind").containsExactlyInAnyOrder("FEE", "PRIZE");
    }
}
