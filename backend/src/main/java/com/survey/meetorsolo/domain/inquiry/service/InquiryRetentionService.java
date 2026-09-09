package com.survey.meetorsolo.domain.inquiry.service;

import com.survey.meetorsolo.domain.inquiry.entity.Inquiry;
import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessage;
import com.survey.meetorsolo.domain.inquiry.repository.InquiryMessageRepository;
import com.survey.meetorsolo.domain.inquiry.repository.InquiryRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기간이 지난 종결 문의의 본문을 익명화한다.
 *
 * <p>보관 기간은 <b>종결 후 1년</b>이다. 개인정보 최소보관 원칙과 제재 이의제기 분쟁 대비를
 * 함께 만족시키는 값으로 확정했다(docs/28_MEMBER_INQUIRY_DESIGN.md 확정 7번).
 *
 * <p>행을 지우지 않고 제목·본문만 고정 문구로 덮는다. FK가 모두
 * {@code ON DELETE RESTRICT}이고, {@code category}·{@code status}·{@code created_at}은 통계와
 * 감사 목적으로 남겨야 한다.
 *
 * <p>재처리는 {@code anonymized_at}이 막는다. 본문 문구를 비교해 판정하면 사용자가 우연히 같은
 * 문구를 입력한 경우와 구분되지 않는다(docs/28 5.6).
 */
@Service
public class InquiryRetentionService {

    public static final Period RETENTION_PERIOD = Period.ofYears(1);
    static final String ANONYMIZED_TITLE = "보관 기간이 지나 삭제된 문의입니다.";
    static final String ANONYMIZED_BODY = "보관 기간이 지나 삭제된 내용입니다.";

    private final InquiryRepository inquiries;
    private final InquiryMessageRepository messages;
    private final Clock clock;

    public InquiryRetentionService(
            InquiryRepository inquiries,
            InquiryMessageRepository messages,
            Clock clock
    ) {
        this.inquiries = inquiries;
        this.messages = messages;
        this.clock = clock;
    }

    /**
     * 보관 기간이 지난 문의를 최대 {@code batchSize}건 익명화한다.
     *
     * @return 익명화한 문의 수
     */
    @Transactional
    public int anonymizeBatch(int batchSize) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime threshold = now.minus(RETENTION_PERIOD);

        List<Inquiry> targets = inquiries.findAnonymizationTargets(threshold, batchSize);
        if (targets.isEmpty()) {
            return 0;
        }

        Set<Long> inquiryIds = targets.stream().map(Inquiry::getId).collect(Collectors.toSet());
        for (InquiryMessage message : messages.findByInquiryIdIn(inquiryIds)) {
            message.anonymize(ANONYMIZED_BODY);
        }
        for (Inquiry inquiry : targets) {
            inquiry.anonymize(ANONYMIZED_TITLE, now);
        }
        return targets.size();
    }
}
