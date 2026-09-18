package com.survey.meetorsolo.domain.safety.block.dto;

import java.time.OffsetDateTime;

public record MemberBlockResponse(long blockedMemberId, String nickname, String profileImageUrl,
                                  OffsetDateTime blockedAt) {
}
