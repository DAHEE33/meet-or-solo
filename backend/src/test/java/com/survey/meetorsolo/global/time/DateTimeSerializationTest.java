package com.survey.meetorsolo.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class DateTimeSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createdAt과_updatedAt은_ISO_8601_KST로_일관되게_직렬화한다() throws Exception {
        AuditTimes value = new AuditTimes(
                OffsetDateTime.parse("2026-07-10T06:50:45Z"),
                OffsetDateTime.parse("2026-07-10T07:00:00Z")
        );

        String json = objectMapper.writeValueAsString(value);

        assertThat(json).contains("\"createdAt\":\"2026-07-10T15:50:45+09:00\"");
        assertThat(json).contains("\"updatedAt\":\"2026-07-10T16:00:00+09:00\"");
        assertThat(json).doesNotContain(".000");
    }

    @Test
    void null_날짜는_null로_직렬화한다() throws Exception {
        String json = objectMapper.writeValueAsString(new AuditTimes(null, null));

        assertThat(json).contains("\"createdAt\":null");
        assertThat(json).contains("\"updatedAt\":null");
    }

    private record AuditTimes(OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
