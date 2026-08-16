package com.survey.meetorsolo.domain.safety.block.service;

import com.survey.meetorsolo.domain.safety.block.dto.MemberBlockResponse;
import com.survey.meetorsolo.domain.safety.block.repository.MemberBlockRepository;
import com.survey.meetorsolo.domain.matching.service.MatchMemberPairLockService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberBlockService {
    private final MemberBlockRepository blocks;
    private final MatchMemberPairLockService memberPairLocks;

    public MemberBlockService(MemberBlockRepository blocks, MatchMemberPairLockService memberPairLocks) {
        this.blocks = blocks;
        this.memberPairLocks = memberPairLocks;
    }

    @Transactional(readOnly = true)
    public List<MemberBlockResponse> getMyBlocks(long blockerMemberId) {
        return blocks.findAllByBlockerMemberId(blockerMemberId).stream()
                .map(block -> new MemberBlockResponse(block.blockedMemberId(), block.nickname(),
                        block.profileImageUrl(), block.blockedAt()))
                .toList();
    }

    @Transactional
    public void unblock(long blockerMemberId, long blockedMemberId) {
        memberPairLocks.lock(blockerMemberId, blockedMemberId);
        blocks.delete(blockerMemberId, blockedMemberId);
    }
}
