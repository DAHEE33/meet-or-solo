package com.survey.meetorsolo.domain.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.inquiry.entity.Inquiry;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessage;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessageAuthorType;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import com.survey.meetorsolo.domain.inquiry.repository.InquiryMessageRepository;
import com.survey.meetorsolo.domain.inquiry.repository.InquiryRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InquiryRetentionServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 9, 9, 3, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private InquiryRepository inquiries;
    @Mock
    private InquiryMessageRepository messages;

    private InquiryRetentionService service;

    @BeforeEach
    void setUp() {
        service = new InquiryRetentionService(
                inquiries, messages, Clock.fixed(NOW.toInstant(), ZoneOffset.ofHours(9)));
    }

    @Test
    void 보관_기간_기준은_현재_시각에서_1년_전이다() {
        when(inquiries.findAnonymizationTargets(any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of());

        service.anonymizeBatch(100);

        ArgumentCaptor<OffsetDateTime> threshold = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(inquiries).findAnonymizationTargets(threshold.capture(), anyInt());
        assertThat(threshold.getValue()).isEqualTo(NOW.minusYears(1));
    }

    @Test
    void 대상이_없으면_아무것도_읽지_않는다() {
        when(inquiries.findAnonymizationTargets(any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of());

        assertThat(service.anonymizeBatch(100)).isZero();
        verify(messages, never()).findByInquiryIdIn(anyCollection());
    }

    @Test
    void 대상의_제목과_본문을_고정_문구로_덮고_익명화_시각을_남긴다() {
        Inquiry target = closedInquiry(1L, NOW.minusYears(2));
        InquiryMessage message = message(1L, "제 전화번호는 010-0000-0000입니다");
        when(inquiries.findAnonymizationTargets(any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of(target));
        when(messages.findByInquiryIdIn(anyCollection())).thenReturn(List.of(message));

        assertThat(service.anonymizeBatch(100)).isEqualTo(1);

        assertThat(target.getTitle()).isEqualTo(InquiryRetentionService.ANONYMIZED_TITLE);
        assertThat(target.getAnonymizedAt()).isEqualTo(NOW);
        assertThat(message.getBody()).isEqualTo(InquiryRetentionService.ANONYMIZED_BODY);
        // 통계·감사용으로 남기는 값은 지우지 않는다.
        assertThat(target.getCategory()).isEqualTo(InquiryCategory.SANCTION_APPEAL);
        assertThat(target.getStatus()).isEqualTo(InquiryStatus.CLOSED);
    }

    @Test
    void 이미_익명화한_문의는_조회_조건에서_빠지므로_다시_처리되지_않는다() {
        // 재처리를 막는 것은 본문 문구 비교가 아니라 anonymized_at이다(docs/29 5.6).
        Inquiry alreadyDone = closedInquiry(1L, NOW.minusYears(2));
        alreadyDone.anonymize(InquiryRetentionService.ANONYMIZED_TITLE, NOW.minusDays(3));
        assertThat(alreadyDone.getAnonymizedAt()).isEqualTo(NOW.minusDays(3));

        when(inquiries.findAnonymizationTargets(any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of());

        assertThat(service.anonymizeBatch(100)).isZero();
        assertThat(alreadyDone.getAnonymizedAt()).isEqualTo(NOW.minusDays(3));
    }

    private static Inquiry closedInquiry(long id, OffsetDateTime closedAt) {
        Inquiry inquiry = Inquiry.create(
                MEMBER_ID, InquiryCategory.SANCTION_APPEAL, "이용정지 사유 문의", closedAt.minusDays(1));
        ReflectionTestUtils.setField(inquiry, "id", id);
        ReflectionTestUtils.setField(inquiry, "status", InquiryStatus.CLOSED);
        ReflectionTestUtils.setField(inquiry, "closedAt", closedAt);
        ReflectionTestUtils.setField(inquiry, "createdAt", closedAt.minusDays(1));
        ReflectionTestUtils.setField(inquiry, "updatedAt", closedAt);
        return inquiry;
    }

    private static InquiryMessage message(long inquiryId, String body) {
        InquiryMessage message = InquiryMessage.create(
                inquiryId, InquiryMessageAuthorType.USER, MEMBER_ID, body);
        ReflectionTestUtils.setField(message, "id", 100L);
        ReflectionTestUtils.setField(message, "createdAt", NOW.minusYears(2));
        return message;
    }
}
