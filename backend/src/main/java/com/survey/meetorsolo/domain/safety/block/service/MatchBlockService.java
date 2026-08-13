package com.survey.meetorsolo.domain.safety.block.service;

import com.survey.meetorsolo.domain.matching.service.MatchMemberPairLockService;
import com.survey.meetorsolo.domain.safety.block.dto.MatchBlockResponse;
import com.survey.meetorsolo.domain.safety.block.repository.MatchBlockRepository;
import com.survey.meetorsolo.domain.safety.block.repository.MatchBlockRepository.BlockSnapshot;
import com.survey.meetorsolo.domain.safety.block.repository.MatchBlockRepository.GroupSnapshot;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchBlockService {

    private static final int BLOCK_WINDOW_DAYS = 30;
    private static final String INTERNAL_REASON = "MATCH_ROOM_MEMBER_BLOCK";

    private final MatchBlockRepository blocks;
    private final MatchMemberPairLockService memberPairLocks;
    private final Clock clock;

    public MatchBlockService(
            MatchBlockRepository blocks,
            MatchMemberPairLockService memberPairLocks,
            Clock clock
    ) {
        this.blocks = blocks;
        this.memberPairLocks = memberPairLocks;
        this.clock = clock;
    }

    @Transactional
    public MatchBlockResponse block(long blockerMemberId, long groupId, long blockedMemberId) {
        if (blockerMemberId == blockedMemberId) {
            throw new BusinessException(ErrorCode.BLOCK_INVALID_REQUEST);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        GroupSnapshot group = blocks.findGroupForShare(groupId)
                .orElseThrow(this::resourceNotFound);
        if (!blocks.existsParticipant(groupId, blockerMemberId)
                || !blocks.existsParticipant(groupId, blockedMemberId)) {
            throw resourceNotFound();
        }
        validateWindow(group, now);

        memberPairLocks.lock(blockerMemberId, blockedMemberId);
        BlockSnapshot snapshot = blocks.insertIfAbsent(
                        blockerMemberId, blockedMemberId, INTERNAL_REASON, now)
                .orElseGet(() -> blocks.findExisting(blockerMemberId, blockedMemberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.BLOCK_CONFLICT)));
        return new MatchBlockResponse(
                snapshot.blockId(), snapshot.blockedMemberId(), snapshot.createdAt());
    }

    private void validateWindow(GroupSnapshot group, OffsetDateTime now) {
        if ("CONFIRMED".equals(group.status()) || "IN_PROGRESS".equals(group.status())) {
            return;
        }
        OffsetDateTime terminalAt;
        if ("COMPLETED".equals(group.status())) {
            terminalAt = group.completedAt();
        } else if ("CANCELLED".equals(group.status())) {
            terminalAt = group.cancelledAt();
        } else {
            throw new BusinessException(ErrorCode.BLOCK_CONFLICT);
        }
        if (terminalAt == null) {
            throw new BusinessException(ErrorCode.BLOCK_CONFLICT);
        }
        if (now.isAfter(terminalAt.plusDays(BLOCK_WINDOW_DAYS))) {
            throw new BusinessException(ErrorCode.BLOCK_WINDOW_EXPIRED);
        }
    }

    private BusinessException resourceNotFound() {
        return new BusinessException(ErrorCode.BLOCK_RESOURCE_NOT_FOUND);
    }
}
