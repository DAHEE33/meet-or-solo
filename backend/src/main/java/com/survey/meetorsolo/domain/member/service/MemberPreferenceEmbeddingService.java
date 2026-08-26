package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.dto.MemberPreferenceEmbeddingResponse;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberConsentType;
import com.survey.meetorsolo.domain.member.entity.MemberPreferenceEmbedding;
import com.survey.meetorsolo.domain.member.repository.MemberConsentQueryRepository;
import com.survey.meetorsolo.domain.member.repository.MemberPreferenceEmbeddingRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.external.openai.OpenAiEmbeddingClient;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberPreferenceEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(MemberPreferenceEmbeddingService.class);
    private final MemberRepository memberRepository;
    private final MemberPreferenceEmbeddingRepository embeddingRepository;
    private final MemberConsentQueryRepository consentRepository;
    private final OpenAiEmbeddingClient openAiEmbeddingClient;

    public MemberPreferenceEmbeddingService(
            MemberRepository memberRepository,
            MemberPreferenceEmbeddingRepository embeddingRepository,
            MemberConsentQueryRepository consentRepository,
            OpenAiEmbeddingClient openAiEmbeddingClient
    ) {
        this.memberRepository = memberRepository;
        this.embeddingRepository = embeddingRepository;
        this.consentRepository = consentRepository;
        this.openAiEmbeddingClient = openAiEmbeddingClient;
    }

    @Transactional
    public MemberPreferenceEmbeddingResponse createOrUpdate(Long memberId, String preferenceText) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        requireEmbeddingConsents(memberId);

        MemberPreferenceEmbedding embedding = embeddingRepository.findByMemberId(memberId)
                .map(existing -> {
                    existing.updatePreferenceText(preferenceText);
                    return existing;
                })
                .orElseGet(() -> MemberPreferenceEmbedding.create(member, preferenceText));

        embeddingRepository.save(embedding);

        try {
            float[] vector = openAiEmbeddingClient.embed(preferenceText);
            embedding.markCompleted(vector, openAiEmbeddingClient.getModel());
        } catch (BusinessException exception) {
            log.warn("임베딩 생성 실패. memberId={}, error={}", memberId, exception.getMessage());
            embedding.markFailed();
        }

        return MemberPreferenceEmbeddingResponse.from(embedding);
    }

    /**
     * 취향 글을 외부 임베딩 API로 보내는 데 필요한 동의를 모두 보유했는지 확인한다.
     *
     * <p>AI 처리 동의와 국외 이전 동의는 법적 성격과 거부 선택이 다르므로 하나로 합치지 않고
     * 각각 확인한다. 하나라도 없으면 외부 전송을 하지 않는다.
     */
    private void requireEmbeddingConsents(Long memberId) {
        for (MemberConsentType type : MemberConsentType.AI_EMBEDDING_REQUIRED) {
            if (!consentRepository.hasAgreedConsent(memberId, type.name())) {
                throw new BusinessException(ErrorCode.AI_CONSENT_REQUIRED);
            }
        }
    }

    /**
     * 저장된 취향이 없으면 null을 반환한다.
     *
     * <p>"아직 입력하지 않음"은 오류가 아니라 정상 상태이므로 404가 아니라 200과 null data로
     * 응답한다. 진행 중인 pool·proposal·cooldown 조회와 같은 규약이다.
     */
    @Transactional(readOnly = true)
    public MemberPreferenceEmbeddingResponse getByMemberId(Long memberId) {
        return embeddingRepository.findByMemberId(memberId)
                .map(MemberPreferenceEmbeddingResponse::from)
                .orElse(null);
    }

    @Transactional
    public void delete(Long memberId) {
        embeddingRepository.deleteByMemberId(memberId);
    }
}
