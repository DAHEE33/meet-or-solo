package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.dto.MatchPoolEntryRequest;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolResponse;
import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.repository.MatchCooldownRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchPoolEntryService {

    private final Clock clock;
    private final MemberRepository members;
    private final MatchPoolRepository pools;
    private final MatchCooldownRepository cooldowns;
    private final MatchGroupMemberRepository groupMembers;

    public MatchPoolEntryService(
            Clock clock,
            MemberRepository members,
            MatchPoolRepository pools,
            MatchCooldownRepository cooldowns,
            MatchGroupMemberRepository groupMembers
    ) {
        this.clock = clock;
        this.members = members;
        this.pools = pools;
        this.cooldowns = cooldowns;
        this.groupMembers = groupMembers;
    }

    @Transactional
    public MatchPoolResponse enter(long memberId, MatchPoolEntryRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCHING_RESOURCE_NOT_FOUND));
        if (!Member.STATUS_ACTIVE.equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.MATCHING_INVALID_REQUEST, "프로필을 완료한 활성 회원만 매칭을 신청할 수 있습니다.");
        }
        if (cooldowns.existsActive(memberId, now)) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT, "cooldown 중에는 매칭을 신청할 수 없습니다.");
        }
        if (pools.existsActiveByMemberId(memberId)) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT, "이미 진행 중인 match pool이 있습니다.");
        }
        if (groupMembers.existsActiveByMemberId(memberId)) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT, "이미 활성 매칭 그룹에 참여 중입니다.");
        }

        long checkinId = pools.findValidCheckinId(memberId, request.festivalId(), now)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MATCHING_INVALID_REQUEST,
                        "해당 축제의 유효한 체크인이 필요합니다."
                ));
        MatchPool pool = MatchPool.waiting(
                memberId,
                request.festivalId(),
                checkinId,
                request.preferredGroupSize(),
                request.allowMinimumTwo(),
                List.of(),
                now,
                now.plusSeconds(60)
        );
        try {
            return MatchPoolResponse.from(pools.saveAndFlush(pool));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT, "이미 진행 중인 match pool이 있습니다.");
        }
    }
}
