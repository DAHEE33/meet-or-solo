package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.dto.MatchGroupMemberResponse;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupResponse;
import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
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

    public MatchGroupQueryService(
            MatchGroupRepository groups,
            MatchGroupMemberRepository groupMembers,
            MemberRepository members
    ) {
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.members = members;
    }

    public MatchGroupResponse currentGroup(long memberId) {
        requireMember(memberId);
        List<MatchGroup> activeGroups = groups.findActiveByMemberId(memberId);
        if (activeGroups.isEmpty()) {
            return null;
        }
        if (activeGroups.size() != 1) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT);
        }

        MatchGroup group = activeGroups.get(0);
        List<MatchGroupMemberResponse> participants = groupMembers
                .findActiveMembersWithProfileByGroupId(group.getId())
                .stream()
                .map(MatchGroupMemberResponse::from)
                .toList();

        if (participants.size() != group.getConfirmedMemberCount()
                || participants.stream().noneMatch(member -> member.memberId() == memberId)) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT);
        }
        return MatchGroupResponse.from(group, participants);
    }

    private void requireMember(long memberId) {
        if (!members.existsById(memberId)) {
            throw new BusinessException(ErrorCode.MATCHING_RESOURCE_NOT_FOUND);
        }
    }
}
