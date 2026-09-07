package com.survey.meetorsolo.domain.matching.history.service;

import com.survey.meetorsolo.domain.matching.history.dto.MatchHistoryItemResponse;
import com.survey.meetorsolo.domain.matching.history.dto.MatchHistoryMemberResponse;
import com.survey.meetorsolo.domain.matching.history.dto.MatchHistoryPaginationResponse;
import com.survey.meetorsolo.domain.matching.history.dto.MatchHistoryResponse;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import com.survey.meetorsolo.domain.safety.report.policy.MatchReportWindowPolicy;
import com.survey.meetorsolo.domain.safety.report.repository.MatchReportRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만남 종료 후 신고 진입점을 위한 매칭 기록 조회다(docs/19 4.10).
 *
 * <p>정상 종료와 취소를 모두 담고, 신고 가능 기간이 지난 기록도 목록에는 남긴다. 기간 판정은
 * 접수 API와 같은 {@link MatchReportWindowPolicy}를 쓴다. 화면이 날짜를 직접 계산하면 정책이
 * 갈라져 "신고 가능"으로 보이는 항목이 접수에서 거절될 수 있다.
 */
@Service
public class MatchHistoryService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MatchReportRepository reports;
    private final MatchHistoryCursorCodec cursors;
    private final Clock clock;

    public MatchHistoryService(
            MatchGroupRepository groups,
            MatchGroupMemberRepository groupMembers,
            MatchReportRepository reports,
            MatchHistoryCursorCodec cursors,
            Clock clock
    ) {
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.reports = reports;
        this.cursors = cursors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MatchHistoryResponse getMyHistory(long memberId, String cursor, Integer size) {
        int pageSize = normalizeSize(size);
        MatchHistoryCursorCodec.Cursor decoded = cursor == null || cursor.isBlank()
                ? null
                : cursors.decode(cursor);

        // hasNext 판정을 위해 한 건 더 읽고 응답에서는 pageSize까지만 남긴다.
        List<MatchGroupRepository.MatchHistoryGroupProjection> rows = groups.findHistoryByMemberId(
                memberId,
                decoded == null ? null : decoded.endedAt(),
                decoded == null ? 0L : decoded.groupId(),
                pageSize + 1);

        boolean hasNext = rows.size() > pageSize;
        List<MatchGroupRepository.MatchHistoryGroupProjection> page =
                hasNext ? rows.subList(0, pageSize) : rows;
        if (page.isEmpty()) {
            return new MatchHistoryResponse(
                    List.of(), new MatchHistoryPaginationResponse(pageSize, false, null));
        }

        List<Long> groupIds = page.stream()
                .map(MatchGroupRepository.MatchHistoryGroupProjection::getGroupId)
                .toList();
        Map<Long, List<MatchGroupMemberRepository.MatchHistoryMemberProjection>> membersByGroup =
                new LinkedHashMap<>();
        for (MatchGroupMemberRepository.MatchHistoryMemberProjection row
                : groupMembers.findHistoryMembersByGroupIds(groupIds, memberId)) {
            membersByGroup.computeIfAbsent(row.getGroupId(), ignored -> new ArrayList<>()).add(row);
        }

        Set<String> reportedPairs = new HashSet<>();
        for (MatchReportRepository.ReportedPair pair
                : reports.findReportedPairs(memberId, groupIds)) {
            reportedPairs.add(pairKey(pair.groupId(), pair.reportedMemberId()));
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<MatchHistoryItemResponse> items = new ArrayList<>(page.size());
        for (MatchGroupRepository.MatchHistoryGroupProjection group : page) {
            OffsetDateTime endedAt = group.getEndedAt().atOffset(now.getOffset());
            items.add(new MatchHistoryItemResponse(
                    group.getGroupId(),
                    group.getStatus(),
                    group.getFestivalTitle(),
                    group.getFestivalAddress(),
                    group.getMeetingPlaceName(),
                    group.getConfirmedMemberCount() == null ? 0 : group.getConfirmedMemberCount(),
                    endedAt,
                    MatchReportWindowPolicy.reportableUntil(endedAt),
                    MatchReportWindowPolicy.isReportable(endedAt, now),
                    members(membersByGroup.get(group.getGroupId()), group.getGroupId(), reportedPairs)));
        }

        MatchHistoryItemResponse last = items.get(items.size() - 1);
        return new MatchHistoryResponse(items, new MatchHistoryPaginationResponse(
                pageSize,
                hasNext,
                hasNext ? cursors.encode(last.endedAt(), last.groupId()) : null));
    }

    private List<MatchHistoryMemberResponse> members(
            List<MatchGroupMemberRepository.MatchHistoryMemberProjection> rows,
            long groupId,
            Set<String> reportedPairs
    ) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new MatchHistoryMemberResponse(
                        row.getMemberId(),
                        row.getNickname(),
                        row.getProfileImageUrl(),
                        reportedPairs.contains(pairKey(groupId, row.getMemberId()))))
                .toList();
    }

    private static String pairKey(long groupId, long memberId) {
        return groupId + ":" + memberId;
    }

    private static int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
