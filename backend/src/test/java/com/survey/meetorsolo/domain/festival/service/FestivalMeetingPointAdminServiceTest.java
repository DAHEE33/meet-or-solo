package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.survey.meetorsolo.domain.festival.dto.FestivalMeetingPointUpsertRequest;
import com.survey.meetorsolo.domain.festival.entity.*;
import com.survey.meetorsolo.domain.festival.repository.*;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FestivalMeetingPointAdminServiceTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final FestivalRepository festivals = mock(FestivalRepository.class);
    private final FestivalMeetingPointRepository points = mock(FestivalMeetingPointRepository.class);
    private final FestivalMeetingPointAdminService service =
            new FestivalMeetingPointAdminService(members, festivals, points);

    @Test
    void 일반_회원은_관리_API_service를_사용할_수_없다() {
        Member user = member("USER");
        when(members.findById(1L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.list(1, 10))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(festivals, points);
    }

    @Test
    void 관리자가_등록한_후보는_검증_전_INACTIVE다() {
        Member admin = member("ADMIN");
        when(members.findById(1L)).thenReturn(Optional.of(admin));
        Festival festival = mock(Festival.class);
        when(festivals.findByIdForUpdate(10L)).thenReturn(Optional.of(festival));
        when(points.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(1, 10, new FestivalMeetingPointUpsertRequest(
                " kakao-id ", " 장소명 ", " 주소 ", new BigDecimal("128.1"),
                new BigDecimal("37.1"), 10));

        assertThat(response.status()).isEqualTo(FestivalMeetingPointStatus.INACTIVE);
        assertThat(response.kakaoPlaceId()).isEqualTo("kakao-id");
        verify(festivals).findByIdForUpdate(10L);
    }

    private Member member(String role) {
        Member member = mock(Member.class);
        when(member.getRole()).thenReturn(role);
        return member;
    }
}
