package com.survey.meetorsolo.domain.matching.service;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MeetingPointRoundRobinPolicy {
    public <T> T select(List<T> orderedActiveCandidates, long assignedGroupCount) {
        if (orderedActiveCandidates == null || orderedActiveCandidates.isEmpty()) {
            throw new IllegalArgumentException("활성 만남 장소가 필요합니다.");
        }
        if (assignedGroupCount < 0) throw new IllegalArgumentException("배정 group 수는 0 이상이어야 합니다.");
        return orderedActiveCandidates.get(Math.toIntExact(assignedGroupCount % orderedActiveCandidates.size()));
    }
}
