package iuh.fit.clubservice.presentation.converter;

import iuh.fit.clubservice.application.dto.request.CreateClubCommand;
import iuh.fit.clubservice.domain.enums.ClubApprovalMode;
import iuh.fit.clubservice.domain.enums.ClubPrivacy;
import iuh.fit.clubservice.domain.enums.SportType;
import iuh.fit.clubservice.presentation.request.CreateClubRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClubConverterTest {

    private final ClubConverter converter = new ClubConverter();

    @Test
    void createCommandKeepsCityAndLocationFromRequest() {
        UUID ownerId = UUID.randomUUID();
        CreateClubRequest request = CreateClubRequest.builder()
                .name("GOAT Weekend Club")
                .description("Cộng đồng cuối tuần")
                .city("ho-chi-minh")
                .location("Quận 7, TP. Hồ Chí Minh")
                .sportType(SportType.BADMINTON)
                .privacy(ClubPrivacy.PUBLIC)
                .approvalMode(ClubApprovalMode.AUTO)
                .build();

        CreateClubCommand command = converter.toCommand(ownerId, request);

        assertThat(command.getOwnerId()).isEqualTo(ownerId);
        assertThat(command.getCity()).isEqualTo("ho-chi-minh");
        assertThat(command.getLocation()).isEqualTo("Quận 7, TP. Hồ Chí Minh");
    }
}
