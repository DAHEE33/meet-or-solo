package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.dto.MemberConsentResponse;
import com.survey.meetorsolo.domain.member.dto.MemberConsentsResponse;
import com.survey.meetorsolo.domain.member.entity.MemberConsentType;
import com.survey.meetorsolo.domain.member.repository.MemberConsentCommandRepository;
import com.survey.meetorsolo.domain.member.repository.MemberConsentQueryRepository;
import com.survey.meetorsolo.domain.member.repository.MemberPreferenceEmbeddingRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.time.SeoulDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberConsentService {

    private static final Logger log = LoggerFactory.getLogger(MemberConsentService.class);

    private final MemberRepository memberRepository;
    private final MemberConsentQueryRepository consentQueryRepository;
    private final MemberConsentCommandRepository consentCommandRepository;
    private final MemberPreferenceEmbeddingRepository embeddingRepository;

    public MemberConsentService(
            MemberRepository memberRepository,
            MemberConsentQueryRepository consentQueryRepository,
            MemberConsentCommandRepository consentCommandRepository,
            MemberPreferenceEmbeddingRepository embeddingRepository
    ) {
        this.memberRepository = memberRepository;
        this.consentQueryRepository = consentQueryRepository;
        this.consentCommandRepository = consentCommandRepository;
        this.embeddingRepository = embeddingRepository;
    }

    /**
     * 취향 분석에 필요한 동의 상태를 조회한다.
     *
     * <p>기록이 없어도 항목을 빼지 않고 `agreed = false`로 채워서 반환한다. 화면이 어떤 동의가
     * 비어 있는지 알아야 하기 때문이다.
     */
    @Transactional(readOnly = true)
    public MemberConsentsResponse getAiConsents(Long memberId) {
        return new MemberConsentsResponse(
                findStatuses(memberId, MemberConsentType.AI_EMBEDDING_REQUIRED));
    }

    @Transactional
    public MemberConsentResponse agree(Long memberId, MemberConsentType type) {
        requireMember(memberId);
        OffsetDateTime now = SeoulDateTime.now();
        consentCommandRepository.agree(memberId, type, now);
        return new MemberConsentResponse(type.name(), true, type.currentVersion(), now, null);
    }

    /**
     * 동의를 철회한다.
     *
     * <p>취향 분석에 필요한 동의를 철회하면 같은 transaction에서 저장된 취향 글과 임베딩을
     * 삭제한다. 두 동의가 모두 있어야 외부 전송이 허용되므로 하나만 철회해도 보관 근거가
     * 사라진다. 원문과 벡터는 같은 row라 한 번의 삭제로 함께 지워진다.
     *
     * <p>철회할 동의가 없어도 오류로 보지 않는다. 결과 상태가 "동의 없음"으로 같기 때문이다.
     */
    @Transactional
    public MemberConsentResponse revoke(Long memberId, MemberConsentType type) {
        requireMember(memberId);
        OffsetDateTime now = SeoulDateTime.now();
        boolean revoked = consentCommandRepository.revoke(memberId, type, now);

        if (MemberConsentType.AI_EMBEDDING_REQUIRED.contains(type)) {
            embeddingRepository.deleteByMemberId(memberId);
            log.info("취향 분석 동의 철회로 저장된 취향을 삭제했다. memberId={}, consentType={}",
                    memberId, type);
        }

        return new MemberConsentResponse(
                type.name(),
                false,
                type.currentVersion(),
                null,
                revoked ? now : null);
    }

    private List<MemberConsentResponse> findStatuses(
            Long memberId, Collection<MemberConsentType> types) {
        Map<String, MemberConsentResponse> stored =
                consentQueryRepository.findLatestByMemberIdAndTypes(memberId, types).stream()
                        .collect(Collectors.toMap(
                                MemberConsentResponse::consentType, Function.identity()));

        return types.stream()
                .map(type -> stored.getOrDefault(
                        type.name(), MemberConsentResponse.notAgreed(type)))
                .toList();
    }

    private void requireMember(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
