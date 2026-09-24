package iuh.fit.clubservice.domain.service;

import iuh.fit.clubservice.domain.enums.ParticipantType;
import iuh.fit.clubservice.domain.enums.SportType;

import java.util.List;
import java.util.Optional;

/**
 * Hinh thuc thi dau hop le theo mon: mon vot danh don (ca nhan) hoac danh doi (doi 2 nguoi);
 * mon dong doi luon theo doi, so nguoi tren san tuy the thuc. Ban to chuc chi chon ma hinh thuc,
 * so nguoi toi thieu/toi da cua doi do server suy ra de nguoi choi khong nhan duoc gia tri tu dat.
 */
public final class SportParticipationPolicy {

    /**
     * @param onField  so nguoi thi dau cung luc cua mot ben
     * @param rosterMin so nguoi toi thieu da xac nhan de doi duoc dong le phi / giu suat
     * @param rosterMax so nguoi toi da trong doi hinh (gom du bi)
     */
    public record PlayFormat(String code, String label, SportType sport, ParticipantType participantType,
                             int onField, int rosterMin, int rosterMax) {
        public boolean isTeam() { return participantType == ParticipantType.TEAM; }
    }

    private static final List<PlayFormat> FORMATS = List.of(
            new PlayFormat("BADMINTON_SINGLES", "Đánh đơn", SportType.BADMINTON, ParticipantType.INDIVIDUAL, 1, 1, 1),
            new PlayFormat("BADMINTON_DOUBLES", "Đánh đôi", SportType.BADMINTON, ParticipantType.TEAM, 2, 2, 2),
            new PlayFormat("TENNIS_SINGLES", "Đánh đơn", SportType.TENNIS, ParticipantType.INDIVIDUAL, 1, 1, 1),
            new PlayFormat("TENNIS_DOUBLES", "Đánh đôi", SportType.TENNIS, ParticipantType.TEAM, 2, 2, 2),
            new PlayFormat("PICKLEBALL_SINGLES", "Đánh đơn", SportType.PICKLEBALL, ParticipantType.INDIVIDUAL, 1, 1, 1),
            new PlayFormat("PICKLEBALL_DOUBLES", "Đánh đôi", SportType.PICKLEBALL, ParticipantType.TEAM, 2, 2, 2),
            new PlayFormat("FOOTBALL_5", "Bóng đá 5 người", SportType.FOOTBALL, ParticipantType.TEAM, 5, 5, 10),
            new PlayFormat("FOOTBALL_7", "Bóng đá 7 người", SportType.FOOTBALL, ParticipantType.TEAM, 7, 7, 14),
            new PlayFormat("FOOTBALL_11", "Bóng đá 11 người", SportType.FOOTBALL, ParticipantType.TEAM, 11, 11, 22),
            new PlayFormat("BASKETBALL_3X3", "Bóng rổ 3x3", SportType.BASKETBALL, ParticipantType.TEAM, 3, 3, 4),
            new PlayFormat("BASKETBALL_5X5", "Bóng rổ 5x5", SportType.BASKETBALL, ParticipantType.TEAM, 5, 5, 12),
            new PlayFormat("VOLLEYBALL_6", "Bóng chuyền 6 người", SportType.VOLLEYBALL, ParticipantType.TEAM, 6, 6, 12)
    );

    private SportParticipationPolicy() { }

    public static List<PlayFormat> formatsOf(SportType sport) {
        return FORMATS.stream().filter(format -> format.sport() == sport).toList();
    }

    public static Optional<PlayFormat> find(String code) {
        return FORMATS.stream().filter(format -> format.code().equals(code)).findFirst();
    }

    /** Hinh thuc cua giai; giai cu chua luu ma thi lay hinh thuc dau tien cua mon (don / doi nho nhat). */
    public static PlayFormat resolve(SportType sport, String code) {
        return find(code).filter(format -> format.sport() == sport)
                .orElseGet(() -> formatsOf(sport).get(0));
    }
}
