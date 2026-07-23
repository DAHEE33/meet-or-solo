package com.survey.meetorsolo.domain.member.repository;

import com.survey.meetorsolo.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByProviderAndProviderUserId(String provider, String providerUserId);

    @Query(value = "SELECT * FROM members WHERE id = :memberId FOR UPDATE", nativeQuery = true)
    Optional<Member> findByIdForUpdate(@Param("memberId") long memberId);

    @Modifying
    @Query("""
            UPDATE Member member
            SET member.penaltyScore = member.penaltyScore + :scoreDelta
            WHERE member.id = :memberId
            """)
    int increasePenaltyScore(
            @Param("memberId") long memberId,
            @Param("scoreDelta") int scoreDelta
    );
}
