package com.survey.meetorsolo.domain.safety.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survey.meetorsolo.domain.safety.block.repository.MemberBlockRepository;
import com.survey.meetorsolo.domain.safety.block.repository.MemberBlockRepository.MemberBlockSnapshot;
import com.survey.meetorsolo.domain.safety.block.service.MemberBlockService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberBlockServiceTest {
    @Mock MemberBlockRepository repository;

    @Test
    void repository_snapshot을_공개_DTO로만_변환한다() {
        OffsetDateTime blockedAt = OffsetDateTime.parse("2026-08-14T10:00:00+09:00");
        when(repository.findAllByBlockerMemberId(1L)).thenReturn(List.of(
                new MemberBlockSnapshot(2L, "상대", "https://image", blockedAt)));

        var result = new MemberBlockService(repository).getMyBlocks(1L);

        assertThat(result).singleElement().satisfies(block -> {
            assertThat(block.blockedMemberId()).isEqualTo(2L);
            assertThat(block.nickname()).isEqualTo("상대");
            assertThat(block.profileImageUrl()).isEqualTo("https://image");
            assertThat(block.blockedAt()).isEqualTo(blockedAt);
        });
    }

    @Test
    void 해제는_인증회원과_대상_ID를_repository에_그대로_전달한다() {
        new MemberBlockService(repository).unblock(1L, 2L);
        verify(repository).delete(1L, 2L);
    }
}
