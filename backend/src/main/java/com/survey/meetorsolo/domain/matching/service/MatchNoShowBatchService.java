package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class MatchNoShowBatchService {
    private final Clock clock;
    private final MatchGroupRepository groups;
    private final MatchNoShowGroupService groupService;

    public MatchNoShowBatchService(Clock clock, MatchGroupRepository groups,
            MatchNoShowGroupService groupService) {
        this.clock = clock;
        this.groups = groups;
        this.groupService = groupService;
    }

    public int runBatch(int batchSize) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        int processed = 0;
        for (Long groupId : groups.findNoShowCandidateIds(now, batchSize)) {
            try {
                if (groupService.process(groupId, now)) processed++;
            } catch (RuntimeException ignored) {
                // group별 transaction 실패를 다음 group과 격리한다.
            }
        }
        return processed;
    }
}
