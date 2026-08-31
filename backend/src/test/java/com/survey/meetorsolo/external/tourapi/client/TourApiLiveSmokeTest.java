package com.survey.meetorsolo.external.tourapi.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.external.tourapi.dto.SearchFestivalRequest;
import com.survey.meetorsolo.external.tourapi.dto.TourApiArrange;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TOUR_API_LIVE_TEST", matches = "true")
class TourApiLiveSmokeTest {

    @Autowired
    private TourApiClient client;

    @Test
    void 실제_searchFestival2에서_강원도_축제를_조회한다() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var page = client.searchFestivals(new SearchFestivalRequest(
                today.minusMonths(1),
                today.plusYears(1),
                null,
                1,
                10,
                TourApiArrange.MODIFIED,
                "51",
                null,
                "EV",
                "EV01",
                null
        ));

        assertThat(page.totalCount()).isGreaterThanOrEqualTo(0);
        assertThat(page.items())
                .allSatisfy(item -> assertThat(item.contentTypeId()).isEqualTo("15"));
    }
}
