package com.survey.meetorsolo.domain.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.inquiry.dto.InquiryDetailResponse;
import com.survey.meetorsolo.domain.inquiry.dto.InquiryListResponse;
import com.survey.meetorsolo.domain.inquiry.entity.Inquiry;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessage;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessageAuthorType;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryStatus;
import com.survey.meetorsolo.domain.inquiry.repository.InquiryMessageRepository;
import com.survey.meetorsolo.domain.inquiry.repository.InquiryRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final long OTHER_MEMBER_ID = 2L;
    private static final long INQUIRY_ID = 10L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 9, 9, 12, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private InquiryRepository inquiries;
    @Mock
    private InquiryMessageRepository messages;

    private InquiryService service;

    @BeforeEach
    void setUp() {
        service = new InquiryService(
                inquiries, messages, Clock.fixed(NOW.toInstant(), ZoneOffset.ofHours(9)));
    }

    @Test
    void 문의를_등록하면_접수_상태와_사용자_발화가_함께_생성된다() {
        when(inquiries.countOpenByMemberId(MEMBER_ID)).thenReturn(0L);
        when(inquiries.save(any(Inquiry.class))).thenAnswer(invocation -> {
            Inquiry saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", INQUIRY_ID);
            return saved;
        });
        when(messages.save(any(InquiryMessage.class))).thenAnswer(invocation -> {
            InquiryMessage saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        InquiryDetailResponse response = service.create(
                MEMBER_ID, InquiryCategory.SANCTION_APPEAL, "  이용정지 사유 문의  ", "  확인 부탁드립니다  ");

        assertThat(response.inquiryId()).isEqualTo(INQUIRY_ID);
        assertThat(response.status()).isEqualTo(InquiryStatus.RECEIVED);
        // trim 후 저장한다.
        assertThat(response.title()).isEqualTo("이용정지 사유 문의");
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).authorType())
                .isEqualTo(InquiryMessageAuthorType.USER);
        assertThat(response.messages().get(0).body()).isEqualTo("확인 부탁드립니다");
    }

    @Test
    void 제목이_비어_있으면_등록을_거절한다() {
        assertThatThrownBy(() -> service.create(MEMBER_ID, InquiryCategory.ETC, "   ", "본문"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INQUIRY_INVALID_REQUEST));

        verify(inquiries, never()).save(any());
    }

    @Test
    void 답변을_기다리는_문의가_3건이면_등록을_거절한다() {
        // 도배 완화는 댓글의 N초 규칙이 아니라 미답변 누적으로 막는다(docs/29 확정 8번).
        when(inquiries.countOpenByMemberId(MEMBER_ID)).thenReturn(3L);

        assertThatThrownBy(() -> service.create(MEMBER_ID, InquiryCategory.ETC, "제목", "본문"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INQUIRY_TOO_MANY_OPEN));

        verify(inquiries, never()).save(any());
    }

    @Test
    void 남의_문의를_조회하면_FORBIDDEN이다() {
        when(inquiries.findById(INQUIRY_ID))
                .thenReturn(Optional.of(inquiry(OTHER_MEMBER_ID, InquiryStatus.RECEIVED)));

        assertThatThrownBy(() -> service.getMyInquiry(MEMBER_ID, INQUIRY_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INQUIRY_FORBIDDEN));
    }

    @Test
    void 없는_문의를_조회하면_NOT_FOUND다() {
        when(inquiries.findById(INQUIRY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyInquiry(MEMBER_ID, INQUIRY_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND));
    }

    @Test
    void 스레드를_열면_열람_시각을_갱신한다() {
        // 관리자 답변을 밀어줄 채널이 없어 열람 시각이 badge를 끄는 유일한 신호다(docs/29 2.2).
        Inquiry target = inquiry(MEMBER_ID, InquiryStatus.ANSWERED);
        target.onAdminAnswer(NOW.minusHours(1));
        assertThat(target.hasUnreadAnswer()).isTrue();

        when(inquiries.findById(INQUIRY_ID)).thenReturn(Optional.of(target));
        when(messages.findByInquiryIdOrderByIdAsc(INQUIRY_ID)).thenReturn(List.of());

        service.getMyInquiry(MEMBER_ID, INQUIRY_ID);

        assertThat(target.getMemberReadAt()).isEqualTo(NOW);
        assertThat(target.hasUnreadAnswer()).isFalse();
    }

    @Test
    void 종결된_문의에는_추가_질문을_남길_수_없다() {
        Inquiry target = inquiry(MEMBER_ID, InquiryStatus.RECEIVED);
        target.changeStatus(InquiryStatus.CLOSED, NOW.minusDays(1));
        when(inquiries.findByIdForUpdate(INQUIRY_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.addMessage(MEMBER_ID, INQUIRY_ID, "추가 질문"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INQUIRY_CLOSED));

        verify(messages, never()).save(any());
    }

    @Test
    void 남의_문의에는_추가_질문을_남길_수_없다() {
        when(inquiries.findByIdForUpdate(INQUIRY_ID))
                .thenReturn(Optional.of(inquiry(OTHER_MEMBER_ID, InquiryStatus.RECEIVED)));

        assertThatThrownBy(() -> service.addMessage(MEMBER_ID, INQUIRY_ID, "추가 질문"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INQUIRY_FORBIDDEN));

        verify(messages, never()).save(any());
    }

    @Test
    void 답변_완료_문의에_추가_질문을_남기면_확인_중으로_되돌린다() {
        // 되돌리지 않으면 관리자 미처리 목록에 다시 뜨지 않아 재질문이 묻힌다(docs/29 5.4).
        Inquiry target = inquiry(MEMBER_ID, InquiryStatus.RECEIVED);
        target.onAdminAnswer(NOW.minusHours(2));
        assertThat(target.getStatus()).isEqualTo(InquiryStatus.ANSWERED);

        when(inquiries.findByIdForUpdate(INQUIRY_ID)).thenReturn(Optional.of(target));
        when(messages.save(any(InquiryMessage.class))).thenAnswer(invocation -> {
            InquiryMessage saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            return saved;
        });
        when(messages.findByInquiryIdOrderByIdAsc(INQUIRY_ID)).thenReturn(List.of());

        InquiryDetailResponse response = service.addMessage(MEMBER_ID, INQUIRY_ID, "아직 해결이 안 됐어요");

        assertThat(response.status()).isEqualTo(InquiryStatus.IN_PROGRESS);
        assertThat(target.getLastMessageAt()).isEqualTo(NOW);
        // 자기 발화 직후에도 badge가 남으면 안 된다.
        assertThat(target.hasUnreadAnswer()).isFalse();
    }

    @Test
    void 목록은_미확인_답변_여부를_함께_내려준다() {
        Inquiry unread = inquiry(MEMBER_ID, InquiryStatus.RECEIVED);
        unread.onAdminAnswer(NOW.minusHours(3));
        Inquiry read = inquiry(MEMBER_ID, InquiryStatus.RECEIVED);

        when(inquiries.findPageByMemberId(MEMBER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(unread, read), PageRequest.of(0, 20), 2));

        InquiryListResponse response = service.getMyInquiries(MEMBER_ID, 0, 20);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).hasUnreadAnswer()).isTrue();
        assertThat(response.items().get(1).hasUnreadAnswer()).isFalse();
        assertThat(response.hasNext()).isFalse();
    }

    private static Inquiry inquiry(long memberId, InquiryStatus status) {
        Inquiry inquiry = Inquiry.create(memberId, InquiryCategory.ETC, "제목", NOW.minusDays(2));
        ReflectionTestUtils.setField(inquiry, "id", INQUIRY_ID);
        ReflectionTestUtils.setField(inquiry, "status", status);
        ReflectionTestUtils.setField(inquiry, "createdAt", NOW.minusDays(2));
        ReflectionTestUtils.setField(inquiry, "updatedAt", NOW.minusDays(2));
        return inquiry;
    }
}
