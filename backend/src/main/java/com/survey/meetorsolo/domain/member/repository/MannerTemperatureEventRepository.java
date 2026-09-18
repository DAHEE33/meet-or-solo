package com.survey.meetorsolo.domain.member.repository;

import com.survey.meetorsolo.domain.member.entity.MannerTemperatureEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MannerTemperatureEventRepository extends JpaRepository<MannerTemperatureEvent, Long> {

    /**
     * 만남 완료 보상을 이미 받았는지.
     *
     * <p>DB의 {@code uq_manner_temperature_events_match_completed}가 최종 방어선이고 이 조회는
     * 정상 경로에서 예외를 던지지 않으려는 것이다. 완료 API는 반복 호출되는 것이 정상 흐름이라
     * (마지막 도착자 외의 회원이 새로고침) 매번 제약 위반이 나면 로그가 오염된다.
     */
    boolean existsByMemberIdAndEventTypeAndRelatedGroupId(
            long memberId, String eventType, Long relatedGroupId);
}
