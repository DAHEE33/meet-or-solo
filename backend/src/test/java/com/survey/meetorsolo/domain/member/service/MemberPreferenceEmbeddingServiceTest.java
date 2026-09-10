package com.survey.meetorsolo.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.member.dto.MemberPreferenceEmbeddingResponse;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberPreferenceEmbedding;
import com.survey.meetorsolo.domain.member.repository.MemberConsentQueryRepository;
import com.survey.meetorsolo.domain.member.repository.MemberPreferenceEmbeddingRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.external.openai.EmbeddingFailedException;
import com.survey.meetorsolo.external.openai.EmbeddingFailureReason;
import com.survey.meetorsolo.external.openai.OpenAiEmbeddingClient;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberPreferenceEmbeddingServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MemberPreferenceEmbeddingRepository embeddingRepository;
    @Mock
    private MemberConsentQueryRepository consentRepository;
    @Mock
    private OpenAiEmbeddingClient openAiEmbeddingClient;

    private MemberPreferenceEmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new MemberPreferenceEmbeddingService(
                memberRepository, embeddingRepository, consentRepository, openAiEmbeddingClient);
    }

    @Test
    void 새_임베딩을_생성하고_COMPLETED_상태를_반환한다() {
        Member member = Member.createKakaoMember("provider-id", "닉네임", null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(consentRepository.hasAgreedConsent(1L, "AI_PROCESSING")).thenReturn(true);
        when(consentRepository.hasAgreedConsent(1L, "OVERSEAS_TRANSFER")).thenReturn(true);
        when(embeddingRepository.findByMemberId(1L)).thenReturn(Optional.empty());
        when(embeddingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(openAiEmbeddingClient.embed("축제에서 맛집 탐방")).thenReturn(new float[1536]);
        when(openAiEmbeddingClient.getModel()).thenReturn("text-embedding-3-small");

        MemberPreferenceEmbeddingResponse response = service.createOrUpdate(1L, "축제에서 맛집 탐방");

        assertThat(response.embeddingStatus()).isEqualTo("COMPLETED");
        assertThat(response.embeddingModel()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void 기존_임베딩을_갱신한다() {
        Member member = Member.createKakaoMember("provider-id", "닉네임", null);
        MemberPreferenceEmbedding existing = MemberPreferenceEmbedding.create(member, "이전 텍스트");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(consentRepository.hasAgreedConsent(1L, "AI_PROCESSING")).thenReturn(true);
        when(consentRepository.hasAgreedConsent(1L, "OVERSEAS_TRANSFER")).thenReturn(true);
        when(embeddingRepository.findByMemberId(1L)).thenReturn(Optional.of(existing));
        when(embeddingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(openAiEmbeddingClient.embed("새 텍스트")).thenReturn(new float[1536]);
        when(openAiEmbeddingClient.getModel()).thenReturn("text-embedding-3-small");

        MemberPreferenceEmbeddingResponse response = service.createOrUpdate(1L, "새 텍스트");

        assertThat(response.preferenceText()).isEqualTo("새 텍스트");
        assertThat(response.embeddingStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void AI_처리_동의가_없으면_AI_CONSENT_REQUIRED를_던진다() {
        Member member = Member.createKakaoMember("provider-id", "닉네임", null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(consentRepository.hasAgreedConsent(1L, "AI_PROCESSING")).thenReturn(false);

        assertThatThrownBy(() -> service.createOrUpdate(1L, "텍스트"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AI_CONSENT_REQUIRED);

        verify(openAiEmbeddingClient, never()).embed(anyString());
    }

    @Test
    void 국외_이전_동의만_없어도_외부로_전송하지_않는다() {
        // AI 처리와 국외 이전은 법적 성격이 달라 하나로 합치지 않는다. 하나라도 없으면 전송 금지.
        Member member = Member.createKakaoMember("provider-id", "닉네임", null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(consentRepository.hasAgreedConsent(1L, "AI_PROCESSING")).thenReturn(true);
        when(consentRepository.hasAgreedConsent(1L, "OVERSEAS_TRANSFER")).thenReturn(false);

        assertThatThrownBy(() -> service.createOrUpdate(1L, "텍스트"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AI_CONSENT_REQUIRED);

        verify(openAiEmbeddingClient, never()).embed(anyString());
        verify(embeddingRepository, never()).save(any());
    }

    @Test
    void OpenAI_실패_시_FAILED_상태를_반환한다() {
        Member member = Member.createKakaoMember("provider-id", "닉네임", null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(consentRepository.hasAgreedConsent(1L, "AI_PROCESSING")).thenReturn(true);
        when(consentRepository.hasAgreedConsent(1L, "OVERSEAS_TRANSFER")).thenReturn(true);
        when(embeddingRepository.findByMemberId(1L)).thenReturn(Optional.empty());
        when(embeddingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(openAiEmbeddingClient.embed("텍스트"))
                .thenThrow(new BusinessException(ErrorCode.EMBEDDING_API_FAILED));

        MemberPreferenceEmbeddingResponse response = service.createOrUpdate(1L, "텍스트");

        assertThat(response.embeddingStatus()).isEqualTo("FAILED");
    }

    @Test
    void 실패_이유를_함께_기록한다() {
        // 상태만 FAILED로 남기면 키 문제인지 아웃바운드 차단인지 DB만 보고는 구분할 수 없다.
        MemberPreferenceEmbedding saved = failingSave(
                new EmbeddingFailedException(EmbeddingFailureReason.UNAUTHORIZED));

        assertThat(saved.getEmbeddingStatus()).isEqualTo("FAILED");
        assertThat(saved.getEmbeddingErrorReason()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void BusinessException이_아닌_예외에도_취향_저장을_되돌리지_않는다() {
        // 예전에는 BusinessException만 잡아서, 그 밖의 예외가 나면 트랜잭션이 통째로 롤백돼
        // 방금 저장한 취향 행 자체가 사라졌다. 회원 화면에서는 "입력한 적 없음"으로 되돌아간다.
        MemberPreferenceEmbedding saved = failingSave(new IllegalArgumentException("헤더 값이 올바르지 않습니다"));

        assertThat(saved.getEmbeddingStatus()).isEqualTo("FAILED");
        assertThat(saved.getEmbeddingErrorReason()).isEqualTo("UNKNOWN");
        assertThat(saved.getPreferenceText()).isEqualTo("텍스트");
    }

    /** 임베딩 호출이 주어진 예외로 실패했을 때 저장된 엔티티를 돌려준다. */
    private MemberPreferenceEmbedding failingSave(RuntimeException failure) {
        Member member = Member.createKakaoMember("provider-id", "닉네임", null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(consentRepository.hasAgreedConsent(1L, "AI_PROCESSING")).thenReturn(true);
        when(consentRepository.hasAgreedConsent(1L, "OVERSEAS_TRANSFER")).thenReturn(true);
        when(embeddingRepository.findByMemberId(1L)).thenReturn(Optional.empty());
        when(embeddingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(openAiEmbeddingClient.embed("텍스트")).thenThrow(failure);

        MemberPreferenceEmbeddingResponse response = service.createOrUpdate(1L, "텍스트");
        assertThat(response.embeddingStatus()).isEqualTo("FAILED");

        ArgumentCaptor<MemberPreferenceEmbedding> captor =
                ArgumentCaptor.forClass(MemberPreferenceEmbedding.class);
        verify(embeddingRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 존재하지_않는_회원은_NOT_FOUND를_던진다() {
        when(memberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrUpdate(999L, "텍스트"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 임베딩_조회_시_없으면_예외_대신_null을_반환한다() {
        // "아직 입력하지 않음"은 오류가 아니라 정상 상태이므로 200과 null data로 응답한다.
        when(embeddingRepository.findByMemberId(1L)).thenReturn(Optional.empty());

        assertThat(service.getByMemberId(1L)).isNull();
    }

    @Test
    void 삭제는_존재_여부와_무관하게_성공한다() {
        service.delete(1L);
        verify(embeddingRepository).deleteByMemberId(1L);
    }
}
