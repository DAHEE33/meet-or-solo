package com.survey.meetorsolo.domain.festival.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.festival.history.service.CheckinHistoryCursorCodec;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CheckinHistoryCursorCodecTest {

    private final CheckinHistoryCursorCodec codec = new CheckinHistoryCursorCodec();

    @Test
    void 인코딩한_cursor를_같은_값으로_되읽는다() {
        OffsetDateTime checkedInAt = OffsetDateTime.of(2026, 7, 6, 10, 20, 30, 123_456_789, ZoneOffset.ofHours(9));

        CheckinHistoryCursorCodec.Cursor decoded = codec.decode(codec.encode(checkedInAt, 401L));

        // 저장은 epoch 기준이라 offset은 달라질 수 있어도 가리키는 순간은 같아야 한다.
        assertThat(decoded.checkedInAt().toInstant()).isEqualTo(checkedInAt.toInstant());
        assertThat(decoded.checkinId()).isEqualTo(401L);
    }

    @Test
    void 매칭_기록_cursor는_체크인_기록에서_거부한다() {
        // prefix가 다르면 다른 목록의 cursor다. 그대로 받으면 엉뚱한 위치에서 페이지가 시작된다.
        String otherList = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("match-history:v1:1780000000:0:12".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(otherList))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cursor");
    }

    @Test
    void 형식이_깨진_cursor는_400으로_거부한다() {
        assertThatThrownBy(() -> codec.decode("!!not-base64!!"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> codec.decode(payload("checkin-history:v1:1780000000:0")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> codec.decode(payload("checkin-history:v1:1780000000:0:0")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> codec.decode(payload("checkin-history:v1:1780000000:-1:5")))
                .isInstanceOf(BusinessException.class);
    }

    private static String payload(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
