package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.dto.MemberPreferenceEmbeddingResponse;
import com.survey.meetorsolo.domain.member.entity.Member;
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
    private static final String CONSENT_AI_PROCESSING = "AI_PROCESSING";

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

        if (!consentRepository.hasAgreedConsent(memberId, CONSENT_AI_PROCESSING)) {
            throw new BusinessException(ErrorCode.AI_CONSENT_REQUIRED);
        }

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

    @Transactional(readOnly = true)
    public MemberPreferenceEmbeddingResponse getByMemberId(Long memberId) {
        MemberPreferenceEmbedding embedding = embeddingRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMBEDDING_NOT_FOUND));
        return MemberPreferenceEmbeddingResponse.from(embedding);
    }

    @Transactional
    public void delete(Long memberId) {
        embeddingRepository.deleteByMemberId(memberId);
    }
}
