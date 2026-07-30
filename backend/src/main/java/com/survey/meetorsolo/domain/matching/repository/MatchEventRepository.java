package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchEventRepository extends JpaRepository<MatchEvent, Long> {

    @Query(value = """
            SELECT
                event.id AS eventId,
                event.event_type AS eventType,
                event.payload::text AS payload,
                event.created_at AS createdAt,
                visible_member.id AS actorMemberId,
                visible_member.nickname AS actorNickname
            FROM match_events event
            LEFT JOIN match_group_members active_group_member
              ON active_group_member.group_id = event.group_id
             AND active_group_member.member_id = event.member_id
             AND active_group_member.status IN ('JOINED', 'ARRIVAL_TIME_SELECTED', 'ARRIVED')
            LEFT JOIN members visible_member
              ON visible_member.id = active_group_member.member_id
            WHERE event.group_id = :groupId
              AND event.event_type IN ('MATCH_CONFIRMED', 'ARRIVAL_TIME_SELECTED', 'MEMBER_ARRIVED')
            ORDER BY event.created_at DESC, event.id DESC
            LIMIT 50
            """, nativeQuery = true)
    List<CurrentGroupEventProjection> findLatestCurrentGroupEvents(
            @Param("groupId") long groupId
    );

    interface CurrentGroupEventProjection {
        Long getEventId();
        String getEventType();
        String getPayload();
        Instant getCreatedAt();
        Long getActorMemberId();
        String getActorNickname();
    }
}
