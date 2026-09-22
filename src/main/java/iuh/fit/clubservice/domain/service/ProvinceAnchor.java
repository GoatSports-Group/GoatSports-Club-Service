package iuh.fit.clubservice.domain.service;

import java.util.Map;
import java.util.Optional;

/**
 * Diem neo du phong cho cau lac bo chua co toa do rieng.
 *
 * Gia tri la toa do trung tam hanh chinh cua tinh/thanh, khong phai trong tam hinh hoc,
 * nen khoang cach suy ra chi chinh xac o muc tinh. Khi CLB tu dat latitude/longitude
 * thi gia tri do duoc uu tien va bang nay khong con duoc dung toi.
 */
public final class ProvinceAnchor {

    private ProvinceAnchor() { }

    public record Coordinates(double latitude, double longitude) { }

    private static final Map<String, Coordinates> CENTERS = Map.ofEntries(
            Map.entry("an-giang", new Coordinates(10.386, 105.436)),
            Map.entry("bac-ninh", new Coordinates(21.186, 106.076)),
            Map.entry("ca-mau", new Coordinates(9.177, 105.150)),
            Map.entry("can-tho", new Coordinates(10.045, 105.747)),
            Map.entry("cao-bang", new Coordinates(22.666, 106.258)),
            Map.entry("da-nang", new Coordinates(16.047, 108.206)),
            Map.entry("dak-lak", new Coordinates(12.688, 108.078)),
            Map.entry("dien-bien", new Coordinates(21.386, 103.017)),
            Map.entry("dong-nai", new Coordinates(10.945, 106.824)),
            Map.entry("dong-thap", new Coordinates(10.460, 105.634)),
            Map.entry("gia-lai", new Coordinates(13.983, 108.000)),
            Map.entry("ha-noi", new Coordinates(21.028, 105.854)),
            Map.entry("ha-tinh", new Coordinates(18.343, 105.906)),
            Map.entry("hai-phong", new Coordinates(20.865, 106.684)),
            Map.entry("ho-chi-minh", new Coordinates(10.776, 106.701)),
            Map.entry("hue", new Coordinates(16.463, 107.590)),
            Map.entry("hung-yen", new Coordinates(20.646, 106.051)),
            Map.entry("khanh-hoa", new Coordinates(12.239, 109.196)),
            Map.entry("lai-chau", new Coordinates(22.396, 103.458)),
            Map.entry("lam-dong", new Coordinates(11.940, 108.458)),
            Map.entry("lang-son", new Coordinates(21.853, 106.761)),
            Map.entry("lao-cai", new Coordinates(22.486, 103.975)),
            Map.entry("nghe-an", new Coordinates(18.679, 105.681)),
            Map.entry("ninh-binh", new Coordinates(20.251, 105.974)),
            Map.entry("phu-tho", new Coordinates(21.322, 105.402)),
            Map.entry("quang-ngai", new Coordinates(15.121, 108.792)),
            Map.entry("quang-ninh", new Coordinates(20.951, 107.079)),
            Map.entry("quang-tri", new Coordinates(16.816, 107.100)),
            Map.entry("son-la", new Coordinates(21.328, 103.914)),
            Map.entry("tay-ninh", new Coordinates(11.311, 106.098)),
            Map.entry("thai-nguyen", new Coordinates(21.594, 105.848)),
            Map.entry("thanh-hoa", new Coordinates(19.807, 105.776)),
            Map.entry("tuyen-quang", new Coordinates(21.823, 105.214)),
            Map.entry("vinh-long", new Coordinates(10.253, 105.972))
    );

    public static Optional<Coordinates> of(String provinceCode) {
        if (provinceCode == null || provinceCode.isBlank()) return Optional.empty();
        return Optional.ofNullable(CENTERS.get(provinceCode.trim().toLowerCase()));
    }
}
