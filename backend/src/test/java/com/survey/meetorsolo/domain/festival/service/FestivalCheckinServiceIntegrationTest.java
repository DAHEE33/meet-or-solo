package com.survey.meetorsolo.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.festival.dto.CheckInRequest;
import com.survey.meetorsolo.domain.festival.dto.FestivalCheckinResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalSyncData;
import com.survey.meetorsolo.domain.festival.entity.Festival;
import com.survey.meetorsolo.domain.festival.entity.FestivalCheckinStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalCheckinRepository;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.repository.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "app.profile.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.festival.sync.enabled=false",
        "app.tour-place.sync.enabled=false"
})
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
