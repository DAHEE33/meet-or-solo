package com.survey.meetorsolo.domain.festival.service;

import com.survey.meetorsolo.domain.admin.service.AdminAuthorizationService;
import com.survey.meetorsolo.domain.festival.dto.AdminFestivalSummaryResponse;
import com.survey.meetorsolo.domain.festival.dto.FestivalSummary;
import com.survey.meetorsolo.domain.festival.entity.FestivalStatus;
import com.survey.meetorsolo.domain.festival.repository.FestivalRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 만남 장소 화면의 "축제 선택" 검색 전용 조회 서비스.
 *
 * <p>공개 {@link FestivalQueryService#getActiveFestivals}는 일반 사용자 화면을 위해 종료일이
 * 지난 축제를 항상 숨긴다({@code FestivalRepository#findVisibleFestivals}). 관리자는 방금 끝난
 * 축제의 만남 장소도 조회·수정해야 하므로, 그 정책과 분리된 별도 조회를 이 서비스가 담당한다
 * (docs/24_ADMIN_MEETING_POINT_MANAGEMENT_DESIGN.md 7장 후속 과제).
 */
@Service
public class FestivalAdminQueryService {

    /** 운영자가 숨긴(HIDDEN) 축제, 동기화상 비활성 시즌(INACTIVE)인 축제는 만남 장소 관리
     * 대상에서 제외한다 — 실질적으로 만남 장소를 다뤘을 가능성이 있는 범위로 한정한다. */
    private static final List<FestivalStatus> VISIBLE_STATUSES =
            List.of(FestivalStatus.ACTIVE, FestivalStatus.ENDED);

    /** 페이지네이션 UI 없이 한 번에 3분류(진행중/예정/마감)로 나눠 보여주는 화면이라, 상한만
     * 두고 전량을 가져온다. */
    private static final int MAX_RESULTS = 100;

    private final AdminAuthorizationService authorization;
    private final FestivalRepository festivals;

    public FestivalAdminQueryService(AdminAuthorizationService authorization, FestivalRepository festivals) {
        this.authorization = authorization;
        this.festivals = festivals;
    }

    @Transactional(readOnly = true)
    public List<AdminFestivalSummaryResponse> search(long adminId, String keyword) {
        authorization.requireAdmin(adminId);
        PageRequest pageRequest = PageRequest.of(
                0, MAX_RESULTS, Sort.by(Sort.Order.asc("eventStartDate"), Sort.Order.asc("id")));
        return festivals.findForAdmin(VISIBLE_STATUSES, normalize(keyword), pageRequest)
                .getContent().stream()
                .map(FestivalAdminQueryService::toResponse)
                .toList();
    }

    private static AdminFestivalSummaryResponse toResponse(FestivalSummary festival) {
        return new AdminFestivalSummaryResponse(
                festival.id(), festival.title(), festival.address(),
                festival.eventStartDate(), festival.eventEndDate(), festival.status(),
                festival.mapX(), festival.mapY()
        );
    }

    /**
     * {@code FestivalQueryService#normalize}와 같은 이유다 — PostgreSQL이
     * {@code lower(concat('%', :keyword, '%'))}에 바인딩되는 null 파라미터의 타입을 추론하지
     * 못해 오류가 나므로, null 대신 빈 문자열을 써서 항상 LIKE '%%'(전체 매칭)가 되게 한다.
     */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }
}
