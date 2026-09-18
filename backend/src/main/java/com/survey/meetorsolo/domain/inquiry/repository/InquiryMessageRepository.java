package com.survey.meetorsolo.domain.inquiry.repository;

import com.survey.meetorsolo.domain.inquiry.entity.InquiryMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {

    /** 스레드 조회. {@code idx_inquiry_messages_inquiry}를 탄다(docs/29 4.3). */
    List<InquiryMessage> findByInquiryIdOrderByIdAsc(long inquiryId);

    /** 보관 기간 경과 익명화용. 여러 스레드의 발화를 한 번에 읽는다(docs/29 5.6). */
    List<InquiryMessage> findByInquiryIdIn(Collection<Long> inquiryIds);
}
