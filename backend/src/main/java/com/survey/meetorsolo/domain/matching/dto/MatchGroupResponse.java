package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository.ActiveGroupWithFestivalProjection;
import com.survey.meetorsolo.domain.matching.service.MatchArrivalDeadlinePolicy;
import com.survey.meetorsolo.domain.matching.service.MatchMeetingWindowPolicy;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record MatchGroupResponse(
        Long groupId,
        Long festivalId,
        String status,
        Integer confirmedMemberCount,
        Integer currentMemberCount,
        OffsetDateTime confirmedAt,
        OffsetDateTime arrivalDeadlineAt,
        OffsetDateTime meetingEndsAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Long currentMemberId,
        MatchGroupFestivalResponse festival,
        MatchGroupMeetingPointResponse meetingPoint,
        List<MatchGroupMemberResponse> members,
        boolean meetingHeld
) {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public MatchGroupResponse(
            Long groupId,
            Long festivalId,
            String status,
            Integer confirmedMemberCount,
            OffsetDateTime confirmedAt,
            MatchGroupFestivalResponse festival,
            List<MatchGroupMemberResponse> members
    ) {
        this(
                groupId,
                festivalId,
                status,
                confirmedMemberCount,
                members.size(),
                confirmedAt,
                MatchArrivalDeadlinePolicy.deadlineAt(confirmedAt),
                MatchMeetingWindowPolicy.closesAt(confirmedAt),
                null,
                null,
                null,
                festival,
                null,
                members,
                MatchMeetingWindowPolicy.isMeetingHeld((int) members.stream()
                        .filter(member -> "ARRIVED".equals(member.status()))
                        .count())
        );
    }

    /**
     * 나간 사람이 빠져 현재 활성 참여자만으로는 만남 성립 여부를 다시 셀 수 없다
     * ({@code MatchGroupContinuationPolicy.meetingHeld}와 같은 기준). 그래서 이 값은 활성 인원
     * 목록이 아니라 {@code meetingHeld}로 호출자가 직접 넘긴다 — 나가기 처리 뒤에도 도착 인원
     * 2명 이상이었다는 사실이 유지되게 하기 위해서다.
     */
    public static MatchGroupResponse from(
            ActiveGroupWithFestivalProjection group,
            List<MatchGroupMemberResponse> members,
            long currentMemberId,
            int arrivalRadiusMeters,
            boolean meetingHeld
    ) {
        OffsetDateTime confirmedAt = group.getConfirmedAt()
                .atZone(KOREA_ZONE)
                .toOffsetDateTime();
        return new MatchGroupResponse(
                group.getGroupId(),
                group.getFestivalId(),
                group.getStatus(),
                group.getConfirmedMemberCount(),
                members.size(),
                confirmedAt,
                MatchArrivalDeadlinePolicy.deadlineAt(confirmedAt),
                MatchMeetingWindowPolicy.closesAt(confirmedAt),
                group.getStartedAt() == null
                        ? null
                        : group.getStartedAt().atZone(KOREA_ZONE).toOffsetDateTime(),
                group.getCompletedAt() == null
                        ? null
                        : group.getCompletedAt().atZone(KOREA_ZONE).toOffsetDateTime(),
                currentMemberId,
                MatchGroupFestivalResponse.from(group),
                MatchGroupMeetingPointResponse.from(group, arrivalRadiusMeters),
                List.copyOf(members),
                meetingHeld
        );
    }
}
