package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.festival.dto.FestivalSummary;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

class FestivalAdminQueryServiceTest {
    private final AdminAuthorizationService authorization = mock(AdminAuthorizationService.class);
    private final FestivalRepository festivals = mock(FestivalRepository.class);
    private final FestivalAdminQueryService service = new FestivalAdminQueryService(authorization, festivals);

    @Test
    void 관리자가_아니면_검색할_수_없다() {
        when(authorization.requireAdmin(1L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.search(1L, "봄"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(festivals);
    }

    @Test
    void ACTIVE_ENDED만_검색_대상으로_넘긴다() {
        when(festivals.findForAdmin(any(), any(), any())).thenReturn(Page.empty());

        service.search(2L, null);

        verify(festivals).findForAdmin(
                eq(List.of(FestivalStatus.ACTIVE, FestivalStatus.ENDED)), eq(""), any());
    }

    @Test
    void 결과를_관리자_응답으로_변환한다() {
        FestivalSummary summary = new FestivalSummary(
                10L, "content-1", "봄맞이 축제", "강원 어딘가", "51", "51110",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5), FestivalStatus.ENDED,
                new BigDecimal("128.1"), new BigDecimal("37.1"));
        when(festivals.findForAdmin(any(), any(), any())).thenReturn(new PageImpl<>(List.of(summary)));

        var result = service.search(2L, "봄");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).title()).isEqualTo("봄맞이 축제");
        assertThat(result.get(0).status()).isEqualTo(FestivalStatus.ENDED);
        assertThat(result.get(0).mapX()).isEqualByComparingTo("128.1");
        assertThat(result.get(0).mapY()).isEqualByComparingTo("37.1");
    }
}
