package com.survey.meetorsolo.domain.safety.block.repository;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemberBlockRepository {
    private final JdbcTemplate jdbc;

    public MemberBlockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MemberBlockSnapshot> findAllByBlockerMemberId(long blockerMemberId) {
        return jdbc.query("""
                SELECT block.blocked_member_id, blocked.nickname, blocked.profile_image_url,
                       block.created_at
                FROM user_blocks block
                JOIN members blocked ON blocked.id = block.blocked_member_id
                WHERE block.blocker_member_id = ?
                ORDER BY block.created_at DESC, block.id DESC
                """, (rs, rowNum) -> new MemberBlockSnapshot(
                rs.getLong("blocked_member_id"), rs.getString("nickname"),
                rs.getString("profile_image_url"),
                rs.getObject("created_at", OffsetDateTime.class)), blockerMemberId);
    }

    public void delete(long blockerMemberId, long blockedMemberId) {
        jdbc.update("""
                DELETE FROM user_blocks
                WHERE blocker_member_id = ? AND blocked_member_id = ?
                """, blockerMemberId, blockedMemberId);
    }

    public record MemberBlockSnapshot(long blockedMemberId, String nickname,
                                      String profileImageUrl, OffsetDateTime blockedAt) {
    }
}
