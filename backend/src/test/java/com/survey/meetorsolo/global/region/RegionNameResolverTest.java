package com.survey.meetorsolo.global.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegionNameResolverTest {

    @Test
    void 주소_두번째_토큰을_시군구명으로_사용한다() {
        List<RegionOptionResponse> options = RegionNameResolver.toOptions(List.of(
                new RegionAggregate("150", "강원특별자치도 강릉시 창해로 514", 782L)
        ));

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.sigunguCode()).isEqualTo("150");
            assertThat(option.name()).isEqualTo("강릉시");
            assertThat(option.count()).isEqualTo(782L);
        });
    }

    @Test
    void 시도_표기가_달라도_시군구명은_같게_추출한다() {
        // 같은 강원인데 원본 데이터에 '강원특별자치도'와 '강원'이 섞여 있어 첫 토큰은 쓰지 않는다.
        List<RegionOptionResponse> options = RegionNameResolver.toOptions(List.of(
                new RegionAggregate("210", "강원 속초시 중앙로 183", 3L)
        ));

        assertThat(options).singleElement()
                .satisfies(option -> assertThat(option.name()).isEqualTo("속초시"));
    }

    @Test
    void 이름을_뽑을_수_없는_항목은_제외한다() {
        // 라벨이 없으면 사용자에게 보여줄 수 없으므로 목록에 넣지 않는다.
        List<RegionOptionResponse> options = RegionNameResolver.toOptions(List.of(
                new RegionAggregate("150", "강원특별자치도 강릉시 창해로 514", 1L),
                new RegionAggregate("110", null, 1L),
                new RegionAggregate("130", "", 1L),
                new RegionAggregate("140", "강원특별자치도", 1L),
                new RegionAggregate(null, "강원특별자치도 춘천시 중앙로 1", 1L)
        ));

        assertThat(options).extracting(RegionOptionResponse::sigunguCode)
                .containsExactly("150");
    }

    @Test
    void 시군구명_가나다순으로_정렬한다() {
        List<RegionOptionResponse> options = RegionNameResolver.toOptions(List.of(
                new RegionAggregate("760", "강원특별자치도 평창군 대관령면 1", 523L),
                new RegionAggregate("150", "강원특별자치도 강릉시 창해로 514", 782L),
                new RegionAggregate("110", "강원특별자치도 춘천시 중앙로 1", 341L)
        ));

        assertThat(options).extracting(RegionOptionResponse::name)
                .containsExactly("강릉시", "춘천시", "평창군");
    }

    @Test
    void 공백이_여러개인_주소도_처리한다() {
        List<RegionOptionResponse> options = RegionNameResolver.toOptions(List.of(
                new RegionAggregate("150", "  강원특별자치도   강릉시   창해로 514  ", 1L)
        ));

        assertThat(options).singleElement()
                .satisfies(option -> assertThat(option.name()).isEqualTo("강릉시"));
    }

    @Test
    void count가_null이면_0으로_내려준다() {
        List<RegionOptionResponse> options = RegionNameResolver.toOptions(List.of(
                new RegionAggregate("150", "강원특별자치도 강릉시 창해로 514", null)
        ));

        assertThat(options).singleElement()
                .satisfies(option -> assertThat(option.count()).isZero());
    }

    @Test
    void 빈_집계는_빈_목록을_반환한다() {
        assertThat(RegionNameResolver.toOptions(List.of())).isEmpty();
    }
}
