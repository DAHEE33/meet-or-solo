package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/**
 * 만남 시간이 끝난 그룹을 모아 {@link MatchMeetingCloseGroupService}에 넘긴다.
 *
 * <p>{@code MatchNoShowBatchService}와 같은 구조다. group별 transaction 실패를 서로 격리한다.
 */
@Service
public class MatchMeetingCloseBatchService {

    private final Clock clock;
    private final MatchGroupRepository groups;
    private final MatchMeetingCloseGroupService groupService;

    public MatchMeetingCloseBatchService(Clock clock, MatchGroupRepository groups,
            MatchMeetingCloseGroupService groupService) {
        this.clock = clock;
        this.groups = groups;
        this.groupService = groupService;
    }

    public int runBatch(int batchSize) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        int processed = 0;
        for (Long groupId : groups.findMeetingCloseCandidateIds(now, batchSize)) {
            try {
                if (groupService.process(groupId, now)) processed++;
            } catch (RuntimeException ignored) {
                // group별 transaction 실패를 다음 group과 격리한다.
            }
        }
        return processed;
    }
}
