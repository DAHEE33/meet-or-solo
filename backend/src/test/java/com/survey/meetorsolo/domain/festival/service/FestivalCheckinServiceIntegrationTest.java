package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survey.meetorsolo.domain.festival.dto.CheckInRequest;
import com.survey.meetorsolo.domain.festival.dto.CurrentCheckinResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalCheckinResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
import com.survey.meetorsolo.domain.festival.event.FestivalCheckinCancelledEvent;
import com.survey.meetorsolo.domain.festival.repository.FestivalCheckinRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import com.survey.meetorsolo.global.error.ErrorCode;
import com.survey.meetorsolo.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.festival.sync.enabled=false",
        "app.tour-place.sync.enabled=false"
})
@RecordApplicationEvents
@Transactional
class FestivalCheckinServiceIntegrationTest {

    @Autowired
    private FestivalCheckinService service;

    @Autowired
    private FestivalRepository festivalRepository;

    @Autowired
    private FestivalCheckinRepository festivalCheckinRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 최초_체크인은_FestivalCheckinCancelledEvent를_발행하지_않는다(ApplicationEvents events) {
        Member member = memberRepository.save(Member.createKakaoMember("checkin-test-" + UUID.randomUUID(), "테스트유저", null));
        Festival festival = festivalRepository.save(festivalAt(
                new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000")
        ));

        service.checkIn(
                member.getId(), festival.getId(),
                new CheckInRequest(new BigDecimal("37.0010000000"), new BigDecimal("128.0000000000"), 20)
        );

        assertThat(events.stream(FestivalCheckinCancelledEvent.class)).isEmpty();
    }

    @Test
    void 다른_축제로_재체크인하면_기존_축제_id로_FestivalCheckinCancelledEvent가_발행된다(ApplicationEvents events) {
        Member member = memberRepository.save(Member.createKakaoMember("checkin-test-" + UUID.randomUUID(), "테스트유저", null));
        Festival festivalA = festivalRepository.save(festivalAt(
                new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000")
        ));
        Festival festivalB = festivalRepository.save(festivalAt(
                new BigDecimal("129.0000000000"), new BigDecimal("38.0000000000")
        ));

        service.checkIn(
                member.getId(), festivalA.getId(),
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), null)
        );
        service.checkIn(
                member.getId(), festivalB.getId(),
                new CheckInRequest(new BigDecimal("38.0000000000"), new BigDecimal("129.0000000000"), null)
        );

        assertThat(events.stream(FestivalCheckinCancelledEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.memberId()).isEqualTo(member.getId());
                    assertThat(event.festivalId()).isEqualTo(festivalA.getId());
                });
    }

    @Test
    void 체크인하면_실제_DB에_ACTIVE_상태로_저장된다() {
        Member member = memberRepository.save(Member.createKakaoMember("checkin-test-" + UUID.randomUUID(), "테스트유저", null));
        Festival festival = festivalRepository.save(festivalAt(
                new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000")
        ));

        FestivalCheckinResponse result = service.checkIn(
                member.getId(), festival.getId(),
                new CheckInRequest(new BigDecimal("37.0010000000"), new BigDecimal("128.0000000000"), 20)
        );

        var saved = festivalCheckinRepository.findById(result.id()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(FestivalCheckinStatus.ACTIVE);
        assertThat(saved.getMemberId()).isEqualTo(member.getId());
        assertThat(saved.getFestivalId()).isEqualTo(festival.getId());
        assertThat(saved.getDistanceMeters()).isGreaterThan(0);
    }

    @Test
    void 다른_축제로_재체크인하면_기존_체크인은_CANCELLED로_바뀐다() {
        Member member = memberRepository.save(Member.createKakaoMember("checkin-test-" + UUID.randomUUID(), "테스트유저", null));
        Festival festivalA = festivalRepository.save(festivalAt(
                new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000")
        ));
        Festival festivalB = festivalRepository.save(festivalAt(
                new BigDecimal("129.0000000000"), new BigDecimal("38.0000000000")
        ));

        FestivalCheckinResponse first = service.checkIn(
                member.getId(), festivalA.getId(),
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), null)
        );
        service.checkIn(
                member.getId(), festivalB.getId(),
                new CheckInRequest(new BigDecimal("38.0000000000"), new BigDecimal("129.0000000000"), null)
        );

        var firstCheckin = festivalCheckinRepository.findById(first.id()).orElseThrow();
        assertThat(firstCheckin.getStatus()).isEqualTo(FestivalCheckinStatus.CANCELLED);
        List<com.survey.meetorsolo.domain.festival.entity.FestivalCheckin> activeCheckins =
                festivalCheckinRepository.findAllByMemberIdAndStatus(member.getId(), FestivalCheckinStatus.ACTIVE);
        assertThat(activeCheckins).singleElement()
                .satisfies(checkin -> assertThat(checkin.getFestivalId()).isEqualTo(festivalB.getId()));
    }

    @Test
    void 같은_축제에_재체크인해도_unique_index_위반_없이_기존_체크인이_취소되고_새로_생긴다() {
        Member member = memberRepository.save(Member.createKakaoMember("checkin-test-" + UUID.randomUUID(), "테스트유저", null));
        Festival festival = festivalRepository.save(festivalAt(
                new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000")
        ));

        FestivalCheckinResponse first = service.checkIn(
                member.getId(), festival.getId(),
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), null)
        );
        FestivalCheckinResponse second = service.checkIn(
                member.getId(), festival.getId(),
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), null)
        );

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(festivalCheckinRepository.findById(first.id()).orElseThrow().getStatus())
                .isEqualTo(FestivalCheckinStatus.CANCELLED);
        assertThat(festivalCheckinRepository.findById(second.id()).orElseThrow().getStatus())
                .isEqualTo(FestivalCheckinStatus.ACTIVE);
    }

    @Test
    void 활성_체크인이_없으면_getCurrentCheckin은_빈_값을_반환한다() {
        Member member = memberRepository.save(Member.createKakaoMember("checkin-test-" + UUID.randomUUID(), "테스트유저", null));

        Optional<CurrentCheckinResponse> result = service.getCurrentCheckin(member.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void 체크인하면_getCurrentCheckin이_축제명과_함께_반환한다() {
        Member member = memberRepository.save(Member.createKakaoMember("checkin-test-" + UUID.randomUUID(), "테스트유저", null));
        Festival festival = festivalRepository.save(festivalAt(
                new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000")
        ));

        FestivalCheckinResponse checkedIn = service.checkIn(
                member.getId(), festival.getId(),
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), null)
        );

        Optional<CurrentCheckinResponse> result = service.getCurrentCheckin(member.getId());

        assertThat(result).isPresent();
        assertThat(result.get().checkinId()).isEqualTo(checkedIn.id());
        assertThat(result.get().festivalId()).isEqualTo(festival.getId());
        assertThat(result.get().festivalName()).isEqualTo(festival.getTitle());
    }

    @Test
    void 활성_체크인이_없으면_cancelCurrentCheckin은_NOT_FOUND_예외를_던진다() {
        Member member = memberRepository.save(Member.createKakaoMember("checkin-test-" + UUID.randomUUID(), "테스트유저", null));

        assertThatThrownBy(() -> service.cancelCurrentCheckin(member.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void cancelCurrentCheckin은_체크인을_CANCELLED로_바꾸고_이벤트를_발행한다(ApplicationEvents events) {
        Member member = memberRepository.save(Member.createKakaoMember("checkin-test-" + UUID.randomUUID(), "테스트유저", null));
        Festival festival = festivalRepository.save(festivalAt(
                new BigDecimal("128.0000000000"), new BigDecimal("37.0000000000")
        ));
        FestivalCheckinResponse checkedIn = service.checkIn(
                member.getId(), festival.getId(),
                new CheckInRequest(new BigDecimal("37.0000000000"), new BigDecimal("128.0000000000"), null)
        );

        service.cancelCurrentCheckin(member.getId());

        var cancelled = festivalCheckinRepository.findById(checkedIn.id()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(FestivalCheckinStatus.CANCELLED);
        assertThat(events.stream(FestivalCheckinCancelledEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.memberId()).isEqualTo(member.getId());
                    assertThat(event.festivalId()).isEqualTo(festival.getId());
                });
    }

    private Festival festivalAt(BigDecimal mapX, BigDecimal mapY) {
        String contentId = "checkin-test-" + UUID.randomUUID();
        FestivalSyncData data = new FestivalSyncData(
                contentId,
                "15",
                "체크인 테스트 축제",
                "강원특별자치도 테스트시",
                "51",
                "110",
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 22),
                mapX,
                mapY,
                null,
                null,
                OffsetDateTime.parse("2026-07-18T10:00:00+09:00"),
                Map.of("contentid", contentId)
        );
        return Festival.create(data, LocalDate.of(2026, 7, 18));
    }
}
