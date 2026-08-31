package com.survey.meetorsolo.domain.member.repository;

import com.survey.meetorsolo.domain.member.entity.MemberPreferenceEmbedding;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberPreferenceEmbeddingRepository
        extends JpaRepository<MemberPreferenceEmbedding, Long> {

    Optional<MemberPreferenceEmbedding> findByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);

    /** 지정 회원들 중 embeddingStatus가 일치하는 임베딩을 일괄 조회한다. */
    List<MemberPreferenceEmbedding> findAllByMemberIdInAndEmbeddingStatus(
            Collection<Long> memberIds, String embeddingStatus);
}
