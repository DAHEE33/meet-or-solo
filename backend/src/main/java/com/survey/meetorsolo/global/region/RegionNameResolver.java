package com.survey.meetorsolo.global.region;

import java.util.Comparator;
import java.util.List;

/**
 * 주소 문자열에서 시군구명을 뽑아 {@link RegionOptionResponse} 목록을 만든다.
 *
 * <p>주소는 "강원특별자치도 강릉시 ..." 형태라 두 번째 공백 토큰이 시군구명이다. 첫 번째
 * 토큰(시도)은 같은 지역인데도 "강원특별자치도"와 "강원"으로 섞여 있어 쓰지 않는다.
 * 이름을 얻을 수 없는 항목은 사용자에게 보여줄 라벨이 없으므로 목록에서 제외한다.
 */
public final class RegionNameResolver {

    private RegionNameResolver() {
    }

    public static List<RegionOptionResponse> toOptions(List<RegionAggregate> aggregates) {
        return aggregates.stream()
                .map(RegionNameResolver::toOption)
                .filter(option -> option != null)
                .sorted(Comparator.comparing(RegionOptionResponse::name))
                .toList();
    }

    private static RegionOptionResponse toOption(RegionAggregate aggregate) {
        if (aggregate == null || aggregate.sigunguCode() == null) {
            return null;
        }
        String name = sigunguName(aggregate.sampleAddress());
        if (name == null) {
            return null;
        }
        long count = aggregate.count() == null ? 0 : aggregate.count();
        return new RegionOptionResponse(aggregate.sigunguCode(), name, count);
    }

    private static String sigunguName(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length < 2 || tokens[1].isBlank()) {
            return null;
        }
        return tokens[1];
    }
}
