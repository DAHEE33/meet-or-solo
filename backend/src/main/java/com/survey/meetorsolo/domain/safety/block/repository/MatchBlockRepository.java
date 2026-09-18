package com.survey.meetorsolo.domain.safety.block.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MatchBlockRepository {

    private final JdbcTemplate jdbc;

    public MatchBlockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<GroupSnapshot> findGroupForShare(long groupId) {
        return jdbc.query("""
                SELECT id, status, completed_at, cancelled_at
                FROM match_groups
                WHERE id = ?
                FOR SHARE
                """, GROUP_MAPPER, groupId).stream().findFirst();
    }

    public boolean existsParticipant(long groupId, long memberId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM match_group_members
                    WHERE group_id = ? AND member_id = ?
                )
                """, Boolean.class, groupId, memberId);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<BlockSnapshot> insertIfAbsent(
            long blockerMemberId,
            long blockedMemberId,
            String reason,
            OffsetDateTime now
    ) {
        List<BlockSnapshot> inserted = jdbc.query("""
                INSERT INTO user_blocks(blocker_member_id, blocked_member_id, reason, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (blocker_member_id, blocked_member_id) DO NOTHING
                RETURNING id, blocked_member_id, created_at
                """, BLOCK_MAPPER, blockerMemberId, blockedMemberId, reason, now);
        return inserted.stream().findFirst();
    }

    public Optional<BlockSnapshot> findExisting(long blockerMemberId, long blockedMemberId) {
        return jdbc.query("""
                SELECT id, blocked_member_id, created_at
                FROM user_blocks
                WHERE blocker_member_id = ? AND blocked_member_id = ?
                """, BLOCK_MAPPER, blockerMemberId, blockedMemberId).stream().findFirst();
    }

    private static final RowMapper<GroupSnapshot> GROUP_MAPPER = (rs, rowNum) -> new GroupSnapshot(
            rs.getString("status"),
            rs.getObject("completed_at", OffsetDateTime.class),
            rs.getObject("cancelled_at", OffsetDateTime.class)
    );

    private static final RowMapper<BlockSnapshot> BLOCK_MAPPER = (rs, rowNum) -> new BlockSnapshot(
            rs.getLong("id"),
            rs.getLong("blocked_member_id"),
            rs.getObject("created_at", OffsetDateTime.class)
    );

    public record GroupSnapshot(
            String status,
            OffsetDateTime completedAt,
            OffsetDateTime cancelledAt
    ) {
    }

    public record BlockSnapshot(long blockId, long blockedMemberId, OffsetDateTime createdAt) {
    }
}
