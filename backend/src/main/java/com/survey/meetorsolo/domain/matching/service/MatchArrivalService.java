package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.config.MatchingArrivalProperties;
import com.survey.meetorsolo.domain.matching.dto.MatchArrivalRequest;
import com.survey.meetorsolo.domain.matching.dto.MatchGroupResponse;
import com.survey.meetorsolo.domain.matching.entity.MatchEvent;
import com.survey.meetorsolo.domain.matching.entity.MatchGroup;
import com.survey.meetorsolo.domain.matching.entity.MatchGroupMember;
import com.survey.meetorsolo.domain.matching.event.MatchingStateChangedEvent;
import com.survey.meetorsolo.domain.matching.repository.MatchEventRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupMemberRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchGroupRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import com.survey.meetorsolo.global.geo.GeoDistanceCalculator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchArrivalService {

    private static final Logger log = LoggerFactory.getLogger(MatchArrivalService.class);

    private final Clock clock;
    private final MatchGroupRepository groups;
    private final MatchGroupMemberRepository groupMembers;
    private final MatchEventRepository events;
    private final MatchGroupQueryService groupQueries;
    private final ApplicationEventPublisher eventPublisher;
    private final MatchingArrivalProperties arrivalProperties;
    private final MemberRepository members;

    public MatchArrivalService(
            Clock clock,
            MatchGroupRepository groups,
            MatchGroupMemberRepository groupMembers,
            MatchEventRepository events,
            MatchGroupQueryService groupQueries,
            ApplicationEventPublisher eventPublisher,
            MatchingArrivalProperties arrivalProperties,
            MemberRepository members
    ) {
        this.clock = clock;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.events = events;
        this.groupQueries = groupQueries;
        this.eventPublisher = eventPublisher;
        this.arrivalProperties = arrivalProperties;
        this.members = members;
    }

    @Transactional
    public MatchGroupResponse arrive(long memberId, MatchArrivalRequest request) {
        MatchGroup group = findActiveOrLatestCompletedGroup(memberId);
        List<MatchGroupMember> members = groupMembers.findAllByGroupIdForUpdate(group.getId());
        MatchGroupMember member = members.stream()
                .filter(candidate -> candidate.getMemberId() == memberId)
                .findFirst()
                .orElseThrow(this::conflict);
        if ("COMPLETED".equals(group.getStatus())) {
            if (!"COMPLETED".equals(member.getStatus())) throw conflict();
            return groupQueries.snapshot(group.getId(), memberId);
        }
        validateLockedState(group, member);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!now.isBefore(MatchArrivalDeadlinePolicy.deadlineAt(group.getConfirmedAt()))) {
            throw new BusinessException(ErrorCode.MATCHING_ARRIVAL_DEADLINE_EXCEEDED);
        }
        if ("ARRIVED".equals(member.getStatus())) {
            return groupQueries.snapshot(group.getId(), memberId);
        }
        member.arrive(now, verifyArrivalDistance(group, memberId, request));
        group.start(now);
        events.save(MatchEvent.memberArrived(
                group.getId(), group.getAttemptId(), memberId, now
        ));

        // 전원이 도착해도 여기서 완료로 닫지 않는다. 도착은 만남의 시작이고 완료 판정은
        // 확정 + MatchMeetingWindowPolicy.MEETING_WINDOW에서 MatchMeetingCloseGroupService가
        // 한다(docs/19 4.11.2). 예전에는 마지막 도착자가 버튼을 누르는 순간 상태방이 사라졌고,
        // 활성 구성원이 한 명만 남은 그룹에서는 단독 도착이 완료·보상으로 이어졌다.
        List<MatchGroupMember> activeMembers = members.stream().filter(this::isActive).toList();
        List<Long> activeMemberIds = activeMembers.stream()
                .map(MatchGroupMember::getMemberId)
                .toList();
        boolean allArrived = !activeMembers.isEmpty()
                && activeMembers.stream().allMatch(candidate -> "ARRIVED".equals(candidate.getStatus()));
        groupMembers.flush();
        groups.flush();
        events.flush();
        eventPublisher.publishEvent(new MatchingStateChangedEvent(
                activeMemberIds, allArrived ? "ALL_ARRIVED" : "MEMBER_ARRIVED", now
        ));
        return groupQueries.snapshot(group.getId(), memberId);
    }

    /**
     * 만남 장소와의 거리를 재고 반경 안인지 확인한다({@code docs/19} 4.11.3).
     *
     * <p>돌려주는 값은 저장할 거리다. <b>좌표 자체는 어디에도 남기지 않는다</b> — 체크인이
     * {@code distance_meters}만 남기는 것과 같은 원칙이고 개인정보처리방침에 그렇게 적혀 있다.
     *
     * <p>만남 장소 좌표가 없는 그룹은 검증하지 않는다. 장소 좌표는 그룹 확정 시점에 복사되는데,
     * 이 기능 이전에 만들어진 그룹이나 좌표가 없는 장소가 있을 수 있다. 그 경우에 도착을 막으면
     * 잘못은 사용자 쪽이 아닌데 만남이 무산된다.
     */
    private Integer verifyArrivalDistance(MatchGroup group, long memberId, MatchArrivalRequest request) {
        if (group.getMeetingMapX() == null || group.getMeetingMapY() == null || request == null) {
            return null;
        }
        long distanceMeters = GeoDistanceCalculator.metersBetween(
                group.getMeetingMapY(), group.getMeetingMapX(),
                request.latitude(), request.longitude());
        int radiusMeters = arrivalProperties.radiusMeters();
        if (distanceMeters > radiusMeters) {
            // 체크인과 같은 두 경로다(FestivalCheckinService 참고).
            // - 설정: 환경 전체를 끈다. 실기기 GPS가 없는 환경의 임시 수단이다.
            // - 테스트 계정: 그 계정만 면제한다. 같은 환경에서 일반 계정은 검증을 그대로 받는다.
            boolean testAccount = members.existsByIdAndTestAccountIsTrue(memberId);
            if (!arrivalProperties.bypassRadiusCheck() && !testAccount) {
                throw new BusinessException(ErrorCode.MATCHING_ARRIVAL_OUT_OF_RANGE);
            }
            log.warn("도착 반경 검증을 건너뛰고 도착을 인정했습니다(사유={}). "
                            + "memberId={}, groupId={}, distanceMeters={}, radiusMeters={}",
                    testAccount ? "TEST_ACCOUNT" : "CONFIG_BYPASS",
                    memberId, group.getId(), distanceMeters, radiusMeters);
        }
        return Math.toIntExact(distanceMeters);
    }

    private MatchGroup findActiveOrLatestCompletedGroup(long memberId) {
        List<MatchGroup> activeGroups = groups.findActiveByMemberIdForUpdate(memberId);
        if (activeGroups.size() > 1) throw conflict();
        if (activeGroups.size() == 1) return activeGroups.get(0);
        return groups.findLatestCompletedByMemberIdForUpdate(memberId)
                .orElseThrow(this::conflict);
    }

    private void validateLockedState(MatchGroup group, MatchGroupMember member) {
        if ((!"CONFIRMED".equals(group.getStatus()) && !"IN_PROGRESS".equals(group.getStatus()))
                || (!"JOINED".equals(member.getStatus())
                && !"ARRIVAL_TIME_SELECTED".equals(member.getStatus())
                && !"ARRIVED".equals(member.getStatus()))) {
            throw conflict();
        }
    }

    private boolean isActive(MatchGroupMember member) {
        return "JOINED".equals(member.getStatus())
                || "ARRIVAL_TIME_SELECTED".equals(member.getStatus())
                || "ARRIVED".equals(member.getStatus());
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.MATCHING_CONFLICT);
    }
}
