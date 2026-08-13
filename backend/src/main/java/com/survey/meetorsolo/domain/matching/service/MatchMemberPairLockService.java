package com.survey.meetorsolo.domain.matching.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MatchMemberPairLockService {

    private final JdbcTemplate jdbc;

    public MatchMemberPairLockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(long leftMemberId, long rightMemberId) {
        lockAll(List.of(MemberPair.of(leftMemberId, rightMemberId)));
    }

    public void lockAllMembers(Collection<Long> memberIds) {
        List<Long> ordered = memberIds.stream().distinct().sorted().toList();
        List<MemberPair> pairs = new ArrayList<>();
        for (int left = 0; left < ordered.size(); left++) {
            for (int right = left + 1; right < ordered.size(); right++) {
                pairs.add(MemberPair.of(ordered.get(left), ordered.get(right)));
            }
        }
        lockAll(pairs);
    }

    private void lockAll(Collection<MemberPair> pairs) {
        pairs.stream().distinct().sorted(Comparator.naturalOrder()).forEach(pair -> {
            AdvisoryLockKey key = pair.advisoryLockKey();
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, ?)", Object.class,
                    key.first(), key.second());
        });
    }

    record MemberPair(long lowerMemberId, long higherMemberId) implements Comparable<MemberPair> {
        static MemberPair of(long leftMemberId, long rightMemberId) {
            if (leftMemberId <= 0 || rightMemberId <= 0 || leftMemberId == rightMemberId) {
                throw new IllegalArgumentException("서로 다른 양수 memberId가 필요합니다.");
            }
            return leftMemberId < rightMemberId
                    ? new MemberPair(leftMemberId, rightMemberId)
                    : new MemberPair(rightMemberId, leftMemberId);
        }

        AdvisoryLockKey advisoryLockKey() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(("member-block:" + lowerMemberId + ":" + higherMemberId)
                        .getBytes(StandardCharsets.US_ASCII));
                ByteBuffer buffer = ByteBuffer.wrap(hash);
                return new AdvisoryLockKey(buffer.getInt(), buffer.getInt());
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
            }
        }

        @Override
        public int compareTo(MemberPair other) {
            int lower = Long.compare(lowerMemberId, other.lowerMemberId);
            return lower != 0 ? lower : Long.compare(higherMemberId, other.higherMemberId);
        }
    }

    record AdvisoryLockKey(int first, int second) {
    }
}
