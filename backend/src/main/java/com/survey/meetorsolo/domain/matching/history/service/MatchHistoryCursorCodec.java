package com.survey.meetorsolo.domain.matching.history.service;

import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 매칭 기록 목록 cursor codec이다.
 *
 * <p>관리자 목록 codec들과 달리 HMAC 서명을 붙이지 않는다. 조회가 항상 JWT의 회원으로 고정돼
 * 있고 filter도 없어서, cursor를 위조해도 자기 목록 안에서 시작 위치만 바뀔 뿐 다른 회원의
 * 데이터에 닿지 않기 때문이다. 관리자 cursor secret을 회원 API가 공유하는 것도 부적절하다.
 */
@Component
public class MatchHistoryCursorCodec {

    private static final String PAYLOAD_PREFIX = "match-history:v1:";

    public String encode(OffsetDateTime endedAt, long groupId) {
        String payload = PAYLOAD_PREFIX + endedAt.toInstant().getEpochSecond() + ":"
                + endedAt.getNano() + ":" + groupId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String value) {
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            if (!payload.startsWith(PAYLOAD_PREFIX)) {
                throw invalidCursor();
            }
            String[] values = payload.substring(PAYLOAD_PREFIX.length()).split(":", -1);
            if (values.length != 3) {
                throw invalidCursor();
            }
            long epochSecond = Long.parseLong(values[0]);
            int nano = Integer.parseInt(values[1]);
            long groupId = Long.parseLong(values[2]);
            if (nano < 0 || nano > 999_999_999 || groupId <= 0) {
                throw invalidCursor();
            }
            return new Cursor(
                    OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond, nano), ZoneOffset.UTC),
                    groupId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidCursor();
        }
    }

    private BusinessException invalidCursor() {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "cursor 값이 올바르지 않습니다.");
    }

    public record Cursor(OffsetDateTime endedAt, long groupId) {
    }
}
