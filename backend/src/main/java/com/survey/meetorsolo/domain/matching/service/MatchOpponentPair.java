package com.survey.meetorsolo.domain.matching.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record MatchOpponentPair(
        long lowerMemberId,
        long higherMemberId,
        long lowerCheckinId,
        long higherCheckinId
) implements Comparable<MatchOpponentPair> {

    public MatchOpponentPair {
        if (lowerMemberId <= 0 || higherMemberId <= 0 || lowerCheckinId <= 0 || higherCheckinId <= 0) {
            throw new IllegalArgumentException("memberId와 checkinId는 양수여야 합니다.");
        }
        if (lowerMemberId >= higherMemberId) {
            throw new IllegalArgumentException("member pair는 lowerMemberId < higherMemberId여야 합니다.");
        }
    }

    public static MatchOpponentPair of(long leftMemberId, long leftCheckinId,
                                       long rightMemberId, long rightCheckinId) {
        if (leftMemberId == rightMemberId) {
            throw new IllegalArgumentException("동일 회원 pair는 만들 수 없습니다.");
        }
        return leftMemberId < rightMemberId
                ? new MatchOpponentPair(leftMemberId, rightMemberId, leftCheckinId, rightCheckinId)
                : new MatchOpponentPair(rightMemberId, leftMemberId, rightCheckinId, leftCheckinId);
    }

    public AdvisoryLockKey advisoryLockKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((lowerCheckinId + ":" + higherCheckinId)
                    .getBytes(StandardCharsets.US_ASCII));
            ByteBuffer buffer = ByteBuffer.wrap(hash);
            return new AdvisoryLockKey(buffer.getInt(), buffer.getInt());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    @Override
    public int compareTo(MatchOpponentPair other) {
        int lowerMember = Long.compare(lowerMemberId, other.lowerMemberId);
        if (lowerMember != 0) return lowerMember;
        int higherMember = Long.compare(higherMemberId, other.higherMemberId);
        if (higherMember != 0) return higherMember;
        int lowerCheckin = Long.compare(lowerCheckinId, other.lowerCheckinId);
        return lowerCheckin != 0 ? lowerCheckin : Long.compare(higherCheckinId, other.higherCheckinId);
    }

    public record AdvisoryLockKey(int first, int second) {
    }
}
