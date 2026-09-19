package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.festival.entity.FestivalMeetingPointStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalMeetingPointRepository;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolEntryRequest;
import com.survey.meetorsolo.domain.matching.dto.MatchPoolResponse;
import com.survey.meetorsolo.domain.matching.entity.MatchCollectionWindow;
import com.survey.meetorsolo.domain.matching.entity.MatchPool;
import com.survey.meetorsolo.domain.matching.repository.MatchCooldownRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPoolRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.policy.MannerTemperaturePolicy;
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
    private final MatchGroupRepository groups;
    private final MatchCompletionLockPolicy completionLocks;
    private final MatchCollectionWindowService collectionWindows;
    private final FestivalMeetingPointRepository meetingPoints;

    public MatchPoolEntryService(
            Clock clock,
            MemberRepository members,
            MatchPoolRepository pools,
            MatchCooldownRepository cooldowns,
            MatchGroupMemberRepository groupMembers,
            MatchGroupRepository groups,
            MatchCompletionLockPolicy completionLocks,
            MatchCollectionWindowService collectionWindows,
            FestivalMeetingPointRepository meetingPoints
    ) {
        this.clock = clock;
        this.members = members;
        this.pools = pools;
        this.cooldowns = cooldowns;
        this.groupMembers = groupMembers;
        this.groups = groups;
        this.completionLocks = completionLocks;
        this.collectionWindows = collectionWindows;
        this.meetingPoints = meetingPoints;
    }

    @Transactional
    public MatchPoolResponse enter(long memberId, MatchPoolEntryRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCHING_RESOURCE_NOT_FOUND));
        if (!Member.STATUS_ACTIVE.equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.MATCHING_INVALID_REQUEST, "프로필을 완료한 활성 회원만 매칭을 신청할 수 있습니다.");
        }
        // 온도 제한은 회원 status를 바꾸지 않는다(docs/19 4.3). 조회와 문의는 그대로 되고
        // 매칭 신청만 막힌다. 회복 경로는 만남 완료 보상과 시간 경과 회복 둘이다.
        if (!MannerTemperaturePolicy.matchingAllowed(member.getMannerTemperature())) {
            throw new BusinessException(ErrorCode.MATCHING_TEMPERATURE_RESTRICTED);
        }
        if (pools.existsActiveByMemberId(memberId)) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT, "이미 진행 중인 match pool이 있습니다.");
        }
        if (groupMembers.existsActiveByMemberId(memberId)) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT, "이미 활성 매칭 그룹에 참여 중입니다.");
        }
        if (cooldowns.existsActive(memberId, now)) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT, "cooldown 중에는 매칭을 신청할 수 없습니다.");
        }
        var completionLock = completionLocks.evaluate(
                groups.findLatestHeldMeetingByMemberId(memberId).orElse(null), now);
        if (completionLock.active()) {
            throw new BusinessException(ErrorCode.MATCHING_COMPLETION_LOCKED);
        }
        if (!meetingPoints.existsByFestivalIdAndStatus(
                request.festivalId(), FestivalMeetingPointStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.MATCHING_MEETING_POINT_NOT_READY);
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
        // 이 축제의 수집 구간에 합류한다. 열린 구간이 없거나 수집 시간이 끝났으면 새로 연다.
        // 구간이 정해지는 시점이 곧 "첫 유효 대기자가 들어온 시점"이며, 그 구간의 종료 시각은
        // 이후 누가 취소·매칭·만료되어도 다시 계산하지 않는다.
        MatchCollectionWindow window = collectionWindows.openOrJoin(request.festivalId(), now);
        pool.assignCollectWindow(window.getId(), now);

        try {
            // 조합은 scheduler tick에서만 수행한다(docs/05 매칭 흐름 3번).
            //
            // 예전에는 여기서 MatchingPoolEnteredEvent를 발행해 신청 커밋 직후 즉시 조합했다.
            // 그러면 유효한 조합이 처음 생기는 순간 바로 소진돼 대기 후보가 2명을 넘지 못했고,
            // 조합이 1개뿐이라 궁합 점수가 순위에 개입할 수 없었다. 결과적으로 취향 임베딩이
            // 매칭 결과를 바꾸지 못하고 먼저 신청한 사람끼리 묶였다.
            MatchPool savedPool = pools.saveAndFlush(pool);
            return MatchPoolResponse.from(savedPool);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.MATCHING_CONFLICT, "이미 진행 중인 match pool이 있습니다.");
        }
    }
}
