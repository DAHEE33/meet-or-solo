package com.survey.meetorsolo.domain.safety.block.service;

import com.survey.meetorsolo.domain.safety.block.dto.MemberBlockResponse;
import com.survey.meetorsolo.domain.safety.block.repository.MemberBlockRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberBlockService {
    private final MemberBlockRepository blocks;

    public MemberBlockService(MemberBlockRepository blocks) {
        this.blocks = blocks;
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
        blocks.delete(blockerMemberId, blockedMemberId);
    }
}
