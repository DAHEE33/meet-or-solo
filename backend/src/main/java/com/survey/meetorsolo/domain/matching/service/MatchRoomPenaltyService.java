package com.survey.meetorsolo.domain.matching.service;

import com.survey.meetorsolo.domain.matching.entity.MatchCooldown;
import com.survey.meetorsolo.domain.matching.entity.MatchPenaltyEvent;
import com.survey.meetorsolo.domain.matching.repository.MatchCooldownRepository;
import com.survey.meetorsolo.domain.matching.repository.MatchPenaltyEventRepository;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchRoomPenaltyService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final MatchCooldownRepository cooldowns;
    private final MatchPenaltyEventRepository penalties;
    private final MemberRepository members;

    public MatchRoomPenaltyService(MatchCooldownRepository cooldowns,
            MatchPenaltyEventRepository penalties, MemberRepository members) {
        this.cooldowns = cooldowns;
        this.penalties = penalties;
        this.members = members;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyCancellation(long groupId, long attemptId, long memberId,
            OffsetDateTime now) {
        apply(groupId, attemptId, memberId, "CANCEL", 1, now, count -> {
            if (count == 1) return Duration.ofMinutes(10);
            if (count == 2) return Duration.ofMinutes(30);
            return Duration.ofMinutes(60);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyNoShow(long groupId, long attemptId, long memberId, OffsetDateTime now) {
        apply(groupId, attemptId, memberId, "NO_SHOW", 3, now,
                count -> count == 1 ? Duration.ofMinutes(30) : Duration.ofMinutes(60));
    }

    private void apply(long groupId, long attemptId, long memberId, String cause, int score,
            OffsetDateTime now, java.util.function.LongFunction<Duration> durationPolicy) {
        if (penalties.existsByRelatedGroupIdAndMemberIdAndEventType(groupId, memberId, cause)) {
            return;
        }
        OffsetDateTime dayStart = now.atZoneSameInstant(SEOUL).toLocalDate()
                .atStartOfDay(SEOUL).toOffsetDateTime();
        OffsetDateTime dayEnd = dayStart.plusDays(1);
        long occurrence = penalties.countDaily(memberId, cause, dayStart, dayEnd) + 1;
        penalties.saveAndFlush(MatchPenaltyEvent.forGroup(
                memberId, cause, score, "MATCH_ROOM_" + cause, groupId, attemptId, now));
        if (members.increasePenaltyScore(memberId, score) != 1) {
            throw new IllegalStateException("penalty 대상 회원을 갱신할 수 없습니다.");
        }

        OffsetDateTime requestedExpiry = now.plus(durationPolicy.apply(occurrence));
        MatchCooldown active = cooldowns.findActiveForUpdate(memberId).orElse(null);
        if (active != null) {
            OffsetDateTime preservedExpiry = active.getExpiresAt().isAfter(requestedExpiry)
                    ? active.getExpiresAt() : requestedExpiry;
            active.expire();
            cooldowns.flush();
            requestedExpiry = preservedExpiry;
        }
        if (!cooldowns.existsByRelatedGroupIdAndMemberIdAndReason(groupId, memberId, cause)) {
            cooldowns.save(MatchCooldown.activeForGroup(
                    memberId, cause, groupId, now, requestedExpiry));
        }
    }
}
