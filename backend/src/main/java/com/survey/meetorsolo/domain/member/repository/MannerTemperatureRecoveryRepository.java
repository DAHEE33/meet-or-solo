package com.survey.meetorsolo.domain.member.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 시간 경과 회복 대상 조회({@code docs/19} 4.9).
 *
 * <p>"마지막 온도 변동 시각"이 두 테이블에 나뉘어 있어 JPA 파생 쿼리로 표현되지 않는다.
 * 하강은 {@code match_penalty_events}에, 상승은 {@code manner_temperature_events}에 있다.
 * 둘 중 <b>더 최근</b> 시각을 기준으로 30일을 센다 — 하강 직후에 바로 회복이 시작되면
 * 제재 효과가 사라지고, 상승 직후에 또 회복하면 주기가 무너진다.
 */
@Repository
public class MannerTemperatureRecoveryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MannerTemperatureRecoveryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 회복 대상 회원 ID를 오래 방치된 순으로 조회한다.
     *
     * <p>조건은 넷이다.
     *
     * <ol>
     *   <li>온도가 회복 상한보다 낮다</li>
     *   <li>마지막 온도 변동이 {@code threshold}보다 오래됐다. 변동 이력이 아예 없으면 가입
     *       시각을 기준으로 삼는다 — 이력 없이 온도만 낮은 경우는 관리자 수동 조정뿐이고,
     *       그것도 언젠가는 회복 대상이 되어야 한다</li>
     *   <li>매칭에 다시 들어올 수 있는 상태다. 탈퇴·삭제 회원의 온도를 올릴 이유가 없다</li>
     *   <li>관리자 계정이 아니다</li>
     * </ol>
     *
     * <p>{@code FOR UPDATE}를 걸지 않는다. 호출자가 회원별로 다시 잠그고 조건을 재확인하므로
     * 여기서 batch 전체를 잠그면 lock 보유 시간만 길어진다.
     */
    public List<Long> findRecoverableMemberIds(
            BigDecimal ceiling, OffsetDateTime threshold, int batchSize) {
        return jdbc.queryForList("""
                SELECT m.id
                FROM members m
                WHERE m.manner_temperature < :ceiling
                  AND m.status IN ('ACTIVE', 'PROFILE_REQUIRED', 'SUSPENDED')
                  AND m.role <> 'ADMIN'
                  AND COALESCE(
                          GREATEST(
                              (SELECT MAX(e.created_at) FROM manner_temperature_events e
                                WHERE e.member_id = m.id),
                              (SELECT MAX(p.created_at) FROM match_penalty_events p
                                WHERE p.member_id = m.id
                                  AND p.manner_temperature_delta IS NOT NULL
                                  AND p.manner_temperature_delta <> 0)
                          ),
                          m.created_at
                      ) <= :threshold
                ORDER BY m.manner_temperature ASC, m.id ASC
                LIMIT :batchSize
                """,
                Map.of("ceiling", ceiling, "threshold", threshold, "batchSize", batchSize),
                Long.class);
    }
}
