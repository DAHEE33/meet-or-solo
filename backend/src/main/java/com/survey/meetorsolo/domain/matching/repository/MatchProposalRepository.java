package com.survey.meetorsolo.domain.matching.repository;

import com.survey.meetorsolo.domain.matching.entity.MatchProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MatchProposalRepository extends JpaRepository<MatchProposal, Long> {
    Optional<MatchProposal> findByIdAndMemberId(long id, long memberId);

    @Query(value = """
            SELECT * FROM match_proposals
            WHERE member_id = :memberId
              AND status = 'SENT'
              AND expires_at > :now
            ORDER BY proposal_round DESC, id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<MatchProposal> findActiveForMember(
            @Param("memberId") long memberId,
            @Param("now") java.time.OffsetDateTime now
    );

    @Query(value="SELECT * FROM match_proposals WHERE id=:proposalId FOR UPDATE", nativeQuery=true)
    Optional<MatchProposal> findByIdForUpdate(@Param("proposalId") long proposalId);
    List<MatchProposal> findAllByAttemptIdOrderByIdAsc(long attemptId);
    boolean existsByAttemptIdAndProposalRound(long attemptId, int proposalRound);
    Optional<MatchProposal> findFirstByAttemptIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
            long attemptId, String status, java.time.OffsetDateTime now);

    /**
     * 탈퇴 시 응답 대기 중인 제안을 만료시킨다. 미응답 timeout 경로를 타지 않게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE match_proposals
               SET status = 'EXPIRED', updated_at = :now
             WHERE member_id = :memberId
               AND status = 'SENT'
            """, nativeQuery = true)
    int expireSentProposalsOnWithdrawal(
            @Param("memberId") long memberId,
            @Param("now") java.time.OffsetDateTime now
    );
}
