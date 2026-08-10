package com.survey.meetorsolo.domain.matching.dto;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository.ActiveGroupWithFestivalProjection;
import com.survey.meetorsolo.domain.matching.service.MatchArrivalDeadlinePolicy;
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
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Long currentMemberId,
        MatchGroupFestivalResponse festival,
        MatchGroupMeetingPointResponse meetingPoint,
        List<MatchGroupMemberResponse> members
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
                null,
                null,
                null,
                festival,
                null,
                members
        );
    }

    public static MatchGroupResponse from(
            ActiveGroupWithFestivalProjection group,
            List<MatchGroupMemberResponse> members,
            long currentMemberId
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
                group.getStartedAt() == null
                        ? null
                        : group.getStartedAt().atZone(KOREA_ZONE).toOffsetDateTime(),
                group.getCompletedAt() == null
                        ? null
                        : group.getCompletedAt().atZone(KOREA_ZONE).toOffsetDateTime(),
                currentMemberId,
                MatchGroupFestivalResponse.from(group),
                MatchGroupMeetingPointResponse.from(group),
                List.copyOf(members)
        );
    }
}
