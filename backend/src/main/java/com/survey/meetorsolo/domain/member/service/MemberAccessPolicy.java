package com.survey.meetorsolo.domain.member.service;

import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberAccessPolicy {

    private final MemberRepository members;
    private final Clock clock;

    public MemberAccessPolicy(MemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public Member requireAccessible(long memberId) {
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        member.restoreExpiredSuspension(OffsetDateTime.now(clock));
        requireAccessible(member);
        return member;
    }

    public void requireAccessible(Member member) {
        switch (member.getStatus()) {
            case Member.STATUS_ACTIVE, Member.STATUS_PROFILE_REQUIRED -> { }
            case Member.STATUS_SUSPENDED -> throw new BusinessException(ErrorCode.MEMBER_SUSPENDED);
            case Member.STATUS_BANNED -> throw new BusinessException(ErrorCode.MEMBER_BANNED);
            default -> throw new BusinessException(ErrorCode.MEMBER_INACTIVE);
        }
    }
}
