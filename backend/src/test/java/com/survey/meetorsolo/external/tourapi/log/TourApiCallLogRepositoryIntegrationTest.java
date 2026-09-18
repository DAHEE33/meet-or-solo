package com.survey.meetorsolo.external.tourapi.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.festival.sync.enabled=false"
})
@Transactional
class TourApiCallLogRepositoryIntegrationTest {

    @Autowired
    private TourApiCallLogRepository repository;

    @Test
    void 기존_tour_api_call_logs_테이블에_호출_이력을_저장한다() {
        String requestKey = "integration-test-" + UUID.randomUUID();
        TourApiCallLog callLog = TourApiCallLog.success(
                "searchFestival2",
                requestKey,
                "SoloIn",
                200,
                120,
                3,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00")
        );

        TourApiCallLog saved = repository.saveAndFlush(callLog);

        TourApiCallLog found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getRequestKey()).isEqualTo(requestKey);
        assertThat(found.isSuccess()).isTrue();
        assertThat(found.getResultCount()).isEqualTo(3);
    }
}
