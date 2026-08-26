package com.survey.meetorsolo.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.member.dto.MemberConsentResponse;
import com.survey.meetorsolo.domain.member.dto.MemberConsentsResponse;
import com.survey.meetorsolo.domain.member.entity.MemberConsentType;
import com.survey.meetorsolo.domain.member.repository.MemberConsentCommandRepository;
import com.survey.meetorsolo.domain.member.repository.MemberConsentQueryRepository;
import com.survey.meetorsolo.domain.member.repository.MemberPreferenceEmbeddingRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberConsentServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MemberConsentQueryRepository consentQueryRepository;
    @Mock
    private MemberConsentCommandRepository consentCommandRepository;
    @Mock
    private MemberPreferenceEmbeddingRepository embeddingRepository;

    private MemberConsentService service;

    @BeforeEach
    void setUp() {
        service = new MemberConsentService(
                memberRepository,
                consentQueryRepository,
                consentCommandRepository,
                embeddingRepository);
    }

    @Test
    void 동의를_기록하면_현재_고지_버전으로_저장한다() {
        when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);

        MemberConsentResponse response =
                service.agree(MEMBER_ID, MemberConsentType.AI_PROCESSING);

        assertThat(response.consentType()).isEqualTo("AI_PROCESSING");
        assertThat(response.agreed()).isTrue();
        assertThat(response.version()).isEqualTo(MemberConsentType.AI_PROCESSING.currentVersion());
        assertThat(response.agreedAt()).isNotNull();
        verify(consentCommandRepository).agree(
                eq(MEMBER_ID), eq(MemberConsentType.AI_PROCESSING), any(OffsetDateTime.class));
    }

    @Test
    void 존재하지_않는_회원의_동의는_거절한다() {
        when(memberRepository.existsById(MEMBER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.agree(MEMBER_ID, MemberConsentType.TERMS))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(consentCommandRepository, never()).agree(any(), any(), any());
    }

    @Test
    void AI_처리_동의를_철회하면_저장된_취향을_함께_삭제한다() {
        when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);
        when(consentCommandRepository.revoke(
                eq(MEMBER_ID), eq(MemberConsentType.AI_PROCESSING), any(OffsetDateTime.class)))
                .thenReturn(true);

        MemberConsentResponse response =
                service.revoke(MEMBER_ID, MemberConsentType.AI_PROCESSING);

        assertThat(response.agreed()).isFalse();
        assertThat(response.revokedAt()).isNotNull();
        verify(embeddingRepository).deleteByMemberId(MEMBER_ID);
    }

    @Test
    void 국외_이전_동의만_철회해도_저장된_취향을_삭제한다() {
        when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);
        when(consentCommandRepository.revoke(
                eq(MEMBER_ID), eq(MemberConsentType.OVERSEAS_TRANSFER), any(OffsetDateTime.class)))
                .thenReturn(true);

        service.revoke(MEMBER_ID, MemberConsentType.OVERSEAS_TRANSFER);

        verify(embeddingRepository).deleteByMemberId(MEMBER_ID);
    }

    @Test
    void 약관_동의_철회는_취향을_삭제하지_않는다() {
        when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);
        when(consentCommandRepository.revoke(
                eq(MEMBER_ID), eq(MemberConsentType.TERMS), any(OffsetDateTime.class)))
                .thenReturn(true);

        service.revoke(MEMBER_ID, MemberConsentType.TERMS);

        verify(embeddingRepository, never()).deleteByMemberId(any());
    }

    @Test
    void 철회할_동의가_없어도_오류로_보지_않고_취향은_삭제한다() {
        when(memberRepository.existsById(MEMBER_ID)).thenReturn(true);
        when(consentCommandRepository.revoke(
                eq(MEMBER_ID), eq(MemberConsentType.AI_PROCESSING), any(OffsetDateTime.class)))
                .thenReturn(false);

        MemberConsentResponse response =
                service.revoke(MEMBER_ID, MemberConsentType.AI_PROCESSING);

        assertThat(response.agreed()).isFalse();
        assertThat(response.revokedAt()).isNull();
        verify(embeddingRepository).deleteByMemberId(MEMBER_ID);
    }

    @Test
    void 기록이_없는_동의도_항목으로_채워서_반환한다() {
        when(consentQueryRepository.findLatestByMemberIdAndTypes(eq(MEMBER_ID), anyCollection()))
                .thenReturn(List.of());

        MemberConsentsResponse response = service.getAiConsents(MEMBER_ID);

        assertThat(response.consents()).hasSize(2);
        assertThat(response.consents())
                .extracting(MemberConsentResponse::consentType)
                .containsExactly("AI_PROCESSING", "OVERSEAS_TRANSFER");
        assertThat(response.consents()).allMatch(consent -> !consent.agreed());
    }

    @Test
    void 일부만_동의한_상태를_그대로_반환한다() {
        OffsetDateTime agreedAt = OffsetDateTime.now();
        when(consentQueryRepository.findLatestByMemberIdAndTypes(eq(MEMBER_ID), anyCollection()))
                .thenReturn(List.of(
                        new MemberConsentResponse("AI_PROCESSING", true, "1.0", agreedAt, null)));

        MemberConsentsResponse response = service.getAiConsents(MEMBER_ID);

        assertThat(response.consents().get(0).agreed()).isTrue();
        assertThat(response.consents().get(0).agreedAt()).isEqualTo(agreedAt);
        assertThat(response.consents().get(1).consentType()).isEqualTo("OVERSEAS_TRANSFER");
        assertThat(response.consents().get(1).agreed()).isFalse();
    }

    @Test
    void 아직_API로_다루지_않는_동의_유형은_거절한다() {
        assertThatThrownBy(() -> MemberConsentType.from("MARKETING"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 알_수_없는_동의_유형은_거절한다() {
        assertThatThrownBy(() -> MemberConsentType.from("UNKNOWN"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
