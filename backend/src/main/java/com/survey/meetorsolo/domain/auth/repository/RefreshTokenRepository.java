package com.survey.meetorsolo.domain.auth.repository;

import com.survey.meetorsolo.domain.auth.entity.RefreshToken;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByMemberId(Long memberId);

    @Modifying
    @Query("""
            UPDATE RefreshToken token
            SET token.revokedAt = :revokedAt
            WHERE token.member.id = :memberId AND token.revokedAt IS NULL
            """)
    int revokeByMemberId(
            @Param("memberId") long memberId,
            @Param("revokedAt") OffsetDateTime revokedAt
    );
}
