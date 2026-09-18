package com.survey.meetorsolo.domain.safety.report.service;

import com.survey.meetorsolo.domain.safety.report.dto.MatchReportReasonCode;
import com.survey.meetorsolo.domain.safety.report.policy.MatchReportWindowPolicy;
import com.survey.meetorsolo.domain.safety.report.dto.MatchReportResponse;
import com.survey.meetorsolo.domain.safety.report.repository.MatchReportRepository;
import com.survey.meetorsolo.domain.safety.report.repository.MatchReportRepository.GroupSnapshot;
import com.survey.meetorsolo.domain.safety.report.repository.MatchReportRepository.ReportSnapshot;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchReportService {

    private final MatchReportRepository reports;
    private final Clock clock;

    public MatchReportService(MatchReportRepository reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @Transactional
    public MatchReportResponse submit(
            long reporterMemberId,
            long groupId,
            long reportedMemberId,
            MatchReportReasonCode reasonCode
    ) {
        if (reporterMemberId == reportedMemberId) {
            throw new BusinessException(ErrorCode.REPORT_INVALID_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        GroupSnapshot group = reports.findGroupForShare(groupId)
                .orElseThrow(this::resourceNotFound);

        if (!reports.existsParticipant(groupId, reporterMemberId)
                || !reports.existsParticipant(groupId, reportedMemberId)) {
            throw resourceNotFound();
        }

        validateMeetingHeld(groupId);
        validateReportWindow(group, now);

        ReportSnapshot snapshot = reports.insertIfAbsent(
                        reporterMemberId, reportedMemberId, groupId, reasonCode, now)
                .orElseGet(() -> reports.findExisting(
                                reporterMemberId, reportedMemberId, groupId, reasonCode)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_CONFLICT)));
        return toResponse(snapshot);
    }

    /**
     * 만남이 성사되지 않은 매칭은 신고 대상이 아니다({@code docs/19} 4.11.1).
     *
     * <p>예전에는 그룹이 만들어지기만 하면 참가자 전원을 신고할 수 있었다. 확정 3분 뒤에 깨진
     * 매칭도, 누구 때문에 깨졌는지 알 수 없는 상태에서 함께 있던 사람을 14일 동안 신고할 수
     * 있었다는 뜻이다. 이 서비스에는 자유 채팅이 없어 <b>만나기 전에 생길 수 있는 피해가 사실상
     * 없으므로</b> 도착자가 한 명도 없는 그룹은 제외한다.
     *
     * <p>기준을 경과 시간(예: 확정 후 N분)이 아니라 도착 여부로 잡은 이유는 노쇼다. 상대가
     * 오지 않아 취소된 건은 신고할 수 있어야 하는데, 그 경우 기다린 쪽이 이미 도착해 있으므로
     * 이 조건을 자연스럽게 통과한다. 경과 시간으로 자르면 그 구분이 되지 않는다.
     */
    private void validateMeetingHeld(long groupId) {
        if (!reports.existsArrival(groupId)) {
            throw new BusinessException(ErrorCode.REPORT_MEETING_NOT_HELD);
        }
    }

    private void validateReportWindow(GroupSnapshot group, OffsetDateTime now) {
        if ("CONFIRMED".equals(group.status()) || "IN_PROGRESS".equals(group.status())) {
            return;
        }

        OffsetDateTime terminalAt;
        if ("COMPLETED".equals(group.status())) {
            terminalAt = group.completedAt();
        } else if ("CANCELLED".equals(group.status())) {
            terminalAt = group.cancelledAt();
        } else {
            throw new BusinessException(ErrorCode.REPORT_CONFLICT);
        }

        if (terminalAt == null) {
            throw new BusinessException(ErrorCode.REPORT_CONFLICT);
        }
        if (!MatchReportWindowPolicy.isReportable(terminalAt, now)) {
            throw new BusinessException(ErrorCode.REPORT_WINDOW_EXPIRED);
        }
    }

    private BusinessException resourceNotFound() {
        return new BusinessException(ErrorCode.REPORT_RESOURCE_NOT_FOUND);
    }

    private MatchReportResponse toResponse(ReportSnapshot snapshot) {
        return new MatchReportResponse(
                snapshot.reportId(),
                snapshot.groupId(),
                snapshot.reportedMemberId(),
                snapshot.reasonCode(),
                snapshot.status(),
                snapshot.createdAt()
        );
    }
}
