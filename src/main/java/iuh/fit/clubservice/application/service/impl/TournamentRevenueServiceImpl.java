package iuh.fit.clubservice.application.service.impl;

import iuh.fit.clubservice.application.exception.ServiceException;
import iuh.fit.clubservice.application.port.persistence.TournamentPaymentRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRegistrationRepository;
import iuh.fit.clubservice.application.port.persistence.TournamentRepository;
import iuh.fit.clubservice.application.service.TournamentRevenueService;
import iuh.fit.clubservice.domain.entity.Tournament;
import iuh.fit.clubservice.domain.entity.TournamentPayment;
import iuh.fit.clubservice.domain.enums.FeePaymentStatus;
import iuh.fit.clubservice.domain.enums.TournamentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TournamentRevenueServiceImpl implements TournamentRevenueService {
    private static final long MAX_RANGE_DAYS = 366;

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentPaymentRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public OwnerTournamentRevenue getOwnerRevenue(UUID ownerId, UUID venueId, LocalDate fromDate, LocalDate toDate)
            throws ServiceException {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new ServiceException("Khoảng thời gian không hợp lệ.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) + 1 > MAX_RANGE_DAYS) {
            throw new ServiceException("Chỉ xem được tối đa " + MAX_RANGE_DAYS + " ngày.");
        }

        List<Entry> entries = new ArrayList<>();
        double fees = 0;
        double prizes = 0;
        int paid = 0;
        List<Tournament> tournaments = tournamentRepository.findByOrganizerId(ownerId, Pageable.unpaged()).getContent();
        for (Tournament tournament : tournaments) {
            if (venueId != null && !venueId.equals(tournament.getVenueId())) continue;

            // Lệ phí: gom theo ngày tiền về. Khoản đã chuyển sang REFUND_REQUESTED là tiền trả lại người chơi, không tính.
            Map<LocalDate, double[]> byDay = new TreeMap<>();
            registrationRepository.findByTournamentId(tournament.getTournamentId()).forEach(registration ->
                    paymentRepository.findByRegistrationId(registration.getRegistrationId())
                            .filter(payment -> isCollected(payment, fromDate, toDate))
                            .ifPresent(payment -> {
                                double[] day = byDay.computeIfAbsent(payment.getPaidAt().toLocalDate(), key -> new double[2]);
                                day[0] += payment.getAmount();
                                day[1] += 1;
                            }));
            for (Map.Entry<LocalDate, double[]> day : byDay.entrySet()) {
                entries.add(new Entry(day.getKey(), tournament.getTournamentId(), tournament.getName(),
                        tournament.getVenueId(), "FEE", day.getValue()[0], (int) day.getValue()[1]));
                fees += day.getValue()[0];
                paid += (int) day.getValue()[1];
            }

            // Giải thưởng: trừ vào ngày giải kết thúc (giải cũ chưa lưu thời điểm thì lấy ngày kết thúc dự kiến).
            double prize = tournament.getPrizePool() == null ? 0 : tournament.getPrizePool();
            if (tournament.getStatus() == TournamentStatus.COMPLETED && prize > 0) {
                LocalDate completed = tournament.getCompletedAt() != null
                        ? tournament.getCompletedAt().toLocalDate() : tournament.getEndDate();
                if (completed != null && !completed.isBefore(fromDate) && !completed.isAfter(toDate)) {
                    entries.add(new Entry(completed, tournament.getTournamentId(), tournament.getName(),
                            tournament.getVenueId(), "PRIZE", prize, 1));
                    prizes += prize;
                }
            }
        }
        entries.sort(Comparator.comparing(Entry::date).thenComparing(Entry::tournamentName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return new OwnerTournamentRevenue(fees, prizes, fees - prizes, paid, entries);
    }

    private static boolean isCollected(TournamentPayment payment, LocalDate fromDate, LocalDate toDate) {
        if (payment.getStatus() != FeePaymentStatus.SUCCEEDED || payment.getPaidAt() == null
                || payment.getAmount() == null || payment.getAmount() <= 0) return false;
        LocalDate day = payment.getPaidAt().toLocalDate();
        return !day.isBefore(fromDate) && !day.isAfter(toDate);
    }
}
