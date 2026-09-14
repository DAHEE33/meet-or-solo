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

    /**
     * 테스트 계정인지. 체크인이 GPS 반경 검증을 면제할지 판단하는 데만 쓴다.
     *
     * <p>회원 전체를 읽지 않고 존재 여부만 조회한다. 체크인은 요청마다 지나가는 경로이고
     * 필요한 값은 boolean 하나뿐이다. 탈퇴 회원은 {@code withdraw()}가 표시를 지우므로
     * 상태 조건을 따로 걸지 않아도 면제 대상이 되지 않는다.
     */
    boolean existsByIdAndTestAccountIsTrue(long memberId);

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
