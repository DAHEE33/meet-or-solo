package com.survey.meetorsolo.domain.matching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 만남 장소 도착 인정 조건이다({@code docs/19} 4.11.3).
 *
 * <p>체크인({@code app.festival.checkin})과 같은 구조다. 도착도 GPS로 확인해야 "실제로 그 자리에
 * 있었다"가 성립하는데, 예전에는 서버가 좌표를 아예 받지 않고 버튼만 누르면 도착으로 인정했다.
 * 화면에 반경 안내가 떠 있었을 뿐 검증이 없었다.
 *
 * @param radiusMeters      만남 장소 핀에서 이 거리 안이면 도착으로 인정한다.
 * @param bypassRadiusCheck 환경 전체의 반경 검증을 끈다. <b>어느 환경에서도 기본은
 *                          {@code false}다.</b> 환경 전체를 끄면 그 환경에서 검증이 실제로
 *                          동작하는지 확인할 방법이 사라진다. 현장에 가지 않고 확인해야 하면
 *                          {@code members.test_account}로 계정 단위 면제를 쓴다(체크인과 같다).
 */
@ConfigurationProperties(prefix = "app.matching.arrival")
public record MatchingArrivalProperties(
        int radiusMeters,
        boolean bypassRadiusCheck
) {

    public MatchingArrivalProperties {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("app.matching.arrival.radius-meters는 양수여야 합니다.");
        }
    }
}
