package com.survey.meetorsolo.domain.inquiry.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryDetailResponse;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryMemberSummaryResponse;
import com.survey.meetorsolo.domain.inquiry.admin.dto.AdminInquiryTargetStatus;
import com.survey.meetorsolo.domain.inquiry.admin.repository.AdminInquiryRepository;
import com.survey.meetorsolo.domain.inquiry.entity.Inquiry;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryCategory;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessage;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessageAuthorType;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryPriority;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminInquiryServiceTest {

    private static final long ADMIN_ID = 9L;
    private static final long MEMBER_ID = 1L;
    private static final long INQUIRY_ID = 10L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 9, 9, 12, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private AdminAuthorizationService authorization;
    @Mock
    private AdminInquiryRepository adminInquiries;
    @Mock
    private InquiryRepository inquiries;
    @Mock
    private InquiryMessageRepository messages;
    @Mock
    private AdminInquiryCursorCodec cursorCodec;

    private AdminInquiryService service;

    @BeforeEach
    void setUp() {
        service = new AdminInquiryService(
                authorization,
                adminInquiries,
                inquiries,
                messages,
                cursorCodec,
                Clock.fixed(NOW.toInstant(), ZoneOffset.ofHours(9)));
    }

    @Test
    void 답변하면_답변_완료_상태와_답변_시각을_기록한다() {
        Inquiry target = inquiry(InquiryStatus.RECEIVED);
        when(inquiries.findByIdForUpdate(INQUIRY_ID)).thenReturn(Optional.of(target));
        when(messages.save(any(InquiryMessage.class))).thenAnswer(invocation -> {
            InquiryMessage saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 200L);
            return saved;
        });
        stubDetailLookup();

        AdminInquiryDetailResponse response = service.answer(ADMIN_ID, INQUIRY_ID, "  확인했습니다  ");

        assertThat(response.status()).isEqualTo(InquiryStatus.ANSWERED);
        assertThat(target.getLastAnsweredAt()).isEqualTo(NOW);
        // 사용자가 아직 열지 않았으므로 badge가 켜져 있어야 한다.
        assertThat(target.hasUnreadAnswer()).isTrue();
    }

    @Test
    void 답변은_관리자_발화로_저장된다() {
        Inquiry target = inquiry(InquiryStatus.RECEIVED);
        when(inquiries.findByIdForUpdate(INQUIRY_ID)).thenReturn(Optional.of(target));
        when(messages.save(any(InquiryMessage.class))).thenAnswer(invocation -> {
            InquiryMessage saved = invocation.getArgument(0);
            assertThat(saved.getAuthorType()).isEqualTo(InquiryMessageAuthorType.ADMIN);
            assertThat(saved.getAuthorMemberId()).isEqualTo(ADMIN_ID);
            assertThat(saved.getBody()).isEqualTo("확인했습니다");
            ReflectionTestUtils.setField(saved, "id", 200L);
            return saved;
        });
        stubDetailLookup();

        service.answer(ADMIN_ID, INQUIRY_ID, "  확인했습니다  ");

        verify(messages).save(any(InquiryMessage.class));
    }

    @Test
    void 종결된_문의에는_답변할_수_없다() {
        Inquiry target = inquiry(InquiryStatus.RECEIVED);
        target.changeStatus(InquiryStatus.CLOSED, NOW.minusDays(1));
        when(inquiries.findByIdForUpdate(INQUIRY_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.answer(ADMIN_ID, INQUIRY_ID, "답변"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INQUIRY_CLOSED));

        verify(messages, never()).save(any());
    }

    @Test
    void 빈_답변은_거절한다() {
        assertThatThrownBy(() -> service.answer(ADMIN_ID, INQUIRY_ID, "   "))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_INQUIRY_INVALID_REQUEST));

        verify(inquiries, never()).findByIdForUpdate(INQUIRY_ID);
    }

    @Test
    void 종결하면_종결_시각을_함께_기록한다() {
        // chk_inquiries_closed_at이 상태와 시점을 묶어 고정하므로 둘이 함께 움직여야 한다.
        Inquiry target = inquiry(InquiryStatus.IN_PROGRESS);
        when(inquiries.findByIdForUpdate(INQUIRY_ID)).thenReturn(Optional.of(target));
        stubDetailLookup();

        AdminInquiryDetailResponse response =
                service.update(ADMIN_ID, INQUIRY_ID, AdminInquiryTargetStatus.CLOSED, null);

        assertThat(response.status()).isEqualTo(InquiryStatus.CLOSED);
        assertThat(target.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void 종결된_문의는_상태를_되돌릴_수_없다() {
        Inquiry target = inquiry(InquiryStatus.IN_PROGRESS);
        target.changeStatus(InquiryStatus.CLOSED, NOW.minusDays(1));
        when(inquiries.findByIdForUpdate(INQUIRY_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.update(
                ADMIN_ID, INQUIRY_ID, AdminInquiryTargetStatus.IN_PROGRESS, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_INQUIRY_STATUS_CONFLICT));
    }

    @Test
    void 우선순위만_바꿀_수_있다() {
        // 긴급 지정은 관리자 전용이다. 사용자 등록 요청에는 priority 필드가 없다(docs/28 확정 5번).
        Inquiry target = inquiry(InquiryStatus.RECEIVED);
        when(inquiries.findByIdForUpdate(INQUIRY_ID)).thenReturn(Optional.of(target));
        stubDetailLookup();

        AdminInquiryDetailResponse response =
                service.update(ADMIN_ID, INQUIRY_ID, null, InquiryPriority.URGENT);

        assertThat(response.priority()).isEqualTo(InquiryPriority.URGENT);
        assertThat(response.status()).isEqualTo(InquiryStatus.RECEIVED);
    }

    @Test
    void 상태와_우선순위가_모두_없으면_거절한다() {
        assertThatThrownBy(() -> service.update(ADMIN_ID, INQUIRY_ID, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_INQUIRY_INVALID_REQUEST));

        verify(inquiries, never()).findByIdForUpdate(INQUIRY_ID);
    }

    @Test
    void 없는_문의를_조회하면_NOT_FOUND다() {
        when(inquiries.findById(INQUIRY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(ADMIN_ID, INQUIRY_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_INQUIRY_NOT_FOUND));
    }

    private void stubDetailLookup() {
        when(adminInquiries.findMemberSummary(INQUIRY_ID)).thenReturn(Optional.of(
                new AdminInquiryMemberSummaryResponse(MEMBER_ID, "닉네임", "SUSPENDED")));
        when(messages.findByInquiryIdOrderByIdAsc(INQUIRY_ID)).thenReturn(List.of());
    }

    private static Inquiry inquiry(InquiryStatus status) {
        Inquiry inquiry = Inquiry.create(
                MEMBER_ID, InquiryCategory.SANCTION_APPEAL, "제목", NOW.minusDays(2));
        ReflectionTestUtils.setField(inquiry, "id", INQUIRY_ID);
        ReflectionTestUtils.setField(inquiry, "status", status);
        ReflectionTestUtils.setField(inquiry, "createdAt", NOW.minusDays(2));
        ReflectionTestUtils.setField(inquiry, "updatedAt", NOW.minusDays(2));
        return inquiry;
    }
}
