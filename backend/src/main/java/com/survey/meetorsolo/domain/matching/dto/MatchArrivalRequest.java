package com.survey.meetorsolo.domain.matching.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 도착 인증에 쓰는 좌표다({@code docs/19} 4.11.3).
 *
 * <p>체크인의 {@code CheckInRequest}와 같은 규칙이다. 브라우저 Geolocation이 준 좌표를 그대로
 * 보내고, 서버는 만남 장소와의 <b>거리 계산에만 쓴다.</b> 원본 좌표는 응답에도 DB에도 남기지
 * 않고 {@code match_group_members.arrival_distance_meters}에 거리만 저장한다.
 *
 * <p>정확도({@code accuracyMeters})는 받지 않는다. 체크인은 축제 도착 자체를 판정하는 첫 관문이라
 * 정확도가 낮은 좌표를 거르지만, 도착은 이미 체크인을 통과한 사람이 같은 축제 안에서 누르는
 * 것이라 관문을 두 번 세울 이유가 없다.
 */
public record MatchArrivalRequest(
        @NotNull(message = "latitude는 필수입니다.")
        @DecimalMin(value = "-90", message = "latitude는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90", message = "latitude는 90 이하여야 합니다.")
        BigDecimal latitude,

        @NotNull(message = "longitude는 필수입니다.")
        @DecimalMin(value = "-180", message = "longitude는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180", message = "longitude는 180 이하여야 합니다.")
        BigDecimal longitude
) {
}
