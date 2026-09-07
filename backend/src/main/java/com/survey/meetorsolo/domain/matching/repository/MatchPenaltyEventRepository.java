package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchPenaltyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchPenaltyEventRepository extends JpaRepository<MatchPenaltyEvent, Long> {

    boolean existsByRelatedProposalId(long relatedProposalId);

    boolean existsByRelatedPoolId(long relatedPoolId);

    boolean existsByRelatedReportId(long relatedReportId);

    boolean existsByRelatedGroupIdAndMemberIdAndEventType(
            long relatedGroupId, long memberId, String eventType);

    @Query(value = """
            SELECT COUNT(*) FROM match_penalty_events
            WHERE member_id = :memberId
              AND event_type = :eventType
              AND created_at >= :dayStart
              AND created_at < :dayEnd
            """, nativeQuery = true)
    long countDaily(
            @Param("memberId") long memberId,
            @Param("eventType") String eventType,
            @Param("dayStart") OffsetDateTime dayStart,
            @Param("dayEnd") OffsetDateTime dayEnd
    );
}
