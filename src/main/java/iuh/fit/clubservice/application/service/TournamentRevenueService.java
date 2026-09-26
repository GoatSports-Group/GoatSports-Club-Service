package iuh.fit.clubservice.application.service;

import iuh.fit.clubservice.application.exception.ServiceException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Doanh thu giải đấu của chủ sân (người tổ chức): lệ phí người chơi đã đóng được cộng theo ngày thanh toán,
 * giải thưởng được trừ vào ngày giải kết thúc. Lệ phí đã hoàn (rút đội, bị từ chối, hủy giải) không được tính.
 */
public interface TournamentRevenueService {

    OwnerTournamentRevenue getOwnerRevenue(UUID ownerId, UUID venueId, LocalDate fromDate, LocalDate toDate)
            throws ServiceException;

    record OwnerTournamentRevenue(double feeIncome, double prizeExpense, double net, int paidRegistrations,
                                  List<Entry> entries) { }

    record Entry(LocalDate date, UUID tournamentId, String tournamentName, UUID venueId, String kind,
                 double amount, int count) { }
}
