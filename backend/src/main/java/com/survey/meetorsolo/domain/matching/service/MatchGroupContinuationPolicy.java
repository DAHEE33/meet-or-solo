package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MatchGroupContinuationPolicy {
    public String cancellationReason(List<MatchGroupMember> active) {
        if (active.size() <= 1) return "INSUFFICIENT_ACTIVE_MEMBERS";
        if (active.size() == 2
                && active.stream().anyMatch(member -> !Boolean.TRUE.equals(member.getAllowMinimumTwo()))) {
            return "MINIMUM_TWO_NOT_ALLOWED";
        }
        return null;
    }
}
