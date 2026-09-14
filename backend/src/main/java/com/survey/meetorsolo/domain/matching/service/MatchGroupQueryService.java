package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.config.MatchingArrivalProperties;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupMemberResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupResponse;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository.ActiveGroupWithFestivalProjection;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MatchGroupQueryService {

    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MemberRepository members;
    private final MatchingArrivalProperties arrivalProperties;

    public MatchGroupQueryService(
            MatchGroupRepository groups,
            MatchGroupMemberRepository groupMembers,
            MemberRepository members,
            MatchingArrivalProperties arrivalProperties
    ) {
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.members = members;
        this.arrivalProperties = arrivalProperties;
    }

    public MatchGroupResponse currentGroup(long memberId) {
        requireMember(memberId);
        List<ActiveGroupWithFestivalProjection> activeGroups = groups.findActiveByMemberId(memberId);
        if (activeGroups.isEmpty()) {
            return null;
        }
        if (activeGroups.size() != 1) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT);
        }

        ActiveGroupWithFestivalProjection group = activeGroups.get(0);
        List<MatchGroupMemberResponse> participants = groupMembers
                .findActiveMembersWithProfileByGroupId(group.getGroupId())
                .stream()
                .map(MatchGroupMemberResponse::from)
                .toList();

        // 활성 참여자가 한 명만 남는 것은 이제 정상 상태다. 만남이 성립한 방(도착자 2명 이상)은
        // 상대가 먼저 나가도 종료하지 않고 만남 시간이 끝날 때까지 유지하기 때문이다
        // (docs/19 4.11.3, MatchGroupContinuationPolicy 참고).
        if (participants.isEmpty()
                || participants.size() > group.getConfirmedMemberCount()
                || participants.stream().noneMatch(member -> member.memberId() == memberId)) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT);
        }
        return MatchGroupResponse.from(
                group, participants, memberId, arrivalProperties.radiusMeters());
    }

    public MatchGroupResponse snapshot(long groupId, long memberId) {
        ActiveGroupWithFestivalProjection group = groups.findSnapshotById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCHING_CONFLICT));
        List<MatchGroupMemberResponse> participants = ("COMPLETED".equals(group.getStatus())
                ? groupMembers.findCompletedMembersWithProfileByGroupId(groupId)
                : groupMembers.findActiveMembersWithProfileByGroupId(groupId))
                .stream()
                .map(MatchGroupMemberResponse::from)
                .toList();
        if (participants.stream().noneMatch(member -> member.memberId() == memberId)) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT);
        }
        return MatchGroupResponse.from(
                group, participants, memberId, arrivalProperties.radiusMeters());
    }

    private void requireMember(long memberId) {
        if (!members.existsById(memberId)) {
            throw new BusinessException(ErrorCode.MATCHING_RESOURCE_NOT_FOUND);
        }
    }
}
