package iuh.fit.clubservice.application.dto.response;

import lombok.*;

/** Mot CLB noi bat: thong tin CLB + diem va so lieu de giao dien ghi ly do ("Cung mon · 6 buoi trong 30 ngay"). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedClubResponseCommand {
    private ClubResponseCommand club;
    /** Diem 0-100 da nhan he so ca nhan hoa (co the vuot 100). */
    private double score;
    private boolean sameSport;
    private boolean sameCity;
    /** So buoi sinh hoat trong 30 ngay qua va 14 ngay toi. */
    private long recentActivities;
    /** Thanh vien moi trong 30 ngay. */
    private long newMembers;
}
